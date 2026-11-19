package com.andriy.jarvis;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.UUID;

/** Watch-only durable FIFO queue. Idempotent receipts remain owned by Pixel. */
public final class FinanceWatchQueue {
    private static final String PREFS="finance_watch_queue",TAIL="tail";
    private static final int MAX=20;
    private FinanceWatchQueue(){}
    private static SharedPreferences prefs(Context context){
        return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    private static JSONArray tail(Context c,SharedPreferences p){
        try{return new JSONArray(FinanceWatchVault.reveal(p.getString(TAIL,"[]")));}catch(Exception e){return new JSONArray();}
    }
    public static synchronized int count(Context c){
        SharedPreferences p=prefs(c);
        return (p.getString("id","").isEmpty()?0:1)+tail(c,p).length();
    }
    public static synchronized boolean add(Context c,String command){
        SharedPreferences p=prefs(c);
        if(command==null||command.trim().isEmpty())return false;
        String id=p.getString("id","");
        if(id.isEmpty()){
            boolean stored=p.edit().putString("id",UUID.randomUUID().toString()).putString("text",FinanceWatchVault.protect(command))
                .putLong("captured_at",System.currentTimeMillis()).putBoolean("sent",false).commit();
            if(stored)FinanceWatchRetryJob.schedule(c,15000L);
            return stored;
        }
        // Repeating the pending command is a retransmission, never a new charge.
        if(command.equals(FinanceWatchVault.reveal(p.getString("text",""))))return true;
        JSONArray pending=tail(c,p);
        if(pending.length()>=MAX-1)return false;
        try{
            // Distinct utterances get distinct immutable IDs and capture instants.
            JSONObject next=new JSONObject().put("id",UUID.randomUUID().toString())
                .put("text",FinanceWatchVault.protect(command)).put("captured_at",System.currentTimeMillis());
            pending.put(next);
            boolean stored=p.edit().putString(TAIL,FinanceWatchVault.protect(pending.toString())).commit();
            if(stored)FinanceWatchRetryJob.schedule(c,15000L);
            return stored;
        }catch(Exception e){return false;}
    }
    public static synchronized String first(Context c){return FinanceWatchVault.reveal(prefs(c).getString("text",""));}
    public static synchronized String firstId(Context c){return prefs(c).getString("id","");}
    public static synchronized long firstAt(Context c){return prefs(c).getLong("captured_at",System.currentTimeMillis());}
    public static synchronized void markSent(Context c,String id){
        SharedPreferences p=prefs(c);
        if(id!=null&&id.equals(p.getString("id","")))p.edit().putBoolean("sent",true).commit();
    }
    public static synchronized boolean acknowledge(Context c,String id){
        SharedPreferences p=prefs(c);
        if(!id.equals(p.getString("id","")))return false;
        JSONArray pending=tail(c,p);
        SharedPreferences.Editor e=p.edit().clear();
        if(pending.length()>0){
            try{
                JSONObject head=pending.getJSONObject(0);
                JSONArray rest=new JSONArray();
                for(int i=1;i<pending.length();i++)rest.put(pending.getJSONObject(i));
                e.putString("id",head.getString("id")).putString("text",head.getString("text"))
                    .putLong("captured_at",head.getLong("captured_at"))
                    .putBoolean("sent",false).putString(TAIL,FinanceWatchVault.protect(rest.toString()));
            }catch(Exception exc){return false;}
        }
        boolean stored=e.commit();
        if(stored){
            if(!firstId(c).isEmpty())FinanceWatchRetryJob.schedule(c,15000L);
            else FinanceWatchRetryJob.cancel(c);
        }
        return stored;
    }
    /** A phone-side validation failure is final; do not block every later queued expense. */
    public static synchronized boolean rejected(Context c,String id){
        // Keep no private utterance outside the queue; the phone has not posted a record.
        return acknowledge(c,id);
    }
    public static synchronized String cancelLatestUnsent(Context c){
        SharedPreferences p=prefs(c);
        JSONArray pending=tail(c,p);
        if(pending.length()>0){
            JSONArray rest=new JSONArray();
            for(int i=0;i<pending.length()-1;i++){
                try{rest.put(pending.getJSONObject(i));}catch(Exception e){return "Черга пошкоджена; не змінено.";}
            }
            return p.edit().putString(TAIL,FinanceWatchVault.protect(rest.toString())).commit()?
                "Останню команду, яка ще не надсилалась, прибрано з черги.":
                "Не вдалося оновити чергу.";
        }
        if(!p.getString("id","").isEmpty()&&p.contains("sent")&&!p.getBoolean("sent",true)){
            return p.edit().clear().commit()?"Не надіслану команду скасовано.":"Не вдалося скасувати запис.";
        }
        return "Команду вже могли передати телефону. Перевір журнал і скасуй операцію там, щоб уникнути розбіжностей.";
    }
}
