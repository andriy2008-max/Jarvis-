package com.andriy.jarvis;

import android.database.Cursor;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/** Grouped tips are recorded as exactly the supplied split, not as a third total. */
public final class FinanceShifts {
    private FinanceShifts(){}
    public static void tips(FinanceLedger ledger,String label,String total,String cash,String card,
                            long cashAccount,long cardAccount,String hours){
        long expected=FinanceLedger.minor(total),inCash=FinanceLedger.minor(cash),inCard=FinanceLedger.minor(card);
        if(expected<=0||inCash<0||inCard<0||Math.addExact(inCash,inCard)!=expected)
            throw new IllegalArgumentException("Готівка + картка мають точно дорівнювати сумі чайових");
        if((inCash>0&&!ledger.currency(cashAccount).equals("UAH"))||
           (inCard>0&&!ledger.currency(cardAccount).equals("UAH")))
            throw new IllegalArgumentException("Чайові в гривнях записуються лише на рахунки UAH");
        String h=null;
        if(hours!=null&&!hours.trim().isEmpty()){
            try{
                BigDecimal value=new BigDecimal(hours.trim().replace(',','.'));
                if(value.signum()<=0||value.compareTo(new BigDecimal("24"))>0||
                    value.scale()>2)throw new NumberFormatException();
                h=value.toPlainString();
            }catch(RuntimeException e){throw new IllegalArgumentException("Години зміни: понад 0, максимум 24");}
        }
        String id=UUID.randomUUID().toString();long now=System.currentTimeMillis();
        SQLiteDatabase db=ledger.getWritableDatabase();db.beginTransaction();try{
            db.execSQL("INSERT INTO finance_shifts VALUES(?,?,?,?)",
                new Object[]{id,label.trim().isEmpty()?"Зміна":label.trim(),now,h});
            if(inCash>0)db.execSQL("INSERT INTO entries(id,account,amount,category,note,happened,kind,group_id) VALUES(?,?,?,?,?,?,?,?)",
                new Object[]{UUID.randomUUID().toString(),cashAccount,inCash,"чай","Чайові · готівка",now,"income",id});
            if(inCard>0)db.execSQL("INSERT INTO entries(id,account,amount,category,note,happened,kind,group_id) VALUES(?,?,?,?,?,?,?,?)",
                new Object[]{UUID.randomUUID().toString(),cardAccount,inCard,"чай","Чайові · картка",now,"income",id});
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public static String report(FinanceLedger ledger){
        StringBuilder b=new StringBuilder("Підсумки внесених змін (лише позначені чайові):\n");
        boolean any=false;
        try(Cursor c=ledger.getReadableDatabase().rawQuery(
            "SELECT s.label,s.captured,s.hours,COALESCE(SUM(CASE WHEN e.deleted=0 AND e.kind='income' THEN e.amount ELSE 0 END),0) "+
            "FROM finance_shifts s LEFT JOIN entries e ON e.group_id=s.id GROUP BY s.id ORDER BY s.captured DESC LIMIT 25",null)){
            while(c.moveToNext()){
                any=true;long tips=c.getLong(3);
                b.append(c.getString(0)).append(" · ").append(android.text.format.DateFormat.format("dd.MM.yyyy",c.getLong(1)))
                    .append(": ").append(FinanceLedger.money(tips)).append(" ₴");
                if(!c.isNull(2)&&tips>0){
                    BigDecimal perHour=BigDecimal.valueOf(tips,2)
                        .divide(new BigDecimal(c.getString(2)),2,RoundingMode.HALF_UP);
                    b.append(" · ").append(perHour.toPlainString()).append(" ₴/год");
                }
                b.append("\n");
            }
        }
        return any?b.toString():"Змін із розділеними чайовими ще немає.";
    }
}
