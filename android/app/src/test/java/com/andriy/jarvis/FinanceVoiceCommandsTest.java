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
        test("витрата кава 85",FinanceVoiceCommands.Kind.EXPENSE,"85","Їжа → Кафе",null);
        test("чайові 650 готівкою",FinanceVoiceCommands.Kind.INCOME,"650","чай","готівка");
        yes(FinanceVoiceCommands.parse("таксі 250")==null);
        yes(FinanceVoiceCommands.parse("витрата кава 85 завтра")==null);
        yes(FinanceVoiceCommands.parse("витрата кава 85 і таксі 250")==null);
        FinanceVoiceCommands.Entry yesterday=FinanceVoiceCommands.parse("витрата кава 85 вчора");
        yes(yesterday!=null && yesterday.daysAgo==1 && yesterday.category.equals("Їжа → Кафе"));
        FinanceVoiceCommands.Entry earlier=FinanceVoiceCommands.parse("витратив 150 на каву позавчора");
        yes(earlier!=null && earlier.daysAgo==2 && earlier.amount.equals("150"));
        FinanceVoiceCommands.Entry today=FinanceVoiceCommands.parse("запиши витрату 300 гривень на транспорт сьогодні");
        yes(today!=null && today.daysAgo==0);
        yes(FinanceVoiceCommands.parse("витратив 120 на каву завтра")==null);
        test("витрата кава вісімдесят п'ять",FinanceVoiceCommands.Kind.EXPENSE,"85","Їжа → Кафе",null);
        test("витратив на каву двісті п'ятдесят",FinanceVoiceCommands.Kind.EXPENSE,"250","Їжа → Кафе",null);
        test("витрата кава півтори тисячі",FinanceVoiceCommands.Kind.EXPENSE,"1500","Їжа → Кафе",null);
        test("/витрата 85 кава",FinanceVoiceCommands.Kind.EXPENSE,"85","Їжа → Кафе",null);
        test("/дохід 650 чайові",FinanceVoiceCommands.Kind.INCOME,"650","чай",null);
        yes(FinanceVoiceCommands.parse("/видалити 85")==null);
        yes(FinanceVoiceCommands.parse("/витрата")==null);
        yes("який баланс".equals(FinanceSlashCommands.expand("/баланс")));
        test("витрата кава 85 з рахунку моно",FinanceVoiceCommands.Kind.EXPENSE,"85","Їжа → Кафе","моно");
        test("запиши витрату 1800 на заправку з монобанку",FinanceVoiceCommands.Kind.EXPENSE,"1800","Транспорт → Пальне","монобанк");
        test("чайові 650 з рахунку картка роботи",FinanceVoiceCommands.Kind.INCOME,"650","чай","картка роботи");
        // Approved local-first grammar. The category is exactly one word;
        // everything after it is the memo, never a second category.
        FinanceVoiceCommands.Entry shortExpense=
            FinanceVoiceCommands.parse("Дімон, витратив 150 готівка транспорт таксі до роботи");
        yes(shortExpense!=null &&
            shortExpense.kind==FinanceVoiceCommands.Kind.EXPENSE &&
            shortExpense.amount.equals("150") &&
            shortExpense.account.equals("готівка") &&
            shortExpense.category.equals("Транспорт") &&
            shortExpense.note.equals("таксі до роботи"));
        FinanceVoiceCommands.Entry shortIncome=
            FinanceVoiceCommands.parse("заробив 350 готівка чайові столик п'ять");
        yes(shortIncome!=null &&
            shortIncome.kind==FinanceVoiceCommands.Kind.INCOME &&
            shortIncome.category.equals("чай") &&
            shortIncome.note.startsWith("столик "));
        FinanceVoiceCommands.Entry russianSynonym=
            FinanceVoiceCommands.parse("Потратив 50 моно транспорт автобус");
        yes(russianSynonym!=null &&
            russianSynonym.account.equals("карта моно") &&
            russianSynonym.category.equals("Транспорт") &&
            russianSynonym.note.equals("автобус"));
        FinanceVoiceCommands.Entry card=
            FinanceVoiceCommands.parse("Витратив 12,50 карта моно харчування кава");
        yes(card!=null &&
            card.amount.equals("12.5") &&
            card.account.equals("карта моно") &&
            card.category.equals("Харчування") &&
            card.note.equals("кава"));
        FinanceVoiceCommands.Entry later=
            FinanceVoiceCommands.parse("Дімон заробив 15000 моно зарплата за листопад");
        yes(later!=null && later.kind==FinanceVoiceCommands.Kind.INCOME &&
            later.note.equals("за листопад"));
        yes(FinanceVoiceCommands.parse(
            "портатив 80 готівка транспорт")==null);
        yes(FinanceVoiceCommands.parse(
            "витратив 0 готівка транспорт")==null);
        yes(FinanceVoiceCommands.parse(
            "витратив 120 готівка невідома категорія")==null);
        yes(FinanceVoiceFollowups.parse("Дімон, не 85, а 95")!=null);
        System.out.println("PASS FinanceVoiceCommandsTest");
    }
}
