package com.andriy.jarvis;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Money Tracker bearer token; Pixel only, encrypted at rest, never backed up. */
public final class FinanceApiSecrets {
    private static final String ALIAS="jarvis_money_tracker_api_v1";
    private static final String FILENAME="money_tracker_api.enc";
    private FinanceApiSecrets(){}

    public static boolean valid(String raw) {
        if(raw==null)return false;
        String token=raw.trim();
        return token.length()>=45 && token.length()<=8192 &&
            token.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");
    }
    private static File file(Context c) {
        return new File(c.getNoBackupFilesDir(),FILENAME);
    }
    private static SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        SecretKey existing=(SecretKey)store.getKey(ALIAS,null);
        if(existing!=null)return existing;
        KeyGenerator gen=KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(ALIAS,
            KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build());
        return gen.generateKey();
    }
    /** null on success; no token is included in errors. */
    public static synchronized String save(Context c,String provided) {
        if(!valid(provided))
            return "Встав повний новий JWT-токен із Менеджера фінансів.";
        try {
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,key());
            byte[] nonce=cipher.getIV();
            byte[] encrypted=cipher.doFinal(
                provided.trim().getBytes(StandardCharsets.UTF_8));
            File tmp=new File(c.getNoBackupFilesDir(),FILENAME+".tmp");
            try(FileOutputStream out=new FileOutputStream(tmp)) {
                out.write(nonce.length);
                out.write(nonce);
                out.write(encrypted);
                out.flush();
                out.getFD().sync();
            }
            if(!tmp.renameTo(file(c))) {
                tmp.delete();
                return "Не вдалося зберегти токен у захищеному сховищі.";
            }
            return null;
        }catch(Exception ignored){
            return "Android Keystore не зміг захистити токен API.";
        }
    }
    /** Accessed by Pixel only; never send returned value to Watch7. */
    public static synchronized String load(Context c) {
        try {
            File f=file(c);
            if(!f.isFile()||f.length()<42||f.length()>8500)return null;
            byte[] bytes=Files.readAllBytes(f.toPath());
            int n=bytes[0]&0xff;
            if(n!=12 || bytes.length<=1+n+16)return null;
            byte[] nonce=new byte[n];
            System.arraycopy(bytes,1,nonce,0,n);
            Cipher decipher=Cipher.getInstance("AES/GCM/NoPadding");
            decipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,nonce));
            String result=new String(decipher.doFinal(bytes,1+n,
                bytes.length-1-n),StandardCharsets.UTF_8);
            return valid(result)?result:null;
        }catch(Exception ignored){return null;}
    }
    public static synchronized boolean configured(Context c){
        return load(c)!=null;
    }
    public static synchronized void clear(Context c){file(c).delete();}
}
