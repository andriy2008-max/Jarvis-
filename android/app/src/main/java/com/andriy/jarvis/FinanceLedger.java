package com.andriy.jarvis;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.math.BigDecimal;
import java.util.UUID;

/** Local ledger. Amounts are integer minor units; transfers commit atomically. */
public final class FinanceLedger extends SQLiteOpenHelper {
    public FinanceLedger(Context c){super(c.getApplicationContext(),"jarvis-finance.db",null,1);}
    public void onConfigure(SQLiteDatabase db){db.setForeignKeyConstraintsEnabled(true);}
    public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE accounts(id INTEGER PRIMARY KEY, name TEXT UNIQUE NOT NULL, currency TEXT NOT NULL, opening INTEGER)");
        db.execSQL("CREATE TABLE entries(id TEXT PRIMARY KEY, account INTEGER NOT NULL REFERENCES accounts(id), amount INTEGER NOT NULL, category TEXT NOT NULL, note TEXT NOT NULL, happened INTEGER NOT NULL, kind TEXT NOT NULL, group_id TEXT, deleted INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX entries_date ON entries(happened)");
    }
    public void onUpgrade(SQLiteDatabase db,int old,int next){throw new IllegalStateException("Unsupported finance migration");}
    public static long minor(String s){return new BigDecimal(s.trim().replace(',','.')).movePointRight(2).longValueExact();}
    public static String money(long n){return BigDecimal.valueOf(n,2).toPlainString();}
    public long account(String name,String currency,String opening){
        if(name.trim().isEmpty()||!currency.matches("[A-Z]{3}"))throw new IllegalArgumentException("Вкажи назву та код валюти");
        ContentValues v=new ContentValues();v.put("name",name.trim());v.put("currency",currency);
        if(!opening.trim().isEmpty())v.put("opening",minor(opening));
        return getWritableDatabase().insertOrThrow("accounts",null,v);
    }
    public void record(String id,long account,long amount,String kind,String category,String note,long date){
        if(amount<=0)throw new IllegalArgumentException("Сума повинна бути більшою за нуль");
        if(!kind.equals("expense")&&!kind.equals("income"))throw new IllegalArgumentException("Невідомий тип");
        insert(getWritableDatabase(),id,account,kind.equals("expense")?-amount:amount,kind,category,note,date,null);
    }
    private void insert(SQLiteDatabase db,String id,long account,long amount,String kind,String cat,String note,long date,String group){
        ContentValues v=new ContentValues();v.put("id",id);v.put("account",account);v.put("amount",amount);v.put("kind",kind);v.put("category",cat);v.put("note",note);v.put("happened",date);v.put("group_id",group);
        db.insertOrThrow("entries",null,v);
    }
    public void transfer(long from,long to,long amount){
        if(from==to||amount<=0)throw new IllegalArgumentException("Перевір рахунки й суму");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            String a=currency(from),b=currency(to);
            if(!a.equals(b))throw new IllegalArgumentException("Поки доступні перекази в одній валюті");
            String group=UUID.randomUUID().toString();long now=System.currentTimeMillis();
            insert(db,group+"-out",from,-amount,"transfer","Переказ","",now,group);
            insert(db,group+"-in",to,amount,"transfer","Переказ","",now,group);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public String currency(long account){try(Cursor c=getReadableDatabase().rawQuery("SELECT currency FROM accounts WHERE id=?",new String[]{""+account})){if(!c.moveToFirst())throw new IllegalArgumentException("Рахунок не знайдено");return c.getString(0);}}
    public void remove(String id){
        getWritableDatabase().execSQL("UPDATE entries SET deleted=1 WHERE id=? OR group_id=(SELECT group_id FROM entries WHERE id=?)",new Object[]{id,id});
    }
    public String report(){
        StringBuilder out=new StringBuilder();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT a.name,a.currency,a.opening,COALESCE(SUM(CASE WHEN e.deleted=0 THEN e.amount ELSE 0 END),0) FROM accounts a LEFT JOIN entries e ON e.account=a.id GROUP BY a.id",null)){
            while(c.moveToNext())out.append(c.getString(0)).append(": ").append(c.isNull(2)?"залишок невідомий":money(Math.addExact(c.getLong(2),c.getLong(3)))+" "+c.getString(1)).append("\n");
        }
        try(Cursor c=getReadableDatabase().rawQuery("SELECT a.currency,e.kind,SUM(e.amount) FROM entries e JOIN accounts a ON a.id=e.account WHERE e.deleted=0 AND e.kind!='transfer' GROUP BY a.currency,e.kind",null)){
            while(c.moveToNext())out.append(c.getString(1).equals("income")?"Доходи: ":"Витрати: ").append(money(c.getLong(2))).append(" ").append(c.getString(0)).append("\n");
        }
        return out.length()==0?"Додай перший рахунок. Невідомий залишок можна пропустити.":out.toString();
    }
}
