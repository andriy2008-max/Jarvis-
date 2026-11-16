package com.andriy.jarvis;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Opt-in, strictly localhost Mobilerun Portal companion.
 * The bearer token is encrypted with a non-exportable Android Keystore key.
 * The setting is never synchronized to Wear OS or uploaded to the AI.
 */
final class PortalUiSettings {
    static final String PACKAGE="com.mobilerun.portal";
    static final String HOST="http://127.0.0.1:8080";
    private static final String PREFS="jarvis_portal_bridge_v1";
    private static final String KEY_NAME="jarvis_portal_http_token_v1";
    private static final String TOKEN="token_encrypted";
    private static final String ENABLED="enabled";
    private PortalUiSettings(){}

    static boolean enabled(Context context) {
        return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .getBoolean(ENABLED,false) && !token(context).isEmpty();
    }

    static boolean valid(String token) {
        // Do not allow HTTP header injection or secrets accidentally pasted as URLs.
        return token!=null && token.length()>=16 && token.length()<=512 &&
            token.matches("[A-Za-z0-9_\\-]+");
    }

    private static SecretKey key() throws Exception {
        KeyStore keys=KeyStore.getInstance("AndroidKeyStore");
        keys.load(null);
        java.security.Key saved=keys.getKey(KEY_NAME,null);
        if(saved instanceof SecretKey)return (SecretKey)saved;
        KeyGenerator generator=KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_NAME,
            KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build());
        return generator.generateKey();
    }

    static boolean save(Context context,String value,boolean enabled) {
        String token=value==null?"":value.trim();
        if(!token.isEmpty()&&!valid(token))return false;
        SharedPreferences prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        if(token.isEmpty())return prefs.edit().remove(TOKEN).putBoolean(ENABLED,false).commit();
        try {
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,key());
            byte[] plain=token.getBytes(StandardCharsets.UTF_8);
            byte[] encrypted=cipher.doFinal(plain);
            byte[] iv=cipher.getIV();
            byte[] both=new byte[iv.length+encrypted.length];
            System.arraycopy(iv,0,both,0,iv.length);
            System.arraycopy(encrypted,0,both,iv.length,encrypted.length);
            return prefs.edit().putString(TOKEN,Base64.encodeToString(both,
                    Base64.NO_WRAP)).putBoolean(ENABLED,enabled).commit();
        } catch(Exception error){return false;}
    }

    static String token(Context context) {
        String stored=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .getString(TOKEN,"");
        if(stored.isEmpty())return "";
        try {
            byte[] both=Base64.decode(stored,Base64.NO_WRAP);
            if(both.length<29)return "";
            byte[] iv=new byte[12];
            System.arraycopy(both,0,iv,0,12);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));
            String result=new String(cipher.doFinal(both,12,both.length-12),
                StandardCharsets.UTF_8);
            return valid(result)?result:"";
        } catch(Exception error){return "";}
    }
}
