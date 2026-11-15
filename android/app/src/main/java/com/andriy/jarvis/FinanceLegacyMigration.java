package com.andriy.jarvis;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import java.io.File;
import java.util.*;
import net.zetetic.database.sqlcipher.SQLiteDatabase;

/** All-or-nothing, schema-aware migration from previously unencrypted finance databases. */
public final class FinanceLegacyMigration {
    private static final String OLD="jarvis-finance.db";
    private static final String PREFS="finance_secure_migration";
    private static final String DONE="done";
    private static final String[] TABLES={
        "accounts","plans","finance_shifts","entries","allocations","settings","receipts",
        "audit","fx_transfers","crypto_positions","settlement_history","finance_aliases","finance_reconciliations"
    };
    private FinanceLegacyMigration(){}
    public static synchronized void migrate(Context c,FinanceLedger target){
        Context context=c.getApplicationContext();
        File old=context.getDatabasePath(OLD);
        if(!old.exists())return;
        android.content.SharedPreferences prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        if(prefs.getBoolean(DONE,false)){
            // A previous successful import may have crashed before removing the old file.
            if(!context.deleteDatabase(OLD)&&old.exists())
                throw new IllegalStateException("Захищена база готова, але старий файл не видалено.");
            return;
        }
        // The old file must survive every failure. An incomplete secure transaction is retried.
        android.database.sqlite.SQLiteDatabase legacy=null;
        try{
            legacy=android.database.sqlite.SQLiteDatabase.openDatabase(old.getPath(),null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY);
            SQLiteDatabase encrypted=target.getWritableDatabase();
            encrypted.beginTransaction();
            try{
                for(int n=TABLES.length-1;n>=0;n--){
                    // A previous interrupted attempt could have written data before a crash.
                    encrypted.delete(TABLES[n],null,null);
                }
                for(String table:TABLES){
                    if(!exists(legacy,table))continue;
                    Set<String> names=columns(encrypted,table);
                    try(Cursor rows=legacy.rawQuery("SELECT * FROM "+table,null)){
                        while(rows.moveToNext()){
                            ContentValues values=new ContentValues();
                            for(int k=0;k<rows.getColumnCount();k++){
                                String col=rows.getColumnName(k);
                                if(!names.contains(col))continue;
                                if(rows.isNull(k))values.putNull(col);
                                else if(rows.getType(k)==Cursor.FIELD_TYPE_INTEGER)values.put(col,rows.getLong(k));
                                else if(rows.getType(k)==Cursor.FIELD_TYPE_FLOAT)values.put(col,rows.getDouble(k));
                                else if(rows.getType(k)==Cursor.FIELD_TYPE_BLOB)values.put(col,rows.getBlob(k));
                                else values.put(col,rows.getString(k));
                            }
                            encrypted.insertOrThrow(table,null,values);
                        }
                    }
                }
                try(Cursor check=encrypted.rawQuery("PRAGMA foreign_key_check",null)){
                    if(check.moveToFirst())throw new IllegalStateException("Старі фінансові дані містять порушені зв'язки.");
                }
                for(String table:TABLES){
                    if(!exists(legacy,table))continue;
                    long oldCount=count(legacy,table),newCount=count(encrypted,table);
                    if(oldCount!=newCount)
                        throw new IllegalStateException("Міграцію не підтверджено: різні кількості записів у "+table);
                }
                encrypted.setTransactionSuccessful();
            }finally{encrypted.endTransaction();}
            if(!prefs.edit().putBoolean(DONE,true).commit())
                throw new IllegalStateException("Не вдалося зафіксувати завершення міграції");
        }catch(Exception e){
            throw new IllegalStateException("Не вдалося безпечно зашифрувати фінансову базу. Стара база збережена.",e);
        }finally{if(legacy!=null)legacy.close();}
        // Only after successful encrypted commit and persistent completion marker.
        if(!context.deleteDatabase(OLD)&&old.exists())
            throw new IllegalStateException("Не вдалося видалити незашифровану попередню базу.");
    }
    private static boolean exists(android.database.sqlite.SQLiteDatabase db,String table){
        try(Cursor c=db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",new String[]{table})){
            return c.moveToFirst();
        }
    }
    private static Set<String> columns(SQLiteDatabase db,String table){
        Set<String> result=new HashSet<>();
        try(Cursor c=db.rawQuery("PRAGMA table_info("+table+")",null)){
            while(c.moveToNext())result.add(c.getString(1));
        }
        return result;
    }
    private static long count(android.database.sqlite.SQLiteDatabase db,String table){
        try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM "+table,null)){c.moveToFirst();return c.getLong(0);}
    }
    private static long count(SQLiteDatabase db,String table){
        try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM "+table,null)){c.moveToFirst();return c.getLong(0);}
    }
}
