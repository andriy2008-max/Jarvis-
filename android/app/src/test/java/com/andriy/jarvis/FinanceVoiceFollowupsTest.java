package com.andriy.jarvis;

public final class FinanceVoiceFollowupsTest {
    private static void yes(boolean p){if(!p)throw new AssertionError("Finance correction parsing");}
    public static void main(String[] args){
        FinanceVoiceFollowups.Correction a=FinanceVoiceFollowups.parse("Не 85, а 95");
        yes(a!=null&&a.oldAmount.equals("85")&&a.newAmount.equals("95"));
        FinanceVoiceFollowups.Correction b=FinanceVoiceFollowups.parse("Джарвіс, не 12,50 а 15");
        yes(b!=null&&b.oldAmount.equals("12.5")&&b.newAmount.equals("15"));
        yes(FinanceVoiceFollowups.parse("не 85 а 85")==null);
        yes(FinanceVoiceFollowups.parse("не 85 а 0")==null);
        yes(FinanceVoiceFollowups.parse("виправ на 95")==null);
        yes(FinanceVoiceFollowups.parse("таксі 250")==null);
        System.out.println("PASS FinanceVoiceFollowupsTest");
    }
}
