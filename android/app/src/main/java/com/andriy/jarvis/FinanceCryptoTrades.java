package com.andriy.jarvis;

import android.database.Cursor;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Manual spot crypto trade ledger. Actual bank settlement is an asset transfer,
 * never fake salary/income or discretionary consumption. Fees are expenses.
 */
public final class FinanceCryptoTrades {
    private FinanceCryptoTrades(){}
    private static BigDecimal quantity(String value){
        try{
            BigDecimal q=new BigDecimal(value.trim().replace(',','.'));
            if(q.signum()<=0||q.scale()>18||q.precision()>30)throw new IllegalArgumentException();
            return q;
        }catch(RuntimeException e){throw new IllegalArgumentException("Кількість: додатне число, максимум 18 знаків після коми.");}
    }
    public static void trade(FinanceLedger ledger,String symbol,String action,String quantity,
                             String total,String fee,long cashAccount){
        String ticker=symbol.trim().toUpperCase(Locale.ROOT);
        if(!ticker.matches("[A-Z0-9]{2,16}"))throw new IllegalArgumentException("Код активу: BTC, ETH тощо");
        if(!action.equals("buy")&&!action.equals("sell"))throw new IllegalArgumentException("Оберіть купівлю або продаж");
        BigDecimal qty=quantity(quantity);
        long value=FinanceLedger.minor(total),feeMinor=FinanceLedger.minor(fee.trim().isEmpty()?"0":fee);
        if(value<=0||feeMinor<0||action.equals("sell")&&feeMinor>value)
            throw new IllegalArgumentException("Перевір суму угоди та комісію");
        if(cashAccount>0&&!ledger.currency(cashAccount).equals("UAH"))
            throw new IllegalArgumentException("Для проведення коштів вибери рахунок у гривнях");
        SQLiteDatabase db=ledger.getWritableDatabase();
        db.beginTransaction();try{
            BigDecimal before=BigDecimal.ZERO;
            try(Cursor c=db.rawQuery("SELECT quantity FROM crypto_positions WHERE symbol=?",new String[]{ticker})){
                if(c.moveToFirst())before=new BigDecimal(c.getString(0));
            }
            BigDecimal after=action.equals("buy")?before.add(qty):before.subtract(qty);
            if(after.signum()<0)throw new IllegalArgumentException("Недостатня кількість криптоактиву для продажу");
            String id=UUID.randomUUID().toString();long now=System.currentTimeMillis();
            db.execSQL("INSERT INTO crypto_trades(id,symbol,action,quantity,price_uah_minor,fee_uah_minor,account,happened) VALUES(?,?,?,?,?,?,?,?)",
                new Object[]{id,ticker,action,qty.toPlainString(),value,feeMinor,cashAccount>0?cashAccount:null,now});
            db.execSQL("INSERT OR IGNORE INTO crypto_positions(symbol,quantity,value_uah,valued_on) VALUES(?,? ,NULL,'')",
                new Object[]{ticker,"0"});
            db.execSQL("UPDATE crypto_positions SET quantity=? WHERE symbol=?",
                new Object[]{after.toPlainString(),ticker});
            if(cashAccount>0){
                long net=action.equals("buy")?-value:Math.subtractExact(value,feeMinor);
                db.execSQL("INSERT INTO entries(id,account,amount,category,note,happened,kind,group_id) VALUES(?,?,?,?,?,?,?,?)",
                    new Object[]{id+":asset",cashAccount,net,"Криптоактиви",
                        action.equals("buy")?"Купівля "+ticker:"Продаж "+ticker,now,"asset",id});
                if(action.equals("buy")&&feeMinor>0)db.execSQL(
                    "INSERT INTO entries(id,account,amount,category,note,happened,kind,group_id) VALUES(?,?,?,?,?,?,?,?)",
                    new Object[]{id+":fee",cashAccount,-feeMinor,"Комісії",
                        "Комісія криптоугоди "+ticker,now,"expense",id});
                if(action.equals("sell")&&feeMinor>0)db.execSQL(
                    "INSERT INTO entries(id,account,amount,category,note,happened,kind,group_id) VALUES(?,?,?,?,?,?,?,?)",
                    new Object[]{id+":fee",cashAccount,-feeMinor,"Комісії",
                        "Комісія криптоугоди "+ticker+" (утримана з виручки)",now,"memo_fee",id});
                // For sales the credited amount is already net of fee, so memo_fee
                // is explicitly excluded from income and cash-balance calculations.
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }

    /** Trade history only: manual opening positions have unknown acquisition costs. */
    public static String history(FinanceLedger ledger){
        StringBuilder b=new StringBuilder("Криптоугоди · ручний облік без ринкових котирувань\n");
        boolean any=false;
        try(Cursor c=ledger.getReadableDatabase().rawQuery(
            "SELECT symbol,action,quantity,price_uah_minor,fee_uah_minor,happened FROM crypto_trades ORDER BY happened DESC LIMIT 40",null)){
            while(c.moveToNext()){
                any=true;b.append(c.getString(1).equals("buy")?"Купівля ":"Продаж ")
                    .append(c.getString(0)).append(" · ").append(c.getString(2))
                    .append(" од. · ").append(FinanceLedger.money(c.getLong(3)))
                    .append(" ₴ за всю угоду, комісія ").append(FinanceLedger.money(c.getLong(4)))
                    .append(" ₴ · ").append(java.time.Instant.ofEpochMilli(c.getLong(5))
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDate()).append("\n");
            }
        }
        b.append("Історичні ціни введені вручну. Собівартість старих активів без історії покупок невідома.");
        return any?b.toString():"Криптоугод поки немає.";
    }
}
