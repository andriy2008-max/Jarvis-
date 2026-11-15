package com.andriy.jarvis;

import android.database.Cursor;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;

/** Evidence-limited financial health view; no fabricated assets or forecasts. */
public final class FinanceHealth {
    private FinanceHealth(){}
    public static String report(FinanceLedger l){
        long liquid=0,receivables=0,payables=0;
        int unknown=0;boolean cash=false,cryptoIncomplete=false;
        StringBuilder b=new StringBuilder("Фінансова картина · UAH (окремо від інших валют)\n");
        try(Cursor c=l.getReadableDatabase().rawQuery("SELECT id FROM accounts WHERE currency='UAH'",null)){
            while(c.moveToNext()){
                try{liquid=Math.addExact(liquid,l.balance(c.getLong(0)));cash=true;}
                catch(IllegalArgumentException e){unknown++;}
            }
        }
        try(Cursor c=l.getReadableDatabase().rawQuery(
            "SELECT type,COALESCE(SUM(amount-progress),0) FROM plans WHERE type IN ('owed','owing') AND currency='UAH' GROUP BY type",null)){
            while(c.moveToNext()){
                if(c.getString(0).equals("owed"))receivables=c.getLong(1);
                else payables=c.getLong(1);
            }
        }
        BigDecimal crypto=BigDecimal.ZERO;int positions=0;
        try(Cursor c=l.getReadableDatabase().rawQuery("SELECT quantity,value_uah FROM crypto_positions",null)){
            while(c.moveToNext()){
                positions++;
                if(c.isNull(1)){cryptoIncomplete=true;continue;}
                crypto=crypto.add(new BigDecimal(c.getString(0)).multiply(new BigDecimal(c.getString(1))));
            }
        }
        if(cash)b.append("Відомі грошові рахунки: ").append(FinanceLedger.money(liquid)).append(" ₴\n");
        else b.append("Немає відомих UAH залишків.\n");
        if(unknown>0)b.append("Рахунків з невідомим залишком: ").append(unknown).append("\n");
        b.append("Мені винні: ").append(FinanceLedger.money(receivables)).append(" ₴; мої борги: ")
            .append(FinanceLedger.money(payables)).append(" ₴\n");
        if(positions>0)b.append("Оцінені криптоактиви: ")
            .append(crypto.setScale(2,RoundingMode.HALF_UP).toPlainString())
            .append(" ₴, лише за введеними вручну цінами")
            .append(cryptoIncomplete?" (частина не оцінена)\n":"\n");
        if(cash&&unknown==0&&!cryptoIncomplete){
            BigDecimal net=BigDecimal.valueOf(liquid,2)
                .add(BigDecimal.valueOf(receivables-payables,2)).add(crypto);
            b.append("Умовний чистий капітал у UAH: ").append(net.setScale(2,RoundingMode.HALF_UP).toPlainString())
                .append(" ₴; без неврахованих активів, інших валют і зобов'язань\n");
        }else b.append("Повний чистий капітал не визначено через неповні залишки або оцінки.\n");
        LocalDate current=LocalDate.now().withDayOfMonth(1);
        long total=0;int months=0;
        for(int n=1;n<=3;n++){
            LocalDate month=current.minusMonths(n);
            long first=month.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long last=month.plusMonths(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            try(Cursor c=l.getReadableDatabase().rawQuery(
                "SELECT COUNT(*),COALESCE(-SUM(e.amount),0) FROM entries e JOIN accounts a ON e.account=a.id "+
                "WHERE e.deleted=0 AND e.kind IN ('expense','refund') AND a.currency='UAH' "+
                "AND e.happened>=? AND e.happened<?",new String[]{""+first,""+last})){
                c.moveToFirst();
                if(c.getLong(0)>0){months++;total=Math.addExact(total,Math.max(0,c.getLong(1)));}
            }
        }
        if(months<3)b.append("Подушка у місяцях: недостатньо даних — потрібні 3 повні місяці витрат.\n");
        else if(total<=0)b.append("Подушка у місяцях не визначається без додатних витрат.\n");
        else if(cash&&unknown==0){
            BigDecimal avg=BigDecimal.valueOf(total).divide(BigDecimal.valueOf(3),2,RoundingMode.HALF_UP);
            BigDecimal ratio=BigDecimal.valueOf(liquid).divide(avg,1,RoundingMode.HALF_UP);
            b.append("Подушка: приблизно ").append(ratio.toPlainString()).append(" міс. за історичними витратами\n");
        }else b.append("Подушка не оцінена, оскільки відомі не всі грошові залишки.\n");
        b.append("Цифри лише з внесених даних. Це аналітика, а не порада купувати фінансові продукти.");
        return b.toString();
    }
}
