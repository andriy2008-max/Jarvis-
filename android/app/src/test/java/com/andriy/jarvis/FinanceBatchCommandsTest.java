package com.andriy.jarvis;
import java.util.List;

public final class FinanceBatchCommandsTest {
    private static void yes(boolean value){if(!value)throw new AssertionError("Finance batch contract");}
    public static void main(String[] args){
        List<FinanceVoiceCommands.Entry> a=FinanceBatchCommands.parse("запиши витрати кава 85 і таксі 250");
        yes(a!=null&&a.size()==2);
        yes(a.get(0).category.equals("Їжа → Кафе")&&a.get(0).amount.equals("85"));
        yes(a.get(1).category.equals("Транспорт")&&a.get(1).amount.equals("250"));
        List<FinanceVoiceCommands.Entry> b=FinanceBatchCommands.parse("додай витрати кава 85 і таксі 250 вчора");
        yes(b!=null&&b.size()==2&&b.get(0).daysAgo==1&&b.get(1).daysAgo==1);
        yes(FinanceBatchCommands.parse("запиши витрати кава 85 і невідоме 250")==null);
        yes(FinanceBatchCommands.parse("кава 85 і таксі 250")==null);
        yes(FinanceBatchCommands.parse("запиши витрати кава 85 і таксі 250 і невідома 20")==null);
        System.out.println("PASS FinanceBatchCommandsTest");
    }
}
