package com.andriy.jarvis;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.math.BigDecimal;
import java.util.UUID;

/** Local ledger. Amounts are integer minor units; transfers commit atomically. */
public final class FinanceLedger extends SQLiteOpenHelper {
    public FinanceLedger(Context c){super(c.getApplicationContext(),"jarvis-finance.db",null,4);}
    public void onConfigure(SQLiteDatabase db){db.setForeignKeyConstraintsEnabled(true);}
    public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE accounts(id INTEGER PRIMARY KEY, name TEXT UNIQUE NOT NULL, currency TEXT NOT NULL, opening INTEGER, opening_at INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE entries(id TEXT PRIMARY KEY, account INTEGER NOT NULL REFERENCES accounts(id), amount INTEGER NOT NULL, category TEXT NOT NULL, note TEXT NOT NULL, happened INTEGER NOT NULL, kind TEXT NOT NULL, group_id TEXT, deleted INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX entries_date ON entries(happened)");
        createPlanning(db);
        createSettlements(db);
    }
    private void createPlanning(SQLiteDatabase db){
        db.execSQL("CREATE TABLE plans(id TEXT PRIMARY KEY, type TEXT NOT NULL, name TEXT NOT NULL, currency TEXT NOT NULL, amount INTEGER NOT NULL CHECK(amount>0), progress INTEGER NOT NULL DEFAULT 0 CHECK(progress>=0), due TEXT NOT NULL, category TEXT NOT NULL)");
        db.execSQL("CREATE TABLE audit(id INTEGER PRIMARY KEY, entry_id TEXT NOT NULL, event TEXT NOT NULL, happened INTEGER NOT NULL, previous_amount INTEGER)");
    }
    public void onUpgrade(SQLiteDatabase db,int old,int next){
        if(old<2)createPlanning(db);
        if(old<3)createSettlements(db);
        if(old<4)db.execSQL("ALTER TABLE accounts ADD COLUMN opening_at INTEGER NOT NULL DEFAULT 0");
    }
    private void createSettlements(SQLiteDatabase db){
        db.execSQL("CREATE TABLE allocations(id TEXT PRIMARY KEY, plan TEXT NOT NULL REFERENCES plans(id), account INTEGER NOT NULL REFERENCES accounts(id), amount INTEGER NOT NULL CHECK(amount>0))");
        db.execSQL("CREATE TABLE settings(name TEXT PRIMARY KEY,value TEXT NOT NULL)");
        db.execSQL("CREATE TABLE receipts(id TEXT PRIMARY KEY, utterance TEXT NOT NULL, reply TEXT NOT NULL)");
        db.execSQL("ALTER TABLE plans ADD COLUMN interval_months INTEGER NOT NULL DEFAULT 0");
    }
    public long balance(long account){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT a.opening+COALESCE(SUM(CASE WHEN e.deleted=0 AND (a.opening_at=0 OR e.happened>a.opening_at) THEN e.amount ELSE 0 END),0) FROM accounts a LEFT JOIN entries e ON a.id=e.account WHERE a.id=? GROUP BY a.id",new String[]{""+account})){
            if(!c.moveToFirst()||c.isNull(0))throw new IllegalArgumentException("Спочатку звір залишок рахунку");return c.getLong(0);
        }
    }
    public long available(long account){
        long total=balance(account);try(Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(amount),0) FROM allocations WHERE account=?",new String[]{""+account})){c.moveToFirst();return Math.subtractExact(total,c.getLong(0));}
    }
    public void settle(String id,long account,String value){
        long amount=minor(value);if(amount<=0)throw new IllegalArgumentException("Вкажи додатну суму");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            try(Cursor c=db.rawQuery("SELECT type,name,currency,amount,progress,due,category,interval_months FROM plans WHERE id=?",new String[]{id})){
                if(!c.moveToFirst())throw new IllegalArgumentException("План не знайдено");
                String type=c.getString(0);if(type.equals("budget"))throw new IllegalArgumentException("Бюджет не потребує внесків");
                if(!currency(account).equals(c.getString(2)))throw new IllegalArgumentException("Валюти рахунку та плану мають збігатися");
                long left=Math.subtractExact(c.getLong(3),c.getLong(4));if(amount>left)throw new IllegalArgumentException("Сума перевищує залишок плану");
                if(type.equals("goal")){
                    if(amount>available(account))throw new IllegalArgumentException("Недостатньо незарезервованих коштів");
                    db.execSQL("INSERT INTO allocations VALUES(?,?,?,?)",new Object[]{UUID.randomUUID().toString(),id,account,amount});
                }else{
                    String kind=type.equals("payment")?"expense":"debt";
                    insert(db,UUID.randomUUID().toString(),account,type.equals("owed")?amount:-amount,kind,c.getString(6),c.getString(1),System.currentTimeMillis(),"plan:"+id);
                }
                long progress=Math.addExact(c.getLong(4),amount);
                if(type.equals("payment")&&progress==c.getLong(3)&&c.getInt(7)>0&&!c.getString(5).isEmpty()){
                    db.execSQL("UPDATE plans SET progress=0,due=? WHERE id=?",new Object[]{java.time.LocalDate.parse(c.getString(5)).plusMonths(c.getInt(7)).toString(),id});
                }else db.execSQL("UPDATE plans SET progress=? WHERE id=?",new Object[]{progress,id});
            }db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public void recurrence(String id,int months){
        if(months<0||months>120)throw new IllegalArgumentException("Інтервал: 0–120 місяців");
        getWritableDatabase().execSQL("UPDATE plans SET interval_months=? WHERE id=? AND type='payment'",new Object[]{months,id});
    }
    public void deletePlan(String id){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            db.delete("allocations","plan=?",new String[]{id});db.delete("plans","id=?",new String[]{id});db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public void setSetting(String key,String value){ContentValues v=new ContentValues();v.put("name",key);v.put("value",value);getWritableDatabase().insertWithOnConflict("settings",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    public String setting(String key,String fallback){try(Cursor c=getReadableDatabase().rawQuery("SELECT value FROM settings WHERE name=?",new String[]{key})){return c.moveToFirst()?c.getString(0):fallback;}}
    public String receipt(String id,String utterance){try(Cursor c=getReadableDatabase().rawQuery("SELECT utterance,reply FROM receipts WHERE id=?",new String[]{id})){if(!c.moveToFirst())return null;if(!c.getString(0).equals(utterance))throw new IllegalArgumentException("Конфлікт ідентифікатора команди");return c.getString(1);}}
    public void receipt(String id,String utterance,String reply){getWritableDatabase().execSQL("INSERT INTO receipts VALUES(?,?,?)",new Object[]{id,utterance,reply});}
    public static long minor(String s){
        try{return new BigDecimal(s.trim().replace(" ","").replace(',','.')).movePointRight(2).longValueExact();}
        catch(RuntimeException e){throw new IllegalArgumentException("Вкажи точну суму, максимум дві цифри після коми");}
    }
    public static String money(long n){return BigDecimal.valueOf(n,2).toPlainString();}
    public long account(String name,String currency,String opening){
        if(name.trim().isEmpty()||!currency.matches("[A-Z]{3}"))throw new IllegalArgumentException("Вкажи назву та код валюти");
        ContentValues v=new ContentValues();v.put("name",name.trim());v.put("currency",currency);
        if(!opening.trim().isEmpty()){
            v.put("opening",minor(opening));
            v.put("opening_at",System.currentTimeMillis());
        }
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
    /** Refund against an existing expense; does not count as earned income. */
    public void refund(String expenseId,String value){
        long amount=minor(value);
        if(amount<=0)throw new IllegalArgumentException("Сума повернення має бути додатною");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            try(Cursor c=db.rawQuery("SELECT account,amount,category FROM entries WHERE id=? AND kind='expense' AND deleted=0",new String[]{expenseId})){
                if(!c.moveToFirst())throw new IllegalArgumentException("Початкову витрату не знайдено");
                long original=Math.negateExact(c.getLong(1));
                String reference="Повернення:"+expenseId;
                long refunded;
                try(Cursor sums=db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM entries WHERE kind='refund' AND note=? AND deleted=0",new String[]{reference})){
                    sums.moveToFirst();refunded=sums.getLong(0);
                }
                if(amount>Math.subtractExact(original,refunded))throw new IllegalArgumentException("Сума перевищує залишок покупки");
                insert(db,UUID.randomUUID().toString(),c.getLong(0),amount,"refund",c.getString(2),reference,System.currentTimeMillis(),null);
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
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
        try(Cursor c=getReadableDatabase().rawQuery("SELECT group_id FROM entries WHERE id=?",new String[]{id})){
            if(c.moveToFirst()&&c.getString(0)!=null&&c.getString(0).startsWith("plan:"))throw new IllegalArgumentException("Це погашення плану. Видалення потребує звірки прогресу.");
        }
        try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM entries WHERE kind='refund' AND deleted=0 AND note=? LIMIT 1",new String[]{"Повернення:"+id})){
            if(c.moveToFirst())throw new IllegalArgumentException("Спочатку скасуй пов’язані повернення покупки");
        }
        getWritableDatabase().execSQL("UPDATE entries SET deleted=1 WHERE id=? OR group_id=(SELECT group_id FROM entries WHERE id=?)",new Object[]{id,id});
    }
    public void plan(String type,String name,String currency,String amount,String due,String category){
        if(!java.util.Arrays.asList("budget","goal","payment","owed","owing").contains(type)||name.trim().isEmpty()||!currency.matches("[A-Z]{3}"))throw new IllegalArgumentException("Перевір назву й валюту");
        long value=minor(amount);if(value<=0)throw new IllegalArgumentException("Сума має бути додатною");
        if(!due.isEmpty())try{java.time.LocalDate.parse(due);}catch(RuntimeException e){throw new IllegalArgumentException("Дата: РРРР-ММ-ДД");}
        ContentValues v=new ContentValues();v.put("id",UUID.randomUUID().toString());v.put("type",type);v.put("name",name.trim());v.put("currency",currency);v.put("amount",value);v.put("due",due);v.put("category",category);
        getWritableDatabase().insertOrThrow("plans",null,v);
    }
    /** Editing a plan cannot invalidate any confirmed settlements or goal reservations. */
    public void editPlan(String id,String name,String amount,String due,String category){
        if(name.trim().isEmpty())throw new IllegalArgumentException("Вкажи назву плану");
        long target=minor(amount);if(target<=0)throw new IllegalArgumentException("Сума має бути додатною");
        if(!due.trim().isEmpty())try{java.time.LocalDate.parse(due.trim());}
        catch(RuntimeException e){throw new IllegalArgumentException("Дата: РРРР-ММ-ДД");}
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            try(Cursor c=db.rawQuery("SELECT progress FROM plans WHERE id=?",new String[]{id})){
                if(!c.moveToFirst())throw new IllegalArgumentException("План не знайдено");
                if(target<c.getLong(0))throw new IllegalArgumentException("Нова сума не може бути меншою за вже виконану");
                db.execSQL("UPDATE plans SET name=?,amount=?,due=?,category=? WHERE id=?",
                    new Object[]{name.trim(),target,due.trim(),category.trim(),id});
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public void edit(String id,String amount,String category){
        long value=minor(amount);if(value<=0||category.trim().isEmpty())throw new IllegalArgumentException("Перевір суму й категорію");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            try(Cursor c=db.rawQuery("SELECT amount,kind,group_id FROM entries WHERE id=? AND deleted=0",new String[]{id})){
                if(!c.moveToFirst()||(c.getString(2)!=null&&c.getString(2).startsWith("plan:"))||!java.util.Arrays.asList("expense","income").contains(c.getString(1)))throw new IllegalArgumentException("Переказ редагується через скасування обох сторін і новий запис");
                if(c.getString(1).equals("expense")){
                    try(Cursor refunds=db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM entries WHERE kind='refund' AND deleted=0 AND note=?",new String[]{"Повернення:"+id})){
                        refunds.moveToFirst();
                        if(value<refunds.getLong(0))throw new IllegalArgumentException("Нова сума менша за вже оформлені повернення");
                    }
                }
                db.execSQL("INSERT INTO audit(entry_id,event,happened,previous_amount) VALUES(?,?,?,?)",new Object[]{id,"edit",System.currentTimeMillis(),c.getLong(0)});
                db.execSQL("UPDATE entries SET amount=?,category=? WHERE id=?",new Object[]{c.getLong(0)<0?-value:value,category.trim(),id});
                db.execSQL("UPDATE entries SET category=? WHERE kind='refund' AND note=?",new Object[]{category.trim(),"Повернення:"+id});
            }db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public void restore(String id){getWritableDatabase().execSQL("UPDATE entries SET deleted=0 WHERE id=? OR group_id=(SELECT group_id FROM entries WHERE id=?)",new Object[]{id,id});}
    public String csv(){
        StringBuilder b=new StringBuilder("id,date,type,amount,currency,account,category,note,deleted\n");
        try(Cursor c=getReadableDatabase().rawQuery("SELECT e.id,e.happened,e.kind,e.amount,a.currency,a.name,e.category,e.note,e.deleted FROM entries e JOIN accounts a ON a.id=e.account ORDER BY e.happened",null)){
            while(c.moveToNext()){for(int i=0;i<9;i++){if(i>0)b.append(',');String v=i==3?money(c.getLong(i)):c.getString(i);if(i==5||i==6||i==7){if(v.matches("^[=+@\\-].*"))v="'"+v;}b.append('"').append(v.replace("\"","\"\"")).append('"');}b.append('\n');}
        }return b.toString();
    }
    public String monthlyReport(){
        long start=java.time.LocalDate.now().withDayOfMonth(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        StringBuilder b=new StringBuilder("Поточний місяць\n");
        try(Cursor c=getReadableDatabase().rawQuery("SELECT a.currency,e.kind,e.category,SUM(e.amount) FROM entries e JOIN accounts a ON a.id=e.account WHERE e.deleted=0 AND e.kind IN ('expense','income','refund') AND e.happened>=? AND e.happened<=? GROUP BY a.currency,e.kind,e.category ORDER BY ABS(SUM(e.amount)) DESC",new String[]{""+start,""+System.currentTimeMillis()})){
            while(c.moveToNext())b.append(c.getString(2)).append(" · ").append(money(c.getLong(3))).append(" ").append(c.getString(0)).append("\n");
        }return b.toString();
    }
    public String report(){
        StringBuilder out=new StringBuilder();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT a.name,a.currency,a.opening,COALESCE(SUM(CASE WHEN e.deleted=0 AND (a.opening_at=0 OR e.happened>a.opening_at) THEN e.amount ELSE 0 END),0) FROM accounts a LEFT JOIN entries e ON e.account=a.id GROUP BY a.id",null)){
            while(c.moveToNext())out.append(c.getString(0)).append(": ").append(c.isNull(2)?"залишок невідомий":money(Math.addExact(c.getLong(2),c.getLong(3)))+" "+c.getString(1)).append("\n");
        }
        try(Cursor c=getReadableDatabase().rawQuery("SELECT a.currency,e.kind,SUM(e.amount) FROM entries e JOIN accounts a ON a.id=e.account WHERE e.deleted=0 AND e.kind IN ('expense','income','refund') GROUP BY a.currency,e.kind",null)){
            while(c.moveToNext())out.append(c.getString(1).equals("income")?"Доходи: ":c.getString(1).equals("refund")?"Повернення: ":"Витрати: ").append(money(c.getLong(2))).append(" ").append(c.getString(0)).append("\n");
        }
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,name,currency FROM accounts",null)){
            while(c.moveToNext()){
                try{
                    long reserved=Math.subtractExact(balance(c.getLong(0)),available(c.getLong(0)));
                    if(reserved>0)out.append(c.getString(1)).append(" · на цілі: ").append(money(reserved))
                        .append(" · доступно: ").append(money(available(c.getLong(0))))
                        .append(" ").append(c.getString(2)).append("\n");
                }catch(IllegalArgumentException ignored){/* opening balance unknown */}
            }
        }
        return out.length()==0?"Додай перший рахунок. Невідомий залишок можна пропустити.":out.toString();
    }
}
