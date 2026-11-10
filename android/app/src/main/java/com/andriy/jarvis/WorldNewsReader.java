package com.andriy.jarvis;

import android.os.Handler;
import android.os.Looper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Fetches Ukrainian world headlines on demand; no keys, AI, or persistent cache. */
public final class WorldNewsReader {
    public interface Callback {
        void onComplete(String spokenNews, String error);
    }

    private static final String WORLD_RSS =
        "https://news.google.com/rss/headlines/section/topic/WORLD?hl=uk&gl=UA&ceid=UA:uk";
    private static final int MAX_BYTES = 180000;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean closed;
    private volatile int generation;

    public void read(Callback callback) {
        if (closed) return;
        int request = ++generation;
        worker.execute(() -> {
            String digest = null;
            String error = null;
            try {
                digest = WorldNewsFeed.spokenDigest(download(), System.currentTimeMillis());
            } catch (IllegalStateException noFreshItems) {
                error = noFreshItems.getMessage();
            } catch (Exception e) {
                error = "Не вдалося отримати світові новини. Перевір інтернет та спробуй ще раз.";
            }
            final String text = digest, problem = error;
            main.post(() -> {
                if (!closed && generation == request) callback.onComplete(text, problem);
            });
        });
    }

    private static String download() throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(WORLD_RSS).openConnection();
            conn.setConnectTimeout(9000);
            conn.setReadTimeout(14000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml");
            conn.setRequestProperty("User-Agent", "Jarvis-WorldHeadlines/1.0");
            int status = conn.getResponseCode();
            if (status < 200 || status >= 300)
                throw new Exception("Unexpected news response.");
            try (InputStream input = conn.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[4096];
                int count;
                while ((count = input.read(chunk)) != -1) {
                    if (output.size() + count > MAX_BYTES)
                        throw new Exception("News response exceeds limit.");
                    output.write(chunk, 0, count);
                }
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
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
