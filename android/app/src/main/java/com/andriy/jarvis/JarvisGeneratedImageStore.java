package com.andriy.jarvis;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Persists an AI-created PNG only after the user taps Save.
 * Android 10+ MediaStore makes it visible in Gallery without storage permissions;
 * older devices use app-specific external Pictures storage.
 */
public final class JarvisGeneratedImageStore {
    private JarvisGeneratedImageStore() { }

    public static String save(Context context, byte[] png) throws Exception {
        if (png == null || png.length < 8 || png.length > 9 * 1024 * 1024)
            throw new Exception("Невірний формат або розмір зображення.");
        if (!(png[0] == (byte)137 && png[1] == 80 &&
              png[2] == 78 && png[3] == 71))
            throw new Exception("Очікувалося зображення у форматі PNG.");
        String filename = "JARVIS-" + System.currentTimeMillis() + ".png";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues meta = new ContentValues();
            meta.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
            meta.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            meta.put(MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/Jarvis");
            meta.put(MediaStore.Images.Media.IS_PENDING, 1);
            Uri destination = context.getContentResolver()
                .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, meta);
            if (destination == null) throw new Exception("Немає доступу до сховища зображень.");
            try {
                try (OutputStream output = context.getContentResolver().openOutputStream(destination)) {
                    if (output == null) throw new Exception("Не вдалося відкрити збереження.");
                    output.write(png);
                }
                ContentValues ready = new ContentValues();
                ready.put(MediaStore.Images.Media.IS_PENDING, 0);
                context.getContentResolver().update(destination, ready, null, null);
                return "Зображення збережено в Галереї → Pictures/Jarvis.";
            } catch (Exception error) {
                context.getContentResolver().delete(destination, null, null);
                throw error;
            }
        }
        File external = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (external == null)
            throw new Exception("Зовнішнє сховище недоступне.");
        File folder = new File(external, "Jarvis");
        if (!folder.exists() && !folder.mkdirs())
            throw new Exception("Не вдалося створити папку Jarvis.");
        File image = new File(folder, filename);
        try (FileOutputStream output = new FileOutputStream(image)) {
            output.write(png);
        }
        return "Зображення збережено у файлах застосунку: " + image.getAbsolutePath();
    }
}
