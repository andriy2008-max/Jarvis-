package com.andriy.jarvis;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * User-initiated one-time download of the archived Supertonic 3 ONNX model.
 * Files are pinned to an immutable upstream revision and SHA-256 verified.
 * The ~400 MB weights are never packaged into the APK.
 * Model: OpenRAIL-M, code adapter: MIT.
 */
public final class Supertonic3PackManager {
    public interface Callback {
        void onProgress(int percent, String name);
        void onComplete(boolean success, String reason);
    }

    static final String REVISION = "aafc6e32416a594460b32413efc49d7fe4ce6d46";
    private static final String ROOT = "https://huggingface.co/supertone-oss-archive/supertonic-3/resolve/" + REVISION + "/";
    private static final String[] FILES = {
        "onnx/duration_predictor.onnx",
        "onnx/text_encoder.onnx",
        "onnx/vector_estimator.onnx",
        "onnx/vocoder.onnx",
        "onnx/tts.json",
        "onnx/unicode_indexer.json",
        "voice_styles/M1.json",
        "voice_styles/F1.json"
    };
    private static final String[] HASHES = {
        "c3eb91414d5ff8a7a239b7fe9e34e7e2bf8a8140d8375ffb14718b1c639325db",
        "c7befd5ea8c3119769e8a6c1486c4edc6a3bc8365c67621c881bbb774b9902ff",
        "883ac868ea0275ef0e991524dc64f16b3c0376efd7c320af6b53f5b780d7c61c",
        "085de76dd8e8d5836d6ca66826601f615939218f90e519f70ee8a36ed2a4c4ba",
        "", "", "", ""
    };
    private static final long[] EXPECTED_BYTES = {
        3_700_000L, 36_400_000L, 257_000_000L, 101_000_000L,
        8250L, 278_000L, 292_000L, 292_000L
    };
    private static final long[] MAX_BYTES = {
        9_000_000L, 55_000_000L, 325_000_000L, 145_000_000L,
        100_000L, 800_000L, 1_000_000L, 1_000_000L
    };
    private final File root;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private volatile boolean closed;

    public Supertonic3PackManager(Context context) {
        root = new File(context.getApplicationContext().getFilesDir(), "supertonic-3");
    }

    File directory() { return root; }

    public boolean hasInstalledFiles() {
        if (!new File(root, "ready.v1").isFile()) return false;
        for (String path : FILES) if (!new File(root, path).isFile()) return false;
        return true;
    }

    public void install(Callback callback) {
        if (closed) { callback.onComplete(false, "Менеджер моделі закрито."); return; }
        io.execute(() -> {
            try {
                if (hasInstalledFiles()) {
                    complete(callback, true, "Supertonic 3 уже встановлено.");
                    return;
                }
                if (root.getUsableSpace() < 530_000_000L)
                    throw new Exception("Для Supertonic 3 звільни щонайменше 530 МБ пам'яті.");
                if (!root.isDirectory() && !root.mkdirs())
                    throw new Exception("Не вдалося створити папку моделі.");
                new File(root, "ready.v1").delete();
                long total = 0L, completed = 0L;
                for (long n : EXPECTED_BYTES) total += n;
                int lastPercent = -1;
                for (int i = 0; i < FILES.length; i++) {
                    if (closed || Thread.currentThread().isInterrupted())
                        throw new InterruptedException("Завантаження зупинено.");
                    File dest = new File(root, FILES[i]);
                    File folder = dest.getParentFile();
                    if (!folder.isDirectory() && !folder.mkdirs())
                        throw new Exception("Не вдалося створити папку для " + FILES[i]);
                    if (!verifiedExisting(dest, HASHES[i], MAX_BYTES[i])) {
                        final int index = i;
                        final long soFar = completed, full = total;
                        final String filename = FILES[i];
                        download(index, dest, bytes -> {
                            int percent = (int) Math.min(99, (soFar + Math.min(bytes, EXPECTED_BYTES[index])) * 100L / full);
                            ui.post(() -> { if (!closed) callback.onProgress(percent, filename); });
                        });
                    }
                    completed += EXPECTED_BYTES[i];
                    int pct = (int) Math.min(99, completed * 100L / total);
                    if (pct != lastPercent) {
                        final int p = pct;
                        final String name = FILES[i];
                        ui.post(() -> { if (!closed) callback.onProgress(p, name); });
                        lastPercent = pct;
                    }
                }
                verifyConfig();
                try (FileOutputStream out = new FileOutputStream(new File(root, "ready.v1"))) {
                    out.write(REVISION.getBytes(StandardCharsets.US_ASCII));
                    out.getFD().sync();
                }
                complete(callback, true, "Supertonic 3 встановлено. Можна перевіряти українські та англійські фрази.");
            } catch (Exception error) {
                complete(callback, false, "Supertonic 3: " + (error.getMessage() == null ? "помилка завантаження" : error.getMessage()));
            }
        });
    }

