package com.andriy.jarvis;

import android.database.Cursor;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

/** Read-only, deterministic period reports and conditional planning. */
public final class FinanceAnalytics {
    private FinanceAnalytics(){}
    public static long begin(String period){
        LocalDate today=LocalDate.now();LocalDate date;
        switch(period){
            case "today":date=today;break;
            case "week":date=today.minusDays(today.getDayOfWeek().getValue()-1);break;
            case "previous_month":date=today.withDayOfMonth(1).minusMonths(1);break;
            default:date=today.withDayOfMonth(1);
        }
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
    public static long end(String period){
        LocalDate today=LocalDate.now();
        if("previous_month".equals(period))return today.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return System.currentTimeMillis()+1;
    }
    public static long spent(FinanceLedger l,String currency,String category,String period){
        try(Cursor c=l.getReadableDatabase().rawQuery(
            "SELECT COALESCE(-SUM(e.amount),0) FROM entries e JOIN accounts a ON a.id=e.account "+
            "WHERE e.deleted=0 AND e.kind IN ('expense','refund') AND a.currency=? AND (?='' OR e.category=?) "+
            "AND e.happened>=? AND e.happened<?",
            new String[]{currency,category,category,""+begin(period),""+end(period)})){
            c.moveToFirst();return c.getLong(0);
        }
    }
    public static String budgets(FinanceLedger l){
        StringBuilder b=new StringBuilder("Бюджети за поточний період:\n");boolean any=false;
        try(Cursor c=l.getReadableDatabase().rawQuery(
            "SELECT name,category,currency,amount,budget_period FROM plans WHERE type='budget' ORDER BY name",null)){
            while(c.moveToNext()){
                any=true;String period=c.getString(4), currency=c.getString(2);
                long used=spent(l,currency,c.getString(1),period);
                long remain=Math.subtractExact(c.getLong(3),used);
                b.append(c.getString(0)).append(period.equals("week")?" (тиждень): ":" (місяць): ")
                    .append(FinanceLedger.money(used)).append(" з ").append(FinanceLedger.money(c.getLong(3)))
                    .append(" ").append(currency).append("; залишилось ").append(FinanceLedger.money(remain)).append("\n");
            }
        }
        return any?b.toString():"Бюджетів поки немає.";
    }
    public static String report(FinanceLedger l,String period){
        String label=period.equals("today")?"Сьогодні":period.equals("week")?"Цей тиждень":
            period.equals("previous_month")?"Попередній місяць":"Цей місяць";
        StringBuilder b=new StringBuilder(label+" · фактичні записи\n");
        boolean any=false;
        try(Cursor c=l.getReadableDatabase().rawQuery(
            "SELECT a.currency,COALESCE(SUM(CASE WHEN e.kind='income' THEN e.amount ELSE 0 END),0),"+
            "COALESCE(-SUM(CASE WHEN e.kind IN ('expense','refund') THEN e.amount ELSE 0 END),0) "+
            "FROM entries e JOIN accounts a ON e.account=a.id WHERE e.deleted=0 "+
            "AND e.kind IN ('expense','refund','income') AND e.happened>=? AND e.happened<? GROUP BY a.currency",
            new String[]{""+begin(period),""+end(period)})){
            while(c.moveToNext()){
                any=true;long income=c.getLong(1),out=c.getLong(2);
                b.append(c.getString(0)).append(": доходи ").append(FinanceLedger.money(income))
                    .append("; чисті витрати ").append(FinanceLedger.money(out))
                    .append("; різниця ").append(FinanceLedger.money(Math.subtractExact(income,out))).append("\n");
                if(income>0){
                    BigDecimal percent=BigDecimal.valueOf(income-out).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(income),1,RoundingMode.HALF_UP);
                    b.append("Частка надлишку доходів: ").append(percent.toPlainString()).append("% (не відкладені кошти)\n");
                }else b.append("Норма заощаджень не визначається без додатного доходу\n");
                b.append("Найбільші категорії:\n");
                try(Cursor top=l.getReadableDatabase().rawQuery(
                    "SELECT e.category,COALESCE(-SUM(e.amount),0) AS value FROM entries e JOIN accounts a ON e.account=a.id "+
                    "WHERE e.deleted=0 AND e.kind IN ('expense','refund') AND a.currency=? AND e.happened>=? AND e.happened<? "+
                    "GROUP BY e.category ORDER BY value DESC LIMIT 5",
                    new String[]{c.getString(0),""+begin(period),""+end(period)})){
                    while(top.moveToNext())b.append("• ").append(top.getString(0)).append(": ")
                        .append(FinanceLedger.money(top.getLong(1))).append("\n");
                }
            }
        }
        return any?b.toString():"За обраний період записів немає; нуль не означає відсутності витрат.";
    }
    public static String knownPayments(FinanceLedger l){
        LocalDate today=LocalDate.now(),last=today.withDayOfMonth(today.lengthOfMonth());
        StringBuilder b=new StringBuilder("До кінця місяця · підтверджені в плані майбутні платежі:\n");
        boolean any=false;
        try(Cursor c=l.getReadableDatabase().rawQuery(
            "SELECT currency,COALESCE(SUM(amount-progress),0),COUNT(*) FROM plans "+
            "WHERE type='payment' AND due>=? AND due<=? AND progress<amount GROUP BY currency",
            new String[]{today.toString(),last.toString()})){
            while(c.moveToNext()){
                any=true;b.append(FinanceLedger.money(c.getLong(1))).append(" ")
                    .append(c.getString(0)).append(" · ").append(c.getLong(2)).append(" платежів\n");
            }
        }
        if(!any)b.append("Немає внесених майбутніх платежів.\n");
        b.append("Це відомі платежі, не прогноз балансу: непередбачені витрати й майбутній дохід не враховані.");
        return b.toString();
    }
    public static String scenario(FinanceLedger l,int cutPercent){
        if(cutPercent<0||cutPercent>100)throw new IllegalArgumentException("Частка скорочення має бути 0–100%");
        StringBuilder b=new StringBuilder("Що як зменшити зареєстровані місячні витрати на ")
            .append(cutPercent).append("%? (не змінює жодного бюджету)\n");
        boolean any=false;
        try(Cursor c=l.getReadableDatabase().rawQuery("SELECT DISTINCT currency FROM accounts",null)){
            while(c.moveToNext()){
                any=true;String currency=c.getString(0);
                long spent=Math.max(0,spent(l,currency,"","month"));
                long save=BigDecimal.valueOf(spent).multiply(BigDecimal.valueOf(cutPercent))
                    .divide(BigDecimal.valueOf(100),0,RoundingMode.HALF_UP).longValueExact();
                b.append(currency).append(": умовна економія ").append(FinanceLedger.money(save))
                    .append(" за такий самий обсяг витрат.\n");
            }
        }
        return any?b.toString():"Рахунків ще немає.";
    }
    public static String audit(FinanceLedger l){
        StringBuilder b=new StringBuilder("Аудит можливих повторів і великих витрат (не видаляє записи):\n");
        boolean found=false;
        try(Cursor c=l.getReadableDatabase().rawQuery(
            "SELECT a.currency,e1.category,e1.amount,e1.happened FROM entries e1 "+
            "JOIN entries e2 ON e1.account=e2.account AND e1.kind=e2.kind AND e1.category=e2.category "+
            "AND e1.amount=e2.amount AND e1.id!=e2.id AND e1.happened>e2.happened "+
            "AND e1.happened-e2.happened<=120000 "+
            "JOIN accounts a ON a.id=e1.account WHERE e1.deleted=0 AND e2.deleted=0 "+
            "AND e1.kind='expense' AND (e1.group_id IS NULL OR e1.group_id!=e2.group_id) "+
            "ORDER BY e1.happened DESC LIMIT 10",null)){
            while(c.moveToNext()){
                found=true;b.append("Можливе дублювання: ").append(FinanceLedger.money(-c.getLong(2)))
                    .append(" ").append(c.getString(0)).append(" · ").append(c.getString(1)).append("\n");
            }
        }
        Map<String,List<Long>> samples=new LinkedHashMap<>();
        try(Cursor c=l.getReadableDatabase().rawQuery(
            "SELECT a.currency,e.category,-e.amount FROM entries e JOIN accounts a ON a.id=e.account "+
            "WHERE e.deleted=0 AND e.kind='expense' AND e.amount<0 ORDER BY e.happened DESC LIMIT 2000",null)){
            while(c.moveToNext())samples.computeIfAbsent(c.getString(0)+" · "+c.getString(1),k->new ArrayList<>()).add(c.getLong(2));
        }
        int suggestions=0;
        for(Map.Entry<String,List<Long>> entry:samples.entrySet()){
            List<Long> values=entry.getValue();
            if(values.size()<11)continue;
            List<Long> previous=new ArrayList<>(values.subList(1,values.size()));Collections.sort(previous);
            long median=previous.get(previous.size()/2),latest=values.get(0);
            if(median>0&&latest>median*2L&&suggestions++<3){
                found=true;b.append("Незвична сума: ").append(entry.getKey()).append(" · ")
                    .append(FinanceLedger.money(latest)).append(" проти медіани ")
                    .append(FinanceLedger.money(median)).append(" (10+ попередніх записів)\n");
            }
        }
        return found?b.toString():"За наявними даними достатньо надійних сигналів не знайдено.";
    }
}
