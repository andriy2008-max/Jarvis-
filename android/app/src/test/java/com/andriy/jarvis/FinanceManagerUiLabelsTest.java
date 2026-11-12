package com.andriy.jarvis;

public final class FinanceManagerUiLabelsTest {
    private static void yes(boolean x){if(!x)throw new AssertionError("Finance screen guard");}
    public static void main(String[] args){
        int w=684,h=1536;
        yes(FinanceManagerUiLabels.screenshotShape(w,h));
        yes(!FinanceManagerUiLabels.screenshotShape(1536,684));
        yes(FinanceManagerUiLabels.plusPoint(w,h)[0]==342);
        yes(FinanceManagerUiLabels.categoryPoint(
            FinanceVoiceCommands.Kind.EXPENSE,"Транспорт",w,h)[0]==422);
        yes(FinanceManagerUiLabels.categoryPoint(
            FinanceVoiceCommands.Kind.EXPENSE,"Харчування",w,h)[1]==322);
        yes(FinanceManagerUiLabels.categoryPoint(
            FinanceVoiceCommands.Kind.INCOME,"Зарплата",w,h)[0]==584);
        yes(FinanceManagerUiLabels.categoryPoint(
            FinanceVoiceCommands.Kind.INCOME,"Транспорт",w,h)==null);
        yes(FinanceManagerUiLabels.keypadPoint('9',w,h)[0]==415);
        yes(FinanceManagerUiLabels.keypadPoint('x',w,h)==null);
        yes(FinanceManagerUiLabels.confirmPoint(1080,2424)[0]>850);
        yes(FinanceManagerUiLabels.amountEquals("350","350"));
        yes(FinanceManagerUiLabels.amountEquals("13523.5","13 523,50"));
        yes(!FinanceManagerUiLabels.amountEquals("350","35"));
        yes(!FinanceManagerUiLabels.amountEquals("350",""));
        System.out.println("PASS FinanceManagerUiLabelsTest");
    }
}
