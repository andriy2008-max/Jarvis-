package com.andriy.jarvis;
import android.database.Cursor;
import java.time.*;
import java.util.*;

/** Deterministic insights; never mixes currencies or fills missing history. */
public final class FinanceInsights {
    private FinanceInsights(){}
    public static String report(FinanceLedger ledger){
        LocalDate today=LocalDate.now();long start=today.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        StringBuilder out=new StringBuilder("Оцінка за внесеними записами. Пропуски обліку знижують точність.\n");
        try(Cursor accounts=ledger.getReadableDatabase().rawQuery("SELECT DISTINCT currency FROM accounts",null)){
            while(accounts.moveToNext()){
                String currency=accounts.getString(0);long income=0,expense=0,count=0,first=Long.MAX_VALUE;
                try(Cursor c=ledger.getReadableDatabase().rawQuery("SELECT e.kind,e.amount,e.happened FROM entries e JOIN accounts a ON a.id=e.account WHERE e.deleted=0 AND a.currency=? AND e.kind IN ('expense','income')",new String[]{currency})){
                    while(c.moveToNext()){count++;first=Math.min(first,c.getLong(2));if(c.getLong(2)>=start){if(c.getString(0).equals("income"))income=Math.addExact(income,c.getLong(1));else expense=Math.subtractExact(expense,c.getLong(1));}}
                }
                out.append('\n').append(currency).append("\nДоходи: ").append(FinanceLedger.money(income)).append("\nВитрати: ").append(FinanceLedger.money(expense)).append("\nРізниця: ").append(FinanceLedger.money(income-expense));
                if(income>0)out.append("\nНадлишок доходу: ").append(String.format(Locale.ROOT,"%.1f%%",100.0*(income-expense)/income));
                if(count==0||System.currentTimeMillis()-first<14L*86400000)out.append("\nДля прогнозу недостатньо історії (менше 14 днів).");
                out.append("\nОрієнтовні витрати за повний місяць: ").append(FinanceLedger.money(java.math.BigDecimal.valueOf(expense).multiply(java.math.BigDecimal.valueOf(today.lengthOfMonth())).divide(java.math.BigDecimal.valueOf(today.getDayOfMonth()),0,java.math.RoundingMode.HALF_UP).longValueExact())).append(" (простий темп витрат, не прогноз балансу)\n");
                out.append("Динаміка витрат за 6 місяців:\n");
                for(int m=5;m>=0;m--){LocalDate month=today.withDayOfMonth(1).minusMonths(m);long a=month.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),b=month.plusMonths(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
                    try(Cursor c=ledger.getReadableDatabase().rawQuery("SELECT COUNT(*),COALESCE(-SUM(e.amount),0) FROM entries e JOIN accounts a ON a.id=e.account WHERE e.deleted=0 AND e.kind='expense' AND a.currency=? AND e.happened>=? AND e.happened<?",new String[]{currency,""+a,""+b})){c.moveToFirst();out.append(month.toString().substring(0,7)).append(": ").append(c.getLong(0)==0?"немає записів":FinanceLedger.money(c.getLong(1))).append('\n');}
                }
            }
        }return out.toString();
    }
    public static String reminders(FinanceLedger ledger){
        StringBuilder s=new StringBuilder();String limit=LocalDate.now().plusDays(3).toString();
        try(Cursor c=ledger.getReadableDatabase().rawQuery("SELECT name,currency,amount-progress,due FROM plans WHERE due!='' AND due<=? AND progress<amount AND type!='budget' ORDER BY due LIMIT 3",new String[]{limit})){
            while(c.moveToNext())s.append(c.getString(0)).append(" · ").append(FinanceLedger.money(c.getLong(2))).append(' ').append(c.getString(1)).append(" · ").append(c.getString(3)).append('\n');
        }return s.toString();
    }
}
