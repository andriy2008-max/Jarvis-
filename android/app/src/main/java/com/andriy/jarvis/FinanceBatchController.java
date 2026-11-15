package com.andriy.jarvis;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.time.*;
import java.util.List;

/** Every item and the command receipt commit together, or none do. */
public final class FinanceBatchController {
    private FinanceBatchController(){}
    public static void start(Context context,String utterance,String id,long capturedAt,
                             FinanceManagerController.Listener listener){
        List<FinanceVoiceCommands.Entry> items=FinanceBatchCommands.parse(utterance);
        if(items==null){
            listener.onFinished(false,"Не вдалося розібрати всі витрати. Скажи «запиши витрати кава 85 і таксі 250». Нічого не збережено.");
            return;
        }
        try(FinanceLedger ledger=new FinanceLedger(context)){
            SQLiteDatabase db=ledger.getWritableDatabase();
            String reply;
            db.beginTransaction();
            try{
                String saved=ledger.receipt(id,utterance);
                if(saved!=null){listener.onFinished(true,saved);return;}
                long total=0;int count=0;
                for(FinanceVoiceCommands.Entry item:items){
                    long account=-1;
                    if(item.account==null)account=Long.parseLong(ledger.setting("default_account","-1"));
                    else{
                        try(Cursor c=db.rawQuery("SELECT id FROM accounts WHERE LOWER(name)=LOWER(?)",new String[]{item.account})){
                            if(c.moveToFirst()){account=c.getLong(0);if(c.moveToNext())account=-1;}
                        }
                    }
                    if(account<0||!ledger.currency(account).equals("UAH"))
                        throw new IllegalArgumentException("Вибери рахунок UAH для кожної витрати");
                    if(item.category.equals("крипта")||item.category.equals("борг"))
                        throw new IllegalArgumentException("Крипта і борги мають окремий тип обліку");
                    long amount=FinanceLedger.minor(item.amount);
                    total=Math.addExact(total,amount);
                    long happened=Instant.ofEpochMilli(capturedAt).atZone(ZoneId.systemDefault())
                        .minusDays(item.daysAgo).toInstant().toEpochMilli();
                    db.execSQL("INSERT INTO entries(id,account,amount,category,note,happened,kind) VALUES(?,?,?,?,?,?,?)",
                        new Object[]{id+":"+count,account,-amount,item.category,utterance,happened,"expense"});
                    count++;
                }
                reply="✅ Записано "+count+" витрати на "+FinanceLedger.money(total)+
                    " ₴. Кожну операцію збережено окремо.";
                ledger.receipt(id,utterance,reply);
                db.setTransactionSuccessful();
            }finally{db.endTransaction();}
            listener.onFinished(true,reply);
        }catch(IllegalArgumentException e){listener.onFinished(false,e.getMessage()+". Нічого не збережено.");}
        catch(RuntimeException e){listener.onFinished(false,"Не вдалося зберегти пакет витрат. Жодну операцію не проведено.");}
    }
}
