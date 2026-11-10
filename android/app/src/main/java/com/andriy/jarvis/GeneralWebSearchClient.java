package com.andriy.jarvis;

import android.os.Handler;
import android.os.Looper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Public query-only Bing RSS lookup. Unofficial feed may be unavailable or limited. */
public final class GeneralWebSearchClient {
    public interface Callback {
        void onComplete(GeneralWebSearchResults found, String error);
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean closed;
    private volatile int generation;

    public void search(String query, Callback callback) {
        if (closed) return;
        int request = ++generation;
        worker.execute(() -> {
            GeneralWebSearchResults found = null;
            String error = null;
            try {
                found = GeneralWebSearchResults.parse(fetch(query));
            } catch (Exception failure) {
                error = "Не вдалося отримати результати з інтернету. Перевір з'єднання або повтори запит.";
            }
            final GeneralWebSearchResults answer = found;
            final String problem = error;
            ui.post(() -> {
                if (!closed && generation == request) callback.onComplete(answer, problem);
            });
        });
    }

    private static String fetch(String query) throws Exception {
        String address = "https://www.bing.com/search?format=rss&q=" +
            URLEncoder.encode(query, "UTF-8");
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(address).openConnection();
            conn.setConnectTimeout(9000);
            conn.setReadTimeout(16000);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 JarvisSearch/1.0");
            if (conn.getResponseCode() != 200)
                throw new IllegalStateException("RSS provider unavailable");
            String type = conn.getContentType();
            if (type != null && !type.toLowerCase(java.util.Locale.ROOT).contains("xml") &&
                !type.toLowerCase(java.util.Locale.ROOT).contains("rss"))
                throw new IllegalStateException("Non-RSS response");
            try (InputStream stream = conn.getInputStream();
                 ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] block = new byte[4096];
                int count;
                while ((count = stream.read(block)) != -1) {
                    if (bytes.size() + count > 170000)
                        throw new IllegalStateException("RSS too large");
                    bytes.write(block, 0, count);
                }
                return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public void close() {
        closed = true;
        generation++;
        worker.shutdownNow();
    }
}
