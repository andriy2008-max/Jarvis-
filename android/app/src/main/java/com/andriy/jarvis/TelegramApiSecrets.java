package com.andriy.jarvis;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * App-private user-provided Telegram API credentials and local TDLib DB key.
 * Never embedded in GitHub, BuildConfig, Java constants, backups, or logs.
 * AES/GCM wrapping key is non-exportable Android Keystore material.
 */
public final class TelegramApiSecrets {
    private static final String KEY_ALIAS="jarvis_telegram_api_v1";
    private static final String FILE_NAME="telegram_credentials.enc";
    private TelegramApiSecrets(){}

    public static final class Credentials {
        public final int apiId;
        public final String apiHash;
        public final byte[] dbKey;
        private Credentials(int id,String hash,byte[] key){
            apiId=id;apiHash=hash;dbKey=key;
        }
    }

    public static boolean validId(String raw){
        if(raw==null || !raw.trim().matches("[0-9]{5,12}"))return false;
        try {return Long.parseLong(raw.trim())>0 &&
            Long.parseLong(raw.trim())<=Integer.MAX_VALUE;}
        catch(NumberFormatException ignored){return false;}
    }
    public static boolean validHash(String raw){
        return raw!=null && raw.trim().matches("[a-fA-F0-9]{32}");
    }

    private static SecretKey secret() throws Exception{
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        SecretKey existing=(SecretKey)ks.getKey(KEY_ALIAS,null);
        if(existing!=null)return existing;
        KeyGenerator generator=KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }
    private static File target(Context c){
        return new File(c.getNoBackupFilesDir(),FILE_NAME);
    }

    /** Save on the device only; changing credentials requires app restart. */
    public static synchronized String save(Context c,String id,String hash){
        if(!validId(id)||!validHash(hash))
            return "Telegram api_id має бути додатним числом, а api_hash — 32 hex-символи.";
        try{
            Credentials current=load(c);
            byte[] dbKey=current!=null?current.dbKey:null;
            if(dbKey==null||dbKey.length!=32){
                dbKey=new byte[32];
                new SecureRandom().nextBytes(dbKey);
            }
            JSONObject object=new JSONObject();
            object.put("id",Integer.parseInt(id.trim()));
            object.put("hash",hash.trim());
            object.put("dbkey",Base64.encodeToString(dbKey,Base64.NO_WRAP));
            byte[] clear=object.toString().getBytes(StandardCharsets.UTF_8);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,secret());
            byte[] iv=cipher.getIV();
            byte[] encrypted=cipher.doFinal(clear);
            File tmp=new File(c.getNoBackupFilesDir(),FILE_NAME+".tmp");
            try(FileOutputStream f=new FileOutputStream(tmp)){
                f.write(iv.length);
                f.write(iv);
                f.write(encrypted);
                f.flush();
                f.getFD().sync();
            }
            File dest=target(c);
            if(!tmp.renameTo(dest)) {
                tmp.delete();
                return "Не вдалося зберегти Telegram API у сховищі телефона.";
            }
            return null;
        }catch(Exception ignored){
            return "Не вдалося захистити Telegram API через Android Keystore.";
        }
    }
    public static synchronized Credentials load(Context c){
        try{
            File file=target(c);
            if(!file.isFile()||file.length()>4096 || file.length()<34)return null;
            byte[] bytes=Files.readAllBytes(file.toPath());
            int size=bytes[0]&0xff;
            if(size!=12||bytes.length<size+17)return null;
            byte[] iv=new byte[size];
            System.arraycopy(bytes,1,iv,0,size);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,secret(),new GCMParameterSpec(128,iv));
            byte[] clear=cipher.doFinal(bytes,1+size,bytes.length-1-size);
            JSONObject data=new JSONObject(new String(clear,StandardCharsets.UTF_8));
            int id=data.optInt("id");
            String hash=data.optString("hash","");
            byte[] key=Base64.decode(data.optString("dbkey",""),Base64.DEFAULT);
            return validId(Integer.toString(id)) && validHash(hash) &&
                key.length==32 ? new Credentials(id,hash,key) : null;
        }catch(Exception ignored){return null;}
    }
    /** Deliberate logout may delete credentials; never silently restore. */
    public static synchronized void clear(Context context){
        target(context).delete();
    }
}
