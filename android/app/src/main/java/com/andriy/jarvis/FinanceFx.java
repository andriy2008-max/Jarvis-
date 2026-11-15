package com.andriy.jarvis;
import java.net.*;
import java.io.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** Dated official rates for valuation; never changes the amounts of recorded transactions. */
public final class FinanceFx {
    private FinanceFx(){}
    public static void refresh(FinanceLedger ledger)throws Exception{
        String day=java.time.LocalDate.now(java.time.ZoneId.of("Europe/Kyiv")).toString();
        URL url=new URL("https://bank.gov.ua/NBUStatService/v1/statdirectory/exchange?date="+day.replace("-","")+"&json");
        HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(15000);c.setReadTimeout(15000);c.setInstanceFollowRedirects(false);
        try{if(c.getResponseCode()!=200)throw new IOException("Курси недоступні");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1){if(bytes.size()+n>1024*1024)throw new IOException("Response too large");bytes.write(buf,0,n);}}
            JSONArray rates=new JSONArray(bytes.toString(StandardCharsets.UTF_8.name()));JSONObject snapshot=new JSONObject();
            for(int i=0;i<rates.length();i++){JSONObject r=rates.getJSONObject(i);String code=r.getString("cc");BigDecimal rate=new BigDecimal(r.get("rate").toString());String date=r.getString("exchangedate");
                if(!code.matches("[A-Z]{3}")||rate.signum()<=0)continue;
                snapshot.put(code,new JSONObject().put("rate",rate.toPlainString()).put("date",date));
            }
            if(snapshot.length()==0)throw new IOException("Порожня відповідь НБУ");ledger.setSetting("nbu_rates",snapshot.toString());
        }finally{c.disconnect();}
    }
    public static String valuation(FinanceLedger ledger){
        StringBuilder b=new StringBuilder("Оцінка рахунків у UAH за курсом НБУ\n");BigDecimal total=BigDecimal.ZERO;boolean complete=true;
        JSONObject rates;try{rates=new JSONObject(ledger.setting("nbu_rates","{}"));}catch(Exception e){rates=new JSONObject();}
        try(android.database.Cursor c=ledger.getReadableDatabase().rawQuery("SELECT id,name,currency FROM accounts",null)){
            while(c.moveToNext()){
                try{long balance=ledger.balance(c.getLong(0));String code=c.getString(2);BigDecimal rate=BigDecimal.ONE;String date="";
                    if(!code.equals("UAH")){JSONObject item=rates.optJSONObject(code);if(item==null)throw new IllegalArgumentException();rate=new BigDecimal(item.getString("rate"));date=" · курс "+item.getString("date");}
                    BigDecimal value=BigDecimal.valueOf(balance,2).multiply(rate).setScale(2,RoundingMode.HALF_UP);total=total.add(value);b.append(c.getString(1)).append(": ").append(value.toPlainString()).append(" ₴").append(date).append('\n');
                }catch(Exception e){complete=false;b.append(c.getString(1)).append(": не оцінено\n");}
            }
        }return b.append(complete?"Разом: ":"Відомі оцінки (неповний підсумок): ").append(total.toPlainString()).append(" ₴\nЦе оцінка, не курс фактичного обміну.").toString();
    }
}
