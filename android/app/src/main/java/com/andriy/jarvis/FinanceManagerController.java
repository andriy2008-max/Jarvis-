package com.andriy.jarvis;
import android.content.Context;
/** Local finance dispatcher; no external app or Accessibility writes. */
public final class FinanceManagerController {
    private FinanceManagerController(){}
    public interface Listener {void onProgress(String message);void onFinished(boolean success,String message);}
    public static boolean isRunning(){return false;}
    public static void cancel(){}
    public static void start(Context context,String utterance,Listener listener) {
        start(context,utterance,java.util.UUID.randomUUID().toString(),listener);
    }
    public static synchronized void start(Context context,String utterance,String commandId,Listener listener) {
        start(context,utterance,commandId,System.currentTimeMillis(),listener);
    }
    public static synchronized void start(Context context,String utterance,String commandId,long capturedAt,Listener listener) {
        if(listener==null)return;
        if(capturedAt<=0||capturedAt>System.currentTimeMillis()+300000L){listener.onFinished(false,"Перевір дату й час годинника.");return;}
        if(FinanceBatchCommands.looksLikeBatch(utterance)){
            FinanceBatchController.start(context,utterance,commandId,capturedAt,listener);
            return;
        }
        FinanceVoiceFollowups.Correction correction=FinanceVoiceFollowups.parse(utterance);
        if(correction!=null){
            FinanceCorrectionController.start(context,utterance,commandId,capturedAt,correction,listener);
            return;
        }
        FinanceVoiceCommands.Entry request=FinanceVoiceCommands.parse(utterance);
        if(request==null){listener.onFinished(false,"Не розпізнав фінансовий запис.");return;}
        try(FinanceLedger ledger=new FinanceLedger(context)){
            net.zetetic.database.sqlcipher.SQLiteDatabase db=ledger.getWritableDatabase();
            db.beginTransaction();try{
            String previous=ledger.receipt(commandId,utterance);if(previous!=null){listener.onFinished(true,previous);return;}
            long account=-1;
            if(request.account==null)account=Long.parseLong(ledger.setting("default_account","-1"));
            else account=ledger.resolveAccount(request.account);
            if(account<0){listener.onFinished(false,"Відкрий Фінанси у Джарвісі та додай рахунок або вкажи його назву в команді.");return;}
            if(!ledger.currency(account).equals("UAH")){listener.onFinished(false,"Голосовий запис гривень потребує рахунку UAH.");return;}
            if(request.category.equals("борг")||request.category.equals("крипта")){
                listener.onFinished(false,"Для боргів та активів потрібен окремий тип обліку. Запис не створено.");return;
            }
            long effectiveTime=java.time.Instant.ofEpochMilli(capturedAt).atZone(java.time.ZoneId.systemDefault())
                .minusDays(request.daysAgo).toInstant().toEpochMilli();
            ledger.record(commandId,account,FinanceLedger.minor(request.amount),
                request.kind==FinanceVoiceCommands.Kind.EXPENSE?"expense":"income",request.category,utterance,effectiveTime);
            String reply="✅ "+request.amount+" ₴ · "+request.category+" · "+java.time.Instant.ofEpochMilli(effectiveTime).atZone(java.time.ZoneId.systemDefault()).toLocalDate()+". Записано у Джарвісі.";
            ledger.receipt(commandId,utterance,reply);
            int separator=commandId.lastIndexOf(':');
            String source=separator<1?"phone":commandId.substring(0,separator);
            ledger.setSetting("finance_last_id:"+source,commandId);
            ledger.setSetting("finance_last_at:"+source,Long.toString(capturedAt));
            db.setTransactionSuccessful();
            }finally{db.endTransaction();}
            listener.onFinished(true,ledger.receipt(commandId,utterance));
        }catch(IllegalArgumentException e){listener.onFinished(false,e.getMessage());}
        catch(RuntimeException e){listener.onFinished(false,"Не вдалося зберегти фінансовий запис.");}
    }
}
