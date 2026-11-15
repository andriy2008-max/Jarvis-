package com.andriy.jarvis;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.util.Arrays;

/** Portable password-encrypted snapshots. Authentication precedes any database mutation. */
public final class FinanceBackup {
    private static final String[] TABLES={"accounts","entries","plans","audit"};
    private FinanceBackup(){}
    private static Cipher cipher(int mode,char[] password,byte[] salt,byte[] iv)throws Exception{
        if(password.length<10)throw new IllegalArgumentException("Пароль: щонайменше 10 символів");
        PBEKeySpec spec=new PBEKeySpec(password,salt,210000,256);
        byte[] key;
        try{key=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}finally{spec.clearPassword();}
        try{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(mode,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));return cipher;}finally{Arrays.fill(key,(byte)0);}
    }
    public static byte[] export(FinanceLedger ledger,char[] password)throws Exception{
        JSONObject root=new JSONObject();root.put("version",2);SQLiteDatabase db=ledger.getReadableDatabase();
        db.beginTransaction();try{
            for(String table:TABLES){JSONArray rows=new JSONArray();try(Cursor c=db.rawQuery("SELECT * FROM "+table,null)){
                while(c.moveToNext()){JSONObject row=new JSONObject();for(int i=0;i<c.getColumnCount();i++)row.put(c.getColumnName(i),c.isNull(i)?JSONObject.NULL:c.getType(i)==Cursor.FIELD_TYPE_INTEGER?c.getLong(i):c.getString(i));rows.put(row);}
            }root.put(table,rows);}db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        byte[] salt=new byte[16],iv=new byte[12];SecureRandom r=new SecureRandom();r.nextBytes(salt);r.nextBytes(iv);
        byte[] payload=cipher(Cipher.ENCRYPT_MODE,password,salt,iv).doFinal(root.toString().getBytes(StandardCharsets.UTF_8));
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();out.write(new byte[]{'J','F','B',2});out.write(salt);out.write(iv);out.write(payload);return out.toByteArray();
    }
    public static void restore(FinanceLedger ledger,char[] password,byte[] bytes)throws Exception{
        if(bytes.length<48||bytes.length>20*1024*1024||bytes[0]!='J'||bytes[1]!='F'||bytes[2]!='B'||bytes[3]!=2)throw new IllegalArgumentException("Непідтримувана резервна копія");
        JSONObject root=new JSONObject(new String(cipher(Cipher.DECRYPT_MODE,password,Arrays.copyOfRange(bytes,4,20),Arrays.copyOfRange(bytes,20,32)).doFinal(Arrays.copyOfRange(bytes,32,bytes.length)),StandardCharsets.UTF_8));
        if(root.getInt("version")!=2)throw new IllegalArgumentException("Версія копії не підтримується");
        for(String table:TABLES)root.getJSONArray(table);
        SQLiteDatabase db=ledger.getWritableDatabase();db.beginTransaction();try{
            db.delete("audit",null,null);db.delete("entries",null,null);db.delete("plans",null,null);db.delete("accounts",null,null);
            for(String table:TABLES){JSONArray rows=root.getJSONArray(table);for(int i=0;i<rows.length();i++){
                JSONObject row=rows.getJSONObject(i);ContentValues v=new ContentValues();java.util.Iterator<String> keys=row.keys();while(keys.hasNext()){String key=keys.next();Object value=row.get(key);if(value==JSONObject.NULL)v.putNull(key);else if(value instanceof Number)v.put(key,((Number)value).longValue());else v.put(key,value.toString());}db.insertOrThrow(table,null,v);
            }}
            try(Cursor check=db.rawQuery("PRAGMA foreign_key_check",null)){if(check.moveToFirst())throw new IllegalArgumentException("Пошкоджені зв’язки у копії");}
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
}
