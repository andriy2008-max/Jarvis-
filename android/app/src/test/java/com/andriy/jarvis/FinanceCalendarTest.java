package com.andriy.jarvis;

public final class FinanceCalendarTest {
    private static void eq(String want,String actual){
        if(!want.equals(actual))throw new AssertionError(want+" != "+actual);
    }
    public static void main(String[] args){
        eq("2026-02-28",FinanceCalendar.nextDue("2026-01-31","month",1,31));
        eq("2026-03-31",FinanceCalendar.nextDue("2026-02-28","month",1,31));
        eq("2028-02-29",FinanceCalendar.nextDue("2028-01-31","month",1,31));
        eq("2027-02-28",FinanceCalendar.nextDue("2026-02-28","year",1,29));
        eq("2028-02-29",FinanceCalendar.nextDue("2027-02-28","year",1,29));
        eq("2026-11-16",FinanceCalendar.nextDue("2026-11-15","day",1,15));
        eq("2026-11-22",FinanceCalendar.nextDue("2026-11-15","week",1,15));
        try{
            FinanceCalendar.nextDue("2026-01-01","week",0,1);
            throw new AssertionError("Zero recurrence must fail");
        }catch(IllegalArgumentException expected){}
        System.out.println("PASS FinanceCalendarTest");
    }
}
