package com.andriy.jarvis;

import android.app.KeyguardManager;
import android.content.Context;
import android.database.Cursor;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

/** Deterministic user-owned Finance budget commands, never an external API. */
public final class FinanceBudgetVoiceController {
    private FinanceBudgetVoiceController() {}

    /** Null = not a budget command. Other finance queries are handled elsewhere. */
    public static String handle(Context context,String utterance) {
        FinanceInsightsCommands.Request request=
            FinanceInsightsCommands.parse(utterance);
        if(request==null)return null;
        switch(request.kind) {
            case SET_BUDGET:
            case SET_CATEGORY_BUDGET:
            case SHOW_BUDGET:
            case SHOW_CATEGORY_BUDGET:
            case BUDGET_REMAINING:break;
            default:return null;
        }
        if(context==null)return "Не можу відкрити локальний журнал фінансів.";
        try(FinanceLedger ledger=new FinanceLedger(context)) {
            if(request.changesBudget())
                return set(ledger,request.kind==FinanceInsightsCommands.Kind.SET_BUDGET
                    ?"":request.category,request.amount);
            KeyguardManager lock=(KeyguardManager)
                context.getSystemService(Context.KEYGUARD_SERVICE);
            if(lock!=null && lock.isDeviceLocked() &&
               !"true".equals(ledger.setting("finance_voice_locked","false")))
                return "Розблокуй Pixel для перегляду фінансових сум.";
            String category=request.kind==FinanceInsightsCommands.Kind.SHOW_CATEGORY_BUDGET
                ?request.category:"";
            return show(ledger,category);
        }catch(RuntimeException | UnsatisfiedLinkError error) {
            android.util.Log.e("DIMON_FINANCE","Budget command failed",error);
            return "Не вдалося виконати команду бюджету в JARVIS Finance. "+
                "Попередні дані не видалені.";
        }
    }

    private static String set(FinanceLedger ledger,String category,String amount) {
        if(amount==null || FinanceLedger.minor(amount)<=0)
            return "Вкажи додатну суму бюджету.";
        String label=category.isEmpty()?"Загальний бюджет":"Бюджет: "+category;
        SQLiteDatabase db=ledger.getWritableDatabase();
        db.beginTransaction();
        try {
            String id=null;
            try(Cursor c=db.rawQuery(
                "SELECT id FROM plans WHERE type='budget' AND currency='UAH' "+
                "AND budget_period='month' AND category=? ORDER BY rowid DESC LIMIT 1",
                new String[]{category})) {
                if(c.moveToFirst())id=c.getString(0);
            }
            if(id!=null) {
                db.execSQL("UPDATE plans SET amount=? WHERE id=?",
                    new Object[]{FinanceLedger.minor(amount),id});
            } else {
                ledger.plan("budget",label,"UAH",amount,"",category,"month");
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        return label+" встановлено: "+amount+" гривень. "+
            "Збережено в JARVIS Finance на Pixel.";
    }

    private static String show(FinanceLedger ledger,String category) {
        try(Cursor c=ledger.getReadableDatabase().rawQuery(
            "SELECT name,currency,amount,budget_period FROM plans "+
            "WHERE type='budget' AND category=? AND currency='UAH' "+
            "ORDER BY rowid DESC LIMIT 1",new String[]{category})) {
            if(!c.moveToFirst())return category.isEmpty()
                ?"Загальний місячний бюджет ще не встановлено."
                :"Бюджет на категорію «"+category+"» ще не встановлено.";
            long limit=c.getLong(2);
            String period=c.getString(3);
            long used=FinanceAnalytics.spent(ledger,c.getString(1),category,period);
            long remain=Math.subtractExact(limit,used);
            return c.getString(0)+": "+FinanceLedger.money(limit)+" гривень. "+
                "Витрачено "+FinanceLedger.money(used)+
                ", залишилось "+FinanceLedger.money(remain)+".";
        }
    }
}
