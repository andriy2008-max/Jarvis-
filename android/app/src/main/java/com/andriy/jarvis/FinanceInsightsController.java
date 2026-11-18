package com.andriy.jarvis;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import java.math.BigDecimal;
import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.Locale;

/**
 * Pixel-owned JARVIS Finance budget read/write and finance question responder.
 *
 * Monthly budgets are real data owned by Jarvis and persisted locally.
 * Balance and transaction answers are deliberately unavailable until a
 * transaction-history read API OR a reconciled first-party ledger exists.
 * We must never interpret a closed third-party editor as a confirmed expense.
 */
public final class FinanceInsightsController {
    private static final String STORE = "jarvis_finance_monthly_budgets_v1";
    private static final Locale UK = Locale.forLanguageTag("uk-UA");

    private FinanceInsightsController() {}

    /** Returns null for anything outside explicit Finance Insights grammar. */
    public static String handle(Context context, String utterance) {
        FinanceInsightsCommands.Request request=FinanceInsightsCommands.parse(utterance);
        if(request==null)return null;
        if(context==null)return "Не можу прочитати фінансові налаштування Pixel.";
        if(context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH))
            return "Фінансові дані зберігаються на Pixel. Перевір з'єднання з телефоном.";
        return execute(context.getApplicationContext(),request);
    }

    private static String execute(Context context,FinanceInsightsCommands.Request request) {
        switch(request.kind) {
            case SET_BUDGET:
                return setBudget(context,null,request.amount);
            case SET_CATEGORY_BUDGET:
                return setBudget(context,request.category,request.amount);
            case SHOW_BUDGET:
                return showBudget(context,null);
            case SHOW_CATEGORY_BUDGET:
                return showBudget(context,request.category);
            case BUDGET_REMAINING:
                String budget=budget(context,null);
                return budget==null
                    ? "На цей місяць бюджет Дімона ще не встановлено. " +
                      "Скажи: «Встанови бюджет 15000 гривень на місяць»."
                    : "Бюджет на " + monthLabel() + ": "+money(budget)+" гривень. "+
                      "Точний залишок поки недоступний: історія витрат "+
                      "«Менеджера фінансів» ще не підключена для читання.";
            case SPENT_TODAY:
                return missingHistory("за сьогодні");
            case SPENT_MONTH:
                return missingHistory("за цей місяць");
            case SPENT_CATEGORY_TODAY:
                return missingHistory("на «"+request.category+"» сьогодні");
            case SPENT_CATEGORY_MONTH:
                return missingHistory("на «"+request.category+"» за цей місяць");
            case BALANCES:
                return missingBalances(null);
            case ACCOUNT_BALANCE:
                return missingBalances(request.account);
            default:
                return "Невідома фінансова команда.";
        }
    }

    private static String missingHistory(String period) {
        return "Суму витрат "+period+" поки не можу підтвердити. "+
            "Дімон ще не має доступу до повної історії «Менеджера фінансів». "+
            "Не називатиму неперевірені цифри.";
    }

    private static String missingBalances(String account) {
        if(account==null)
            return "Не можу підтвердити залишки на рахунках: "+
                "читання балансів у «Менеджері фінансів» ще не підключено.";
        return "Не можу підтвердити залишок рахунку «"+account+"»: "+
            "читання балансів у «Менеджері фінансів» ще не підключено.";
    }

    private static String setBudget(Context context,String category,String amount) {
        if(amount==null)return "Сума бюджету не розпізнана.";
        String key=budgetKey(category);
        boolean stored=prefs(context).edit().putString(key,amount).commit();
        if(!stored)return "Не вдалося зберегти бюджет у пам'яті Pixel. Повтори пізніше.";
        return (category==null?"Місячний бюджет": "Бюджет категорії «"+category+"»")+
            " на "+monthLabel()+" встановлено: "+money(amount)+" гривень. "+
            "Зберіг локально на Pixel.";
    }

    private static String showBudget(Context context,String category) {
        String amount=budget(context,category);
        String scope=category==null?"Загальний бюджет":"Бюджет «"+category+"»";
        if(amount==null)
            return scope+" на "+monthLabel()+" ще не встановлено.";
        return scope+" на "+monthLabel()+": "+money(amount)+" гривень.";
    }

    private static String budget(Context context,String category) {
        return prefs(context).getString(budgetKey(category),null);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(STORE,Context.MODE_PRIVATE);
    }

    private static String budgetKey(String category) {
        Calendar now=Calendar.getInstance();
        return String.format(Locale.ROOT,"%04d-%02d/%s",
            now.get(Calendar.YEAR),now.get(Calendar.MONTH)+1,
            category==null?"total":"category/"+category);
    }

    private static String monthLabel() {
        Calendar now=Calendar.getInstance();
        String month=new DateFormatSymbols(UK).getMonths()[now.get(Calendar.MONTH)];
        return month+" "+now.get(Calendar.YEAR);
    }

    private static String money(String decimal) {
        return new BigDecimal(decimal).stripTrailingZeros().toPlainString();
    }
}
