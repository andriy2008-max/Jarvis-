package com.andriy.jarvis;

import android.content.ContentValues;
import android.database.Cursor;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.util.Arrays;

/** Portable password-encrypted snapshots. Authentication precedes any database mutation. */
public final class FinanceBackup {
    private static final String[] TABLES={"accounts","entries","plans","audit","allocations","settings","receipts","fx_transfers","crypto_positions","settlement_history","finance_shifts","finance_aliases","crypto_trades"};
    private FinanceBackup(){}
    private static Cipher cipher(int mode,char[] password,byte[] salt,byte[] iv)throws Exception{
        if(password.length<10)throw new IllegalArgumentException("Пароль: щонайменше 10 символів");
        PBEKeySpec spec=new PBEKeySpec(password,salt,210000,256);
        byte[] key;
        try{key=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}finally{spec.clearPassword();}
        try{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(mode,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));return cipher;}finally{Arrays.fill(key,(byte)0);}
    }
    public static byte[] export(FinanceLedger ledger,char[] password)throws Exception{
        JSONObject root=new JSONObject();root.put("version",6);SQLiteDatabase db=ledger.getReadableDatabase();
        db.beginTransaction();try{
            for(String table:TABLES){JSONArray rows=new JSONArray();try(Cursor c=db.rawQuery("SELECT * FROM "+table,null)){
                while(c.moveToNext()){JSONObject row=new JSONObject();for(int i=0;i<c.getColumnCount();i++)row.put(c.getColumnName(i),c.isNull(i)?JSONObject.NULL:c.getType(i)==Cursor.FIELD_TYPE_INTEGER?c.getLong(i):c.getString(i));rows.put(row);}
            }root.put(table,rows);}db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        byte[] salt=new byte[16],iv=new byte[12];SecureRandom r=new SecureRandom();r.nextBytes(salt);r.nextBytes(iv);
        byte[] payload=cipher(Cipher.ENCRYPT_MODE,password,salt,iv).doFinal(root.toString().getBytes(StandardCharsets.UTF_8));
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();out.write(new byte[]{'J','F','B',6});out.write(salt);out.write(iv);out.write(payload);return out.toByteArray();
    }
    /** Authenticate and validate all required tables before any snapshot is replaced. */
    public static void validate(char[] password,byte[] bytes)throws Exception{
        parseAndCheck(password,bytes);
    }
    private static JSONObject parseAndCheck(char[] password,byte[] bytes)throws Exception{
        if(bytes==null||bytes.length<48||bytes.length>20*1024*1024||
           bytes[0]!='J'||bytes[1]!='F'||bytes[2]!='B'||
           (bytes[3]!=2&&bytes[3]!=3&&bytes[3]!=4&&bytes[3]!=5&&bytes[3]!=6))
            throw new IllegalArgumentException("Непідтримувана резервна копія");
        byte[] decoded=cipher(Cipher.DECRYPT_MODE,password,Arrays.copyOfRange(bytes,4,20),
            Arrays.copyOfRange(bytes,20,32)).doFinal(Arrays.copyOfRange(bytes,32,bytes.length));
        JSONObject root;
        try{root=new JSONObject(new String(decoded,StandardCharsets.UTF_8));}
        finally{Arrays.fill(decoded,(byte)0);}
        int version=root.getInt("version");
        if(version!=2&&version!=3&&version!=4&&version!=5&&version!=6)
            throw new IllegalArgumentException("Версія копії не підтримується");
        // Reject mismatched headers instead of accepting a misleading container version.
        if(version!=bytes[3])throw new IllegalArgumentException("Заголовок копії не збігається з даними");
        if(version==2){
            root.put("allocations",new JSONArray());
            root.put("settings",new JSONArray());
            root.put("receipts",new JSONArray());
        }
        if(version<5){
            root.put("fx_transfers",new JSONArray());
            root.put("crypto_positions",new JSONArray());
            root.put("settlement_history",new JSONArray());
        }
        if(!root.has("finance_shifts"))root.put("finance_shifts",new JSONArray());
        if(!root.has("finance_aliases"))root.put("finance_aliases",new JSONArray());
        if(version<6)root.put("crypto_trades",new JSONArray());
        for(String table:TABLES)root.getJSONArray(table);
        return root;
    }
    public static void restore(FinanceLedger ledger,char[] password,byte[] bytes)throws Exception{
        JSONObject root=parseAndCheck(password,bytes);
        SQLiteDatabase db=ledger.getWritableDatabase();db.beginTransaction();try{
            db.delete("crypto_trades",null,null);db.delete("settlement_history",null,null);db.delete("fx_transfers",null,null);db.delete("crypto_positions",null,null);db.delete("finance_shifts",null,null);db.delete("finance_aliases",null,null);
            db.delete("allocations",null,null);db.delete("settings",null,null);db.delete("receipts",null,null);db.delete("audit",null,null);db.delete("entries",null,null);db.delete("plans",null,null);db.delete("accounts",null,null);
            for(String table:TABLES){JSONArray rows=root.getJSONArray(table);for(int i=0;i<rows.length();i++){
                JSONObject row=rows.getJSONObject(i);ContentValues v=new ContentValues();java.util.Iterator<String> keys=row.keys();while(keys.hasNext()){String key=keys.next();Object value=row.get(key);if(value==JSONObject.NULL)v.putNull(key);else if(value instanceof Number)v.put(key,((Number)value).longValue());else v.put(key,value.toString());}db.insertOrThrow(table,null,v);
            }}
            try(Cursor check=db.rawQuery("PRAGMA foreign_key_check",null)){if(check.moveToFirst())throw new IllegalArgumentException("Пошкоджені зв’язки у копії");}
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
}
