package com.andriy.jarvis;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Confirmed edits only: correct the last entry from the same phone/watch source. */
public final class FinanceCorrectionController {
    private FinanceCorrectionController(){}
    private static String source(String commandId){
        int colon=commandId.lastIndexOf(':');
        return colon<1?"phone":commandId.substring(0,colon);
    }
    public static void start(Context context,String utterance,String commandId,long capturedAt,
                             FinanceVoiceFollowups.Correction correction,FinanceManagerController.Listener listener){
        try(FinanceLedger ledger=new FinanceLedger(context)){
            SQLiteDatabase db=ledger.getWritableDatabase();
            String response;
            db.beginTransaction();
            try{
                String previous=ledger.receipt(commandId,utterance);
                if(previous!=null){listener.onFinished(true,previous);return;}
                String src=source(commandId);
                String lastId=ledger.setting("finance_last_id:"+src,"");
                long lastAt=Long.parseLong(ledger.setting("finance_last_at:"+src,"0"));
                if(lastId.isEmpty()||capturedAt<lastAt||capturedAt-lastAt>180000L)
                    throw new IllegalArgumentException("Час виправлення минув. Зміни запис у вкладці «Операції».");
                try(Cursor c=db.rawQuery("SELECT amount,kind,deleted,category FROM entries WHERE id=?",new String[]{lastId})){
                    if(!c.moveToFirst()||c.getInt(2)!=0||
                        !(c.getString(1).equals("expense")||c.getString(1).equals("income")))
                        throw new IllegalArgumentException("Остання операція недоступна для виправлення.");
                    long previousValue=c.getLong(0);
                    long oldAmount=Math.abs(previousValue);
                    long expected=FinanceLedger.minor(correction.oldAmount);
                    long target=FinanceLedger.minor(correction.newAmount);
                    if(oldAmount!=expected)
                        throw new IllegalArgumentException("Попередня сума не збігається з останнім записом.");
                    if(c.getString(1).equals("expense")){
                        try(Cursor refunds=db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM entries WHERE kind='refund' AND deleted=0 AND note=?",new String[]{"Повернення:"+lastId})){
                            refunds.moveToFirst();
                            if(target<refunds.getLong(0))
                                throw new IllegalArgumentException("Нова сума менша за повернені кошти.");
                        }
                    }
                    db.execSQL("INSERT INTO audit(entry_id,event,happened,previous_amount) VALUES(?,?,?,?)",
                        new Object[]{lastId,"voice-correction",System.currentTimeMillis(),previousValue});
                    db.execSQL("UPDATE entries SET amount=? WHERE id=?",
                        new Object[]{previousValue<0?-target:target,lastId});
                    response="✅ Виправлено "+correction.oldAmount+" на "+correction.newAmount+" ₴ · "+c.getString(3)+".";
                }
                ledger.receipt(commandId,utterance,response);
                ledger.setSetting("finance_last_at:"+src,Long.toString(capturedAt));
                db.setTransactionSuccessful();
            }finally{db.endTransaction();}
            listener.onFinished(true,response);
        }catch(IllegalArgumentException e){listener.onFinished(false,e.getMessage());}
        catch(RuntimeException e){listener.onFinished(false,"Не вдалося виправити фінансовий запис.");}
    }
}
