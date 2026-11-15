package com.andriy.jarvis;

/** Deterministic recurrence anchored to the original date in short months. */
public final class FinanceCalendar {
    private FinanceCalendar(){}
    public static String nextDue(String date,String unit,int step,int anchor){
        if(step<=0||step>3650)throw new IllegalArgumentException("Некоректна періодичність");
        java.time.LocalDate previous=java.time.LocalDate.parse(date);
        if("day".equals(unit))return previous.plusDays(step).toString();
        if("week".equals(unit))return previous.plusWeeks(step).toString();
        if(!"month".equals(unit)&&!"year".equals(unit))
            throw new IllegalArgumentException("Періодичність: день, тиждень, місяць або рік");
        long months="year".equals(unit)?Math.multiplyExact((long)step,12):step;
        java.time.YearMonth next=java.time.YearMonth.from(previous).plusMonths(months);
        return next.atDay(Math.min(anchor>0?anchor:previous.getDayOfMonth(),next.lengthOfMonth())).toString();
    }
}
