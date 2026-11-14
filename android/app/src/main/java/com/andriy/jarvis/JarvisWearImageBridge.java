package com.andriy.jarvis;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import java.lang.ref.WeakReference;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Phone-only image generation for a paired Watch7.
 * AI API is called on Pixel; a compressed preview travels via a Wear Asset.
 * Original bytes stay in Pixel's private cache until the owner presses Save.
 */
public final class JarvisWearImageBridge {
    public interface Callback {
        void finished(boolean ok, String detail, String imageId);
    }

    public static final String IMAGE_PATH="/jarvis/generated-image/v1/";
    private static final int MAX_BYTES=9*1024*1024;
    private static final int MAX_CACHED=5;
    private static final long TTL=30L*60L*1000L;
    private static final LinkedHashMap<String,Entry> images=new LinkedHashMap<>();
    private static AiChatClient generator;
    private static boolean generating;
    private static WeakReference<MainActivity> phoneUi=new WeakReference<>(null);
    public static final String EXTRA_WEAR_IMAGE_ID="com.andriy.jarvis.WEAR_IMAGE_ID";

    private static final class Entry {
        final String watchNode,id;
        final File file;
        long expiresAt;
        Entry(String n,String i,File f,long expiry){
            watchNode=n;id=i;file=f;expiresAt=expiry;
        }
    }

    private JarvisWearImageBridge(){}

    /** Only a foreground Pixel Activity receives the large original image UI. */
    public static synchronized void attachPhone(MainActivity activity) {
        phoneUi=new WeakReference<>(activity);
    }

    public static synchronized void detachPhone(MainActivity activity) {
        if(phoneUi.get()==activity)phoneUi=new WeakReference<>(null);
    }

    /** Last image is private to Pixel. Read from cache on a worker thread. */
    public static void readOnPixel(Context context,String imageId,
                                   java.util.function.Consumer<byte[]> callback) {
        if(callback==null)return;
        final Entry item;
        synchronized(JarvisWearImageBridge.class) {
            purgeExpired();
            item=images.get(imageId);
        }
        if(item==null) {
            callback.accept(null);
            return;
        }
        new Thread(()->{
            byte[] bytes=null;
            try {
                int length=(int)item.file.length();
                if(length>0 && length<=MAX_BYTES) {
                    bytes=new byte[length];
                    try(FileInputStream stream=new FileInputStream(item.file)){
                        int n=0;
                        while(n<length){
                            int read=stream.read(bytes,n,length-n);
                            if(read<0)throw new java.io.IOException("Invalid image");
                            n+=read;
                        }
                    }
                }
            }catch(Exception ignored){bytes=null;}
            final byte[] result=bytes;
            new android.os.Handler(android.os.Looper.getMainLooper())
                .post(()->callback.accept(result));
        },"jarvis-image-for-pixel").start();
    }

