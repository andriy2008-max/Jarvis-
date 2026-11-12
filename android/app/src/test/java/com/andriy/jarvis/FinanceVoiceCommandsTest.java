package com.andriy.jarvis;

public final class FinanceVoiceCommandsTest {
    private static void yes(boolean v) {if(!v)throw new AssertionError("Finance commands");}
    private static void test(String phrase,FinanceVoiceCommands.Kind kind,
                             String amount,String category,String account){
        FinanceVoiceCommands.Entry e=FinanceVoiceCommands.parse(phrase);
        yes(e!=null && e.kind==kind && e.amount.equals(amount) &&
            e.category.equals(category) &&
            (account==null?e.account==null:account.equals(e.account)));
    }
    public static void main(String[] args){
        test("Джарвіс, запиши витрату 350 гривень на харчування",
            FinanceVoiceCommands.Kind.EXPENSE,"350","Харчування",null);
        test("запиши витрату 180 грн на транспорт з готівки",
            FinanceVoiceCommands.Kind.EXPENSE,"180","Транспорт","готівка");
        test("додай витрату 12,50 на продукти з карти моно",
            FinanceVoiceCommands.Kind.EXPENSE,"12.5","Харчування","карта моно");
        test("запиши дохід 1000 гривень зарплата",
            FinanceVoiceCommands.Kind.INCOME,"1000","Зарплата",null);
        test("додай дохід 500 чайові на карту моно",
            FinanceVoiceCommands.Kind.INCOME,"500","чай","карта моно");
        yes(FinanceVoiceCommands.parse("переказ 1000 на карту")==null);
        yes(FinanceVoiceCommands.parse("переказ з карти 200")==null);
        yes(FinanceVoiceCommands.parse("запиши витрату 0 на транспорт")==null);
        yes(FinanceVoiceCommands.parse("запиши витрату 1000000 на транспорт")==null);
        yes(FinanceVoiceCommands.parse("запиши витрату 500 на невідома категорія")==null);
        yes(FinanceVoiceCommands.parse("запиши витрату 200 на зарплата")==null);
        yes(FinanceVoiceCommands.cancel("Джарвіс, скасуй витрату"));
        yes(!FinanceVoiceCommands.cancel("запиши витрату"));
        System.out.println("PASS FinanceVoiceCommandsTest");
    }
}
