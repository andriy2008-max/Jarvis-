package com.andriy.jarvis;

import android.database.Cursor;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;

/** Cross-currency estimated net worth using explicitly dated cached official NBU rates. */
public final class FinanceNetWorth {
    private FinanceNetWorth(){}
    private static final class Exchange {
        final BigDecimal rate;final String date;
        Exchange(BigDecimal r,String d){rate=r;date=d;}
    }
    public static String report(FinanceLedger ledger){
        JSONObject rates;
        try{rates=new JSONObject(ledger.setting("nbu_rates","{}"));}
        catch(Exception e){rates=new JSONObject();}
        Map<String,Exchange> fx=new HashMap<>();
        fx.put("UAH",new Exchange(BigDecimal.ONE,LocalDate.now().toString()));
        for(java.util.Iterator<String> it=rates.keys();it.hasNext();){
            String code=it.next();JSONObject r=rates.optJSONObject(code);
            if(r==null)continue;
            try{
                BigDecimal value=new BigDecimal(r.getString("rate"));
                if(value.signum()>0)fx.put(code,new Exchange(value,r.getString("date")));
            }catch(Exception ignored){}
        }
        BigDecimal cash=BigDecimal.ZERO,receivables=BigDecimal.ZERO,liabilities=BigDecimal.ZERO,crypto=BigDecimal.ZERO;
        StringBuilder notes=new StringBuilder();int unavailable=0;
        try(Cursor c=ledger.getReadableDatabase().rawQuery("SELECT id,name,currency FROM accounts ORDER BY name",null)){
            while(c.moveToNext()){
                Exchange rate=fx.get(c.getString(2));
                try{
                    if(rate==null)throw new IllegalStateException("немає курсу");
                    long balance=ledger.balance(c.getLong(0));
                    cash=cash.add(BigDecimal.valueOf(balance,2).multiply(rate.rate));
                    notes.append(c.getString(1)).append(" (").append(c.getString(2))
                        .append("): курс ").append(rate.date).append("\n");
                }catch(Exception e){unavailable++;notes.append(c.getString(1)).append(": не оцінено\n");}
            }
        }
        try(Cursor c=ledger.getReadableDatabase().rawQuery(
            "SELECT type,currency,COALESCE(SUM(amount-progress),0) FROM plans "+
            "WHERE type IN ('owed','owing') AND progress<amount GROUP BY type,currency",null)){
            while(c.moveToNext()){
                Exchange rate=fx.get(c.getString(1));
                if(rate==null){unavailable++;notes.append("Борг ").append(c.getString(1)).append(": немає курсу\n");continue;}
                BigDecimal value=BigDecimal.valueOf(c.getLong(2),2).multiply(rate.rate);
                if(c.getString(0).equals("owed"))receivables=receivables.add(value);
                else liabilities=liabilities.add(value);
            }
        }
        try(Cursor c=ledger.getReadableDatabase().rawQuery(
            "SELECT symbol,quantity,value_uah,valued_on FROM crypto_positions",null)){
            while(c.moveToNext()){
                if(c.isNull(2)){unavailable++;notes.append(c.getString(0)).append(": немає ручної ціни\n");continue;}
                try{
                    crypto=crypto.add(new BigDecimal(c.getString(1)).multiply(new BigDecimal(c.getString(2))));
                    notes.append(c.getString(0)).append(": оцінка від ").append(c.getString(3)).append("\n");
                }catch(Exception e){unavailable++;}
            }
        }
        StringBuilder out=new StringBuilder("Оцінка активів і зобов'язань у гривнях (UAH)\n");
        out.append("Грошові рахунки: ").append(money(cash)).append(" ₴\n")
            .append("Мені винні: ").append(money(receivables)).append(" ₴\n")
            .append("Я винен: ").append(money(liabilities)).append(" ₴\n")
            .append("Оцінені криптоактиви: ").append(money(crypto)).append(" ₴\n");
        BigDecimal net=cash.add(receivables).add(crypto).subtract(liabilities);
        out.append(unavailable==0?"Орієнтовний чистий капітал: ":"Частково оцінений підсумок: ")
            .append(money(net)).append(" ₴\n");
        if(unavailable>0)out.append("Неоцінених позицій: ").append(unavailable).append("; підсумок неповний.\n");
        out.append(notes);
        out.append("НБУ — довідкова оцінка за збереженою датою, не виконана конвертація. Криптоціни ручні; повної гарантії актуальності немає.");
        return out.toString();
    }
    private static String money(BigDecimal value){return value.setScale(2,RoundingMode.HALF_UP).toPlainString();}
}
