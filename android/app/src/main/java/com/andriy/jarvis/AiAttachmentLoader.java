package com.andriy.jarvis;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Base64;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** One-off user-selected Android document/photo. No broad storage permissions. */
public final class AiAttachmentLoader {
    public static final int MAX_FILE_BYTES = 2700000;
    private static final Map<String, String> TYPES = new HashMap<>();
    static {
        for (String ext : new String[]{"jpg","jpeg"}) TYPES.put(ext,"image/jpeg");
        TYPES.put("png","image/png");
        TYPES.put("webp","image/webp");
        TYPES.put("pdf","application/pdf");
        TYPES.put("txt","text/plain"); TYPES.put("md","text/markdown");
        TYPES.put("py","text/plain"); TYPES.put("js","text/plain");
        TYPES.put("java","text/plain"); TYPES.put("kt","text/plain");
        TYPES.put("log","text/plain");
        TYPES.put("csv","text/csv"); TYPES.put("json","application/json");
        TYPES.put("xml","application/xml");
        TYPES.put("html","text/html"); TYPES.put("htm","text/html");
        TYPES.put("docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        TYPES.put("xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        TYPES.put("pptx","application/vnd.openxmlformats-officedocument.presentationml.presentation");
        TYPES.put("doc","application/msword");
        TYPES.put("xls","application/vnd.ms-excel");
        TYPES.put("ppt","application/vnd.ms-powerpoint");
        TYPES.put("rtf","application/rtf");
    }

    private AiAttachmentLoader() {}

    public static boolean isImage(String mime) {
        return "image/jpeg".equals(mime) || "image/png".equals(mime) ||
            "image/webp".equals(mime);
    }

    public static String safeName(String name) {
        if (name == null) return "file";
        String safe = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (safe.length() > 100) {
            int dot = safe.lastIndexOf('.');
            String ext = dot < 0 ? "" : safe.substring(dot);
            safe = safe.substring(0, Math.min(100-ext.length(), safe.length())) + ext;
        }
        return safe.isEmpty() ? "file" : safe;
    }

    public static String mimeForName(String name) {
        if (name == null) return null;
        int dot = name.lastIndexOf('.');
        if (dot < 0) return null;
        return TYPES.get(name.substring(dot+1).toLowerCase(Locale.ROOT));
    }

    private static String displayName(ContentResolver resolver, Uri uri) {
        try (Cursor c = resolver.query(uri,
            new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) { }
        return null;
    }

    public static JSONObject read(Context context, Uri uri) throws Exception {
        if (uri == null || !"content".equalsIgnoreCase(uri.getScheme()))
            throw new Exception("Обери файл через системний переглядач Android.");
        ContentResolver resolver = context.getApplicationContext().getContentResolver();
        String name = safeName(displayName(resolver,uri));
        String mime = mimeForName(name);
        if (mime == null)
            throw new Exception("Непідтримуваний тип файлу. Обери фото, PDF, документ або таблицю.");
        byte[] bytes;
        try (InputStream stream = resolver.openInputStream(uri)) {
            if (stream == null) throw new Exception("Не вдалося прочитати вибраний файл.");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = stream.read(chunk)) != -1) {
                if (out.size() + read > MAX_FILE_BYTES) {
                    if (isImage(mime)) return compressPhoto(context,uri,name);
                    throw new Exception("Документ завеликий. Максимальний розмір — 2,7 МБ.");
                }
                out.write(chunk,0,read);
            }
            bytes = out.toByteArray();
        }
        if (bytes.length == 0) throw new Exception("Файл порожній.");
        return pack(name,mime,bytes);
    }

    private static JSONObject compressPhoto(Context context, Uri uri, String original) throws Exception {
        ContentResolver resolver = context.getApplicationContext().getContentResolver();
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream stream = resolver.openInputStream(uri)) {
            BitmapFactory.decodeStream(stream,null,bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
            throw new Exception("Не вдалося декодувати фотографію.");
        BitmapFactory.Options opts = new BitmapFactory.Options();
        int max = Math.max(bounds.outWidth,bounds.outHeight);
        opts.inSampleSize = 1;
        while (max / opts.inSampleSize > 1800) opts.inSampleSize *= 2;
        Bitmap bitmap;
        try (InputStream stream = resolver.openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(stream,null,opts);
        }
        if (bitmap == null) throw new Exception("Не вдалося зменшити фотографію.");
        byte[] compressed = null;
        try {
            for (int quality = 85; quality >= 55; quality -= 10) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG,quality,bytes);
                compressed = bytes.toByteArray();
                if (compressed.length <= MAX_FILE_BYTES) break;
            }
        } finally { bitmap.recycle(); }
        if (compressed == null || compressed.length > MAX_FILE_BYTES)
            throw new Exception("Фотографія завелика для аналізу.");
        String stem = original.replaceFirst("(?i)\\.[^.]+$","");
        return pack(safeName(stem + ".jpg"),"image/jpeg",compressed);
    }

    private static JSONObject pack(String name, String mime, byte[] bytes) throws Exception {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_FILE_BYTES)
            throw new Exception("Файл завеликий або порожній.");
        JSONObject data = new JSONObject();
        data.put("name",name);
        data.put("mime",mime);
        data.put("data",Base64.encodeToString(bytes,Base64.NO_WRAP));
        return data;
    }
}