    private static void presentOnPixel(Context context,String imageId) {
        MainActivity current;
        synchronized(JarvisWearImageBridge.class){current=phoneUi.get();}
        if(current!=null && !current.isFinishing() && !current.isDestroyed()) {
            readOnPixel(context,imageId,bytes->{
                if(bytes!=null && !current.isFinishing() && !current.isDestroyed())
                    current.showGeneratedImageFromWatch(bytes);
            });
            return;
        }
        // Background Activity launches are restricted by Android. Show a
        // notification; the user explicitly opens Pixel to see the original.
        if(Build.VERSION.SDK_INT>=33 &&
           context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=
                PackageManager.PERMISSION_GRANTED)return;
        NotificationManager manager=(NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null)return;
        final String channel="jarvis_images_from_watch_v1";
        if(Build.VERSION.SDK_INT>=26) {
            NotificationChannel setup=new NotificationChannel(channel,
                "Джарвіс — зображення з годинника",
                NotificationManager.IMPORTANCE_DEFAULT);
            manager.createNotificationChannel(setup);
        }
        Intent launch=new Intent(context,MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|
            Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        launch.putExtra(EXTRA_WEAR_IMAGE_ID,imageId);
        PendingIntent action=PendingIntent.getActivity(context,19030,launch,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(context,channel)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Джарвіс створив зображення")
            .setContentText("Торкнись, щоб переглянути його на Pixel")
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(action).build();
        try{manager.notify(19030,notification);}catch(SecurityException ignored){}
    }



    /** Only one expensive image request at a time. Never send API credentials to Wear. */
    public static synchronized void generate(Context context,String watchNode,
                                              String request,Callback callback) {
        if(callback==null)return;
        if(context==null || watchNode==null || watchNode.isEmpty()) {
            callback.finished(false,"Телефон або годинник не підключено.","");
            return;
        }
        String prompt=JarvisWearImageCommands.prompt(request);
        if(prompt==null || prompt.isEmpty()) {
            callback.finished(false,
                "Скажи, наприклад: «Створи зображення кота в окулярах».","");
            return;
        }
        if(generating) {
            callback.finished(false,"Попереднє зображення ще створюється.","");
            return;
        }
        Context app=context.getApplicationContext();
        if(generator==null)generator=new AiChatClient(app);
        if(!generator.isConfigured()) {
            callback.finished(false,"Налаштуй ШІ у Джарвісі на Pixel.","");
            return;
        }
        generating=true;
        try {
            generator.sendDetailed(prompt,"image",null,(result,error)->{
                synchronized(JarvisWearImageBridge.class){generating=false;}
                if(error!=null || result==null || result.image==null ||
                   result.image.length==0) {
                    callback.finished(false,error!=null?error:
                        "ШІ не повернув зображення. Перевір генератор на Pixel.","");
                    return;
                }
                // Decoding and writing the original happen off the UI thread.
                new Thread(()->storeAndPublish(app,watchNode,result.image,callback),
                    "jarvis-watch-image").start();
            });
        }catch(RuntimeException e) {
            generating=false;
            callback.finished(false,"Не вдалося запустити генерацію.","");
        }
    }

    private static void storeAndPublish(Context app,String node,byte[] original,Callback cb) {
        File file=null;
        try {
            if(original.length<8 || original.length>MAX_BYTES)
                throw new IllegalArgumentException("Зображення має неприпустимий розмір.");
            // Store only a recognized format. Save-to-gallery also checks the format.
            boolean png=original[0]==(byte)137 && original[1]==80 &&
                original[2]==78 && original[3]==71;
            boolean jpeg=original[0]==(byte)0xFF &&
                original[1]==(byte)0xD8 && original[2]==(byte)0xFF;
            if(!png && !jpeg)throw new IllegalArgumentException(
                "ШІ повернув непідтримуваний формат. Потрібен PNG або JPEG.");
            BitmapFactory.Options dimensions=new BitmapFactory.Options();
            dimensions.inJustDecodeBounds=true;
            BitmapFactory.decodeByteArray(original,0,original.length,dimensions);
            if(dimensions.outWidth<=0 || dimensions.outHeight<=0 ||
               dimensions.outWidth>8192 || dimensions.outHeight>8192)
                throw new IllegalArgumentException("Некоректні розміри зображення.");
            int scale=1;
            while(dimensions.outWidth/scale>640 || dimensions.outHeight/scale>640)
                scale*=2;
            BitmapFactory.Options opts=new BitmapFactory.Options();
            opts.inSampleSize=scale;
            Bitmap bitmap=BitmapFactory.decodeByteArray(original,0,original.length,opts);
            if(bitmap==null)throw new IllegalArgumentException("Не вдалося створити прев’ю.");
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG,78,out);
            byte[] preview=out.toByteArray();
            bitmap.recycle();
            if(preview.length>700000)
                throw new IllegalArgumentException("Прев’ю завелике для Watch7.");

            String id=UUID.randomUUID().toString();
            File dir=new File(app.getCacheDir(),"watch-generated");
            if(!dir.isDirectory() && !dir.mkdirs())
                throw new IllegalStateException("Не вдалося створити кеш на телефоні.");
            file=new File(dir,id+(png?".png":".jpg"));
            try(FileOutputStream stream=new FileOutputStream(file)){
                stream.write(original);
            }
            final File saved=file;
            synchronized(JarvisWearImageBridge.class) {
                purgeExpired();
                images.put(id,new Entry(node,id,saved,System.currentTimeMillis()+TTL));
                while(images.size()>MAX_CACHED) {
                    String first=images.keySet().iterator().next();
                    Entry obsolete=images.remove(first);
                    if(obsolete!=null)obsolete.file.delete();
                }
            }
            PutDataMapRequest request=PutDataMapRequest.create(IMAGE_PATH+id);
            request.getDataMap().putAsset("preview",Asset.createFromBytes(preview));
            request.getDataMap().putLong("created",System.currentTimeMillis());
            Wearable.getDataClient(app).putDataItem(request.asPutDataRequest().setUrgent())
                .addOnSuccessListener(item-> {
                    presentOnPixel(app,id);
                    cb.finished(true,"Зображення створено. Відкриваю на Watch7.",id);
                })
                .addOnFailureListener(error->cb.finished(false,
                    "Зображення створено на Pixel, але прев’ю не передалося на Watch7.",""));
        }catch(Exception e) {
            if(file!=null)file.delete();
            cb.finished(false,e.getMessage()==null?
                "Не вдалося передати зображення на годинник.":e.getMessage(),"");
        }
    }

    private static void purgeExpired() {
        long now=System.currentTimeMillis();
        java.util.Iterator<Map.Entry<String,Entry>> it=images.entrySet().iterator();
        while(it.hasNext()) {
            Entry entry=it.next().getValue();
            if(entry.expiresAt<now) {
                entry.file.delete();
                it.remove();
            }
        }
    }

    /** Save is authorized by tapping the button on the requesting watch.
     * A second watch/node can never save another watch's generated original.
     */
    public static void save(Context context,String watchNode,String imageId,Callback callback) {
        if(callback==null)return;
        final Entry item;
        synchronized(JarvisWearImageBridge.class) {
            purgeExpired();
            item=images.get(imageId);
            if(item==null || watchNode==null || !watchNode.equals(item.watchNode) ||
               !JarvisWearProtocol.validId(imageId)) {
                callback.finished(false,"Зображення не знайдено або термін зберігання минув.","");
                return;
            }
            item.expiresAt=System.currentTimeMillis()+TTL;
        }
        new Thread(()->{
            try {
                if(item.file.length()<=0 || item.file.length()>MAX_BYTES)
                    throw new IllegalArgumentException("Файл пошкоджений.");
                byte[] bytes=new byte[(int)item.file.length()];
                try(FileInputStream stream=new FileInputStream(item.file)) {
                    int read=0;
                    while(read<bytes.length){
                        int count=stream.read(bytes,read,bytes.length-read);
                        if(count<0)throw new IllegalStateException("Файл не дочитано.");
                        read+=count;
                    }
                }
                String feedback=JarvisGeneratedImageStore.save(
                    context.getApplicationContext(),bytes);
                callback.finished(true,feedback,imageId);
            }catch(Exception problem) {
                callback.finished(false,problem.getMessage()==null?
                    "Не вдалося зберегти зображення на Pixel.":problem.getMessage(),"");
            }
        },"jarvis-watch-image-save").start();
    }
}
