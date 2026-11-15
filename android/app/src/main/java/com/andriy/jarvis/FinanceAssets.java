package com.andriy.jarvis;

import android.database.Cursor;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

/** Manual cash reconciliation, factual FX exchanges and quantity-preserving crypto register. */
public final class FinanceAssets {
    private FinanceAssets(){}

    public static String difference(FinanceLedger ledger,long account,String observed){
        long actual=FinanceLedger.minor(observed);
        try{
            long booked=ledger.balance(account);
            return "Облік: "+FinanceLedger.money(booked)+"; фактично: "+
                FinanceLedger.money(actual)+"; різниця: "+
                FinanceLedger.money(Math.subtractExact(actual,booked))+" "+ledger.currency(account)+
                ". Підтвердження встановить нову контрольну дату, історія операцій залишиться.";
        }catch(IllegalArgumentException ex){
            return "Обліковий залишок невідомий. Встановити "+FinanceLedger.money(actual)+
                " "+ledger.currency(account)+" як фактичний залишок на поточний момент?";
        }
    }
    public static void reconcile(FinanceLedger ledger,long account,String observed){
        long actual=FinanceLedger.minor(observed);
        SQLiteDatabase db=ledger.getWritableDatabase();db.beginTransaction();try{
            try(Cursor c=db.rawQuery("SELECT opening FROM accounts WHERE id=?",new String[]{""+account})){
                if(!c.moveToFirst())throw new IllegalArgumentException("Рахунок не знайдено");
                Long previous=c.isNull(0)?null:c.getLong(0);
                db.execSQL("INSERT INTO audit(entry_id,event,happened,previous_amount) VALUES(?,?,?,?)",
                    new Object[]{"account:"+account,"balance-reconciliation",System.currentTimeMillis(),previous});
            }
            db.execSQL("UPDATE accounts SET opening=?,opening_at=? WHERE id=?",
                new Object[]{actual,System.currentTimeMillis(),account});
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }

    /** An exchange is one atomic journal group; fee is a separately classified expense. */
    public static void exchange(FinanceLedger ledger,long from,long to,String debit,String credit,String fee){
        if(from==to)throw new IllegalArgumentException("Вибери різні рахунки");
        String fromCurrency=ledger.currency(from),toCurrency=ledger.currency(to);
        if(fromCurrency.equals(toCurrency))throw new IllegalArgumentException("Для однієї валюти використовуй звичайний переказ");
        long out=FinanceLedger.minor(debit),in=FinanceLedger.minor(credit);
        long cost=fee==null||fee.trim().isEmpty()?0:FinanceLedger.minor(fee);
        if(out<=0||in<=0||cost<0)throw new IllegalArgumentException("Перевір фактичні суми та комісію");
        long now=System.currentTimeMillis();String id=UUID.randomUUID().toString();
        SQLiteDatabase db=ledger.getWritableDatabase();db.beginTransaction();try{
            db.execSQL("INSERT INTO fx_transfers VALUES(?,?,?,?,?,?,?)",
                new Object[]{id,from,to,out,in,cost,now});
            insert(db,id+"-debit",from,-out,"transfer","Обмін","",now,id);
            insert(db,id+"-credit",to,in,"transfer","Обмін","",now,id);
            if(cost>0)insert(db,id+"-fee",from,-cost,"expense","Комісії","Комісія обміну "+id,now,id);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    private static void insert(SQLiteDatabase db,String id,long account,long value,String kind,
                               String category,String note,long happened,String group){
        db.execSQL("INSERT INTO entries(id,account,amount,category,note,happened,kind,group_id) VALUES(?,?,?,?,?,?,?,?)",
            new Object[]{id,account,value,category,note,happened,kind,group});
    }
    /** A manual crypto position never implies a trade or bank transfer. */
    public static void position(FinanceLedger ledger,String ticker,String quantity,String unitUah,String valuationDate){
        String symbol=ticker.trim().toUpperCase(Locale.ROOT);
        if(!symbol.matches("[A-Z0-9]{2,16}"))throw new IllegalArgumentException("Код активу: 2–16 латинських літер або цифр");
        BigDecimal qty;
        try{qty=new BigDecimal(quantity.trim().replace(',','.'));if(qty.signum()<0||qty.scale()>18||qty.precision()>30)throw new NumberFormatException();}
        catch(RuntimeException e){throw new IllegalArgumentException("Вкажи точну кількість активу (до 18 знаків після коми)");}
        String price=null,date="";
        if(unitUah!=null&&!unitUah.trim().isEmpty()){
            try{
                BigDecimal amount=new BigDecimal(unitUah.trim().replace(',','.'));
                if(amount.signum()<0||amount.precision()>24)throw new NumberFormatException();
                price=amount.toPlainString();
            }catch(RuntimeException e){throw new IllegalArgumentException("Неправильна ручна оцінка в UAH за одиницю");}
            date=valuationDate==null||valuationDate.trim().isEmpty()?LocalDate.now().toString():valuationDate.trim();
            try{LocalDate.parse(date);}catch(RuntimeException e){throw new IllegalArgumentException("Дата оцінки: РРРР-ММ-ДД");}
        }
        SQLiteDatabase db=ledger.getWritableDatabase();
        db.execSQL("INSERT OR REPLACE INTO crypto_positions(symbol,quantity,value_uah,valued_on) VALUES(?,?,?,?)",
            new Object[]{symbol,qty.toPlainString(),price,date});
    }
    public static String positions(FinanceLedger ledger){
        StringBuilder b=new StringBuilder("Активи (кількість з ручного обліку):\n");
        BigDecimal total=BigDecimal.ZERO;boolean incomplete=false,any=false;
        try(Cursor c=ledger.getReadableDatabase().rawQuery("SELECT symbol,quantity,value_uah,valued_on FROM crypto_positions ORDER BY symbol",null)){
            while(c.moveToNext()){
                any=true;
                BigDecimal quantity=new BigDecimal(c.getString(1));
                b.append(c.getString(0)).append(": ").append(quantity.toPlainString()).append(" од.");
                if(c.isNull(2)){incomplete=true;b.append(" · не оцінено\n");}
                else{
                    BigDecimal value=quantity.multiply(new BigDecimal(c.getString(2))).setScale(2,RoundingMode.HALF_UP);
                    total=total.add(value);
                    b.append(" · ").append(value.toPlainString()).append(" ₴ (за ручною ціною від ").append(c.getString(3)).append(")\n");
                }
            }
        }
        if(!any)return "Криптоактивів не внесено.";
        return b.append(incomplete?"Оцінена частина: ":"Сума оцінок: ").append(total.toPlainString())
            .append(" ₴. Не є актуальним ринковим курсом.").toString();
    }
}
