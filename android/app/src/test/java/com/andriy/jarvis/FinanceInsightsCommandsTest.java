package com.andriy.jarvis;

/** Plain-Java parser regression tests, no network, Android emulator or APK. */
public final class FinanceInsightsCommandsTest {
    private static void yes(boolean value,String reason) {
        if(!value)throw new AssertionError(reason);
    }
    private static void check(String phrase,FinanceInsightsCommands.Kind kind,
                              String category,String account,String amount) {
        FinanceInsightsCommands.Request r=FinanceInsightsCommands.parse(phrase);
        yes(r!=null,"Not recognized: "+phrase);
        yes(r.kind==kind,"Wrong kind: "+phrase+" expected="+kind+" actual="+r.kind);
        yes(category==null?r.category==null:category.equals(r.category),
            "Wrong category: "+phrase);
        yes(account==null?r.account==null:account.equals(r.account),
            "Wrong account: "+phrase);
        yes(amount==null?r.amount==null:amount.equals(r.amount),
            "Wrong amount: "+phrase);
    }
    public static void main(String[] args) {
        check("Джарвіс, скільки я витратив сьогодні",
            FinanceInsightsCommands.Kind.SPENT_TODAY,null,null,null);
        check("витрати за сьогодні",
            FinanceInsightsCommands.Kind.SPENT_TODAY,null,null,null);
        check("скільки витратив цього місяця",
            FinanceInsightsCommands.Kind.SPENT_MONTH,null,null,null);
        check("витрати за цей місяць",
            FinanceInsightsCommands.Kind.SPENT_MONTH,null,null,null);
        check("скільки я витратив на транспорт сьогодні",
            FinanceInsightsCommands.Kind.SPENT_CATEGORY_TODAY,"Транспорт",null,null);
        check("скільки витратив на харчування цього місяця",
            FinanceInsightsCommands.Kind.SPENT_CATEGORY_MONTH,"Харчування",null,null);
        check("витрати на телефон за місяць",
            FinanceInsightsCommands.Kind.SPENT_CATEGORY_MONTH,"Телефон",null,null);
        check("який мій баланс",
            FinanceInsightsCommands.Kind.BALANCES,null,null,null);
        check("покажи залишки рахунків",
            FinanceInsightsCommands.Kind.BALANCES,null,null,null);
        check("скільки грошей на карті моно",
            FinanceInsightsCommands.Kind.ACCOUNT_BALANCE,null,"карта моно",null);
        check("який залишок в готівці",
            FinanceInsightsCommands.Kind.ACCOUNT_BALANCE,null,"готівка",null);
        check("встанови бюджет 15000 гривень на місяць",
            FinanceInsightsCommands.Kind.SET_BUDGET,null,null,"15000");
        check("встанови бюджет на цей місяць 15000",
            FinanceInsightsCommands.Kind.SET_BUDGET,null,null,"15000");
        check("зміни місячний бюджет 12500,50 грн",
            FinanceInsightsCommands.Kind.SET_BUDGET,null,null,"12500.5");
        check("встанови бюджет на транспорт 2000 грн",
            FinanceInsightsCommands.Kind.SET_CATEGORY_BUDGET,"Транспорт",null,"2000");
        check("постав бюджет на харчування 4000",
            FinanceInsightsCommands.Kind.SET_CATEGORY_BUDGET,"Харчування",null,"4000");
        check("який бюджет на місяць",
            FinanceInsightsCommands.Kind.SHOW_BUDGET,null,null,null);
        check("покажи мій бюджет",
            FinanceInsightsCommands.Kind.SHOW_BUDGET,null,null,null);
        check("покажи бюджет на транспорт",
            FinanceInsightsCommands.Kind.SHOW_CATEGORY_BUDGET,"Транспорт",null,null);
        check("скільки залишилось бюджету",
            FinanceInsightsCommands.Kind.BUDGET_REMAINING,null,null,null);
        check("який залишок бюджету",
            FinanceInsightsCommands.Kind.BUDGET_REMAINING,null,null,null);
        yes(FinanceInsightsCommands.parse("переказ 500 на карту")==null,
            "Transfers must not be interpreted as budgets");
        yes(FinanceInsightsCommands.parse("встанови бюджет 0 грн")==null,
            "Reject zero budget");
        yes(FinanceInsightsCommands.parse("встанови бюджет 10000000 грн")==null,
            "Reject out-of-range budget");
        yes(FinanceInsightsCommands.parse("встанови бюджет на щось 200 грн")==null,
            "Reject unknown category");
        yes(FinanceInsightsCommands.parse("запиши витрату 150 на транспорт")==null,
            "Expense inputs must remain in existing write flow");
        yes(FinanceInsightsCommands.parse("скажи який баланс")==null,
            "Do not hijack speaking commands");
        yes(FinanceInsightsCommands.parse("скільки часу")==null,
            "Other assistant questions must still reach their own router");
        System.out.println("PASS FinanceInsightsCommandsTest");
    }
}