    private interface Progress { void update(long bytes); }

    private void download(int index, File dest, Progress progress) throws Exception {
        File temporary = new File(dest.getPath() + ".part");
        temporary.delete();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(ROOT + FILES[index] + "?download=true").openConnection();
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(90000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Jarvis-Supertonic3/1.0");
            if (connection.getResponseCode() != 200)
                throw new Exception("Hugging Face: HTTP " + connection.getResponseCode());
            if (!"https".equalsIgnoreCase(connection.getURL().getProtocol()))
                throw new Exception("Небезпечне перенаправлення завантаження.");
            long count = 0L;
            long lastReported = 0L;
            try (InputStream in = connection.getInputStream();
                 FileOutputStream out = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[64 * 1024];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    if (closed || Thread.currentThread().isInterrupted())
                        throw new InterruptedException("Завантаження зупинено.");
                    count += n;
                    if (count > MAX_BYTES[index]) throw new Exception("Файл моделі перевищив допустимий розмір.");
                    out.write(buffer, 0, n);
                    if (count - lastReported >= 2_000_000L) {
                        progress.update(count);
                        lastReported = count;
                    }
                }
                out.getFD().sync();
            }
            if (!verifiedExisting(temporary, HASHES[index], MAX_BYTES[index]))
                throw new Exception("Не пройшла перевірка SHA-256: " + FILES[index]);
            if (!temporary.renameTo(dest))
                throw new Exception("Не вдалося зберегти файл " + FILES[index]);
        } finally {
            if (connection != null) connection.disconnect();
            temporary.delete();
        }
    }

    private static boolean verifiedExisting(File file, String expected, long max) throws Exception {
        if (!file.isFile() || file.length() < 2 || file.length() > max) return false;
        if (expected.isEmpty()) return true;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[128 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) digest.update(buf, 0, n);
        }
        StringBuilder sha = new StringBuilder();
        for (byte b : digest.digest()) sha.append(String.format(Locale.ROOT, "%02x", b & 255));
        return expected.equals(sha.toString());
    }

    private void verifyConfig() throws Exception {
        for (int i = 0; i < FILES.length; i++)
            if (!verifiedExisting(new File(root, FILES[i]), HASHES[i], MAX_BYTES[i]))
                throw new Exception("Не перевірено файл " + FILES[i]);
        String json = new String(java.nio.file.Files.readAllBytes(
            new File(root, "onnx/tts.json").toPath()), StandardCharsets.UTF_8);
        JSONObject cfg = new JSONObject(json);
        if (cfg.optJSONObject("ae") == null || cfg.optJSONObject("ttl") == null)
            throw new Exception("Некоректні параметри Supertonic.");
        for (String voice : new String[] {"M1", "F1"}) {
            JSONObject style = new JSONObject(new String(java.nio.file.Files.readAllBytes(
                new File(root, "voice_styles/" + voice + ".json").toPath()), StandardCharsets.UTF_8));
            if (!style.has("style_ttl") || !style.has("style_dp"))
                throw new Exception("Некоректний голос " + voice);
        }
    }

    private void complete(Callback cb, boolean success, String message) {
        ui.post(() -> { if (!closed) cb.onComplete(success, message); });
    }

    public void close() {
        closed = true;
        io.shutdownNow();
    }
}
