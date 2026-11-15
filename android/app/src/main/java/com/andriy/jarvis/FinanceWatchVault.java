package com.andriy.jarvis;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Keystore-encrypted persisted Watch7 finance utterances, never plaintext in new prefs. */
public final class FinanceWatchVault {
    private static final String ALIAS="jarvis.watch.finance.queue.v1";
    private static final String PREFIX="enc:";
    private FinanceWatchVault(){}
    private static SecretKey key()throws Exception{
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(ks.containsAlias(ALIAS))return (SecretKey)ks.getKey(ALIAS,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    public static String protect(String utterance){
        if(utterance==null)return "";
        try{
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE,key());
            byte[] plain=utterance.getBytes(StandardCharsets.UTF_8);
            byte[] encrypted;
            try{encrypted=c.doFinal(plain);}finally{Arrays.fill(plain,(byte)0);}
            byte[] packed=new byte[12+encrypted.length];
            System.arraycopy(c.getIV(),0,packed,0,12);
            System.arraycopy(encrypted,0,packed,12,encrypted.length);
            String result=PREFIX+Base64.encodeToString(packed,Base64.NO_WRAP);
            Arrays.fill(encrypted,(byte)0);Arrays.fill(packed,(byte)0);
            return result;
        }catch(Exception e){throw new IllegalStateException("Не вдалося захистити фінансову команду",e);}
    }
    public static String reveal(String wrapped){
        if(wrapped==null)return "";
        if(!wrapped.startsWith(PREFIX))return wrapped; // migrate old, never delete it silently
        try{
            byte[] packed=Base64.decode(wrapped.substring(PREFIX.length()),Base64.NO_WRAP);
            if(packed.length<29)throw new IllegalArgumentException("Пошкоджена зашифрована команда");
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Arrays.copyOfRange(packed,0,12)));
            byte[] plain=c.doFinal(Arrays.copyOfRange(packed,12,packed.length));
            String result=new String(plain,StandardCharsets.UTF_8);
            Arrays.fill(plain,(byte)0);Arrays.fill(packed,(byte)0);
            return result;
        }catch(Exception e){throw new IllegalStateException("Не вдалося прочитати чергу фінансів; не стирай її",e);}
    }
}
