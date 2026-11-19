package com.andriy.jarvis;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** AndroidKeyStore-backed per-install SQLCipher secret. Never embeds a default passphrase. */
public final class FinanceSecureKey {
    private static final String ALIAS="jarvis.finance.sqlcipher.v1";
    private static final String NAME="finance_secure_key";
    private static final String WRAPPED="wrapped";
    private static final String DB="jarvis-finance-secure.db";
    private FinanceSecureKey(){}
    private static SecretKey loadOrCreate()throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).setKeySize(256).build());
        return generator.generateKey();
    }
    /**
     * Hardware-backed when supported by the device. Private prefs hold ciphertext only.
     * After a device backup/restore loses its Keystore key, the old DB is never silently reset.
     */
    public static synchronized String password(Context c){
        Context context=c.getApplicationContext();
        SharedPreferences p=context.getSharedPreferences(NAME,Context.MODE_PRIVATE);
        String sealed=p.getString(WRAPPED,null);
        if(sealed==null && context.getDatabasePath(DB).exists())
            throw new IllegalStateException("Ключ бази втрачено. Віднови фінанси з JFB-копії.");
        byte[] data=null,secret=null;
        try{
            SecretKey key=loadOrCreate();
            if(sealed!=null){
                data=Base64.decode(sealed,Base64.NO_WRAP);
                if(data.length<13)throw new IllegalStateException("Пошкоджене сховище ключа");
                byte[] iv=Arrays.copyOfRange(data,0,12);
                Cipher aes=Cipher.getInstance("AES/GCM/NoPadding");
                aes.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,iv));
                secret=aes.doFinal(Arrays.copyOfRange(data,12,data.length));
            }else{
                secret=new byte[32];new SecureRandom().nextBytes(secret);
                Cipher aes=Cipher.getInstance("AES/GCM/NoPadding");aes.init(Cipher.ENCRYPT_MODE,key);
                byte[] iv=aes.getIV(),ciphertext=aes.doFinal(secret);
                data=new byte[iv.length+ciphertext.length];
                System.arraycopy(iv,0,data,0,iv.length);
                System.arraycopy(ciphertext,0,data,iv.length,ciphertext.length);
                if(!p.edit().putString(WRAPPED,Base64.encodeToString(data,Base64.NO_WRAP)).commit())
                    throw new IllegalStateException("Не вдалося захистити ключ фінансової бази");
            }
            return Base64.encodeToString(secret,Base64.NO_WRAP);
        }catch(Exception e){
            throw new IllegalStateException("Не вдалося розблокувати захищені фінанси. Не видаляй дані застосунку.",e);
        }finally{
            if(secret!=null)Arrays.fill(secret,(byte)0);
            if(data!=null)Arrays.fill(data,(byte)0);
        }
    }
}
