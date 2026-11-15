package com.andriy.jarvis;
import android.content.Context;
/** Local finance dispatcher; no external app or Accessibility writes. */
public final class FinanceManagerController {
    private FinanceManagerController(){}
    public interface Listener {void onProgress(String message);void onFinished(boolean success,String message);}
    public static boolean isRunning(){return false;}
    public static void cancel(){}
    public static void start(Context context,String utterance,Listener listener) {
        if(listener==null)return;
        FinanceVoiceCommands.Entry request=FinanceVoiceCommands.parse(utterance);
        if(request==null){listener.onFinished(false,"Не розпізнав фінансовий запис.");return;}
        try(FinanceLedger ledger=new FinanceLedger(context)){
            long account=-1;
            try(android.database.Cursor c=ledger.getReadableDatabase().rawQuery(
                request.account==null?"SELECT id FROM accounts":"SELECT id FROM accounts WHERE lower(name)=lower(?)",
                request.account==null?null:new String[]{request.account})){
                if(c.moveToFirst()){account=c.getLong(0);if(request.account==null&&c.moveToNext())account=-1;}
            }
            if(account<0){listener.onFinished(false,"Відкрий Фінанси у Джарвісі та додай рахунок або вкажи його назву в команді.");return;}
            if(!ledger.currency(account).equals("UAH")){listener.onFinished(false,"Голосовий запис гривень потребує рахунку UAH.");return;}
            if(request.category.equals("борг")||request.category.equals("крипта")){
                listener.onFinished(false,"Для боргів та активів потрібен окремий тип обліку. Запис не створено.");return;
            }
            ledger.record(java.util.UUID.randomUUID().toString(),account,FinanceLedger.minor(request.amount),
                request.kind==FinanceVoiceCommands.Kind.EXPENSE?"expense":"income",request.category,utterance,System.currentTimeMillis());
            listener.onFinished(true,"✅ "+request.amount+" ₴ · "+request.category+" · сьогодні. Записано у Джарвісі.");
        }catch(RuntimeException e){listener.onFinished(false,"Не вдалося зберегти фінансовий запис.");}
    }
}
