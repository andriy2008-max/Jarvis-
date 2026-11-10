package com.andriy.jarvis;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Stores ONE shared offline model pack for both speakers in no-backup app storage.
 * Downloads are bound to an explicit SHA-256 from a trusted source. Files persist
 * through APK updates and are NEVER bundled in the APK itself.
 *
 * This installs/validates the ONNX pack only. Native inference must be enabled
 * separately after a verified export; an installed pack is NOT a working TTS engine.
 */
public final class OfflineVoicePackManager {
    public interface Callback {
        void onProgress(int percentage);
        void onComplete(boolean installed, String message);
    }

    private final File root;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AtomicInteger generation = new AtomicInteger();
    private volatile boolean closed;

    public OfflineVoicePackManager(Context context) {
        root = new File(context.getApplicationContext().getNoBackupFilesDir(), "jarvis-offline-voices");
    }

    public boolean hasInstalledFiles() {
        File target = new File(root, "current");
        return new File(target, "manifest.json").isFile() && target.isDirectory();
    }

    public File installedDirectory() { return new File(root, "current"); }

    /** One-tap voice install: discover a verified pack from our own HTTPS GitHub registry.
     * The registry remains disabled until both real voices and Android inference pass QA.
     */
    public void installOfficial(Callback callback) {
        final int id = generation.incrementAndGet();
        io.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL source = new URL(
                    "https://raw.githubusercontent.com/andriy2008-max/Jarvis-/main/android/offline-voices.json");
                connection = (HttpURLConnection) source.openConnection();
                connection.setRequestMethod("GET");
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(12000);
                if (connection.getResponseCode() != 200)
                    throw new Exception("Наразі не вдалося перевірити випуск голосів.");
                byte[] bytes = new byte[8193];
                int read = 0;
                try (InputStream in = connection.getInputStream()) {
                    while (read < bytes.length) {
                        int n = in.read(bytes, read, bytes.length - read);
                        if (n < 0) break;
                        read += n;
                    }
                }
                if (read <= 0 || read > 8192)
                    throw new Exception("Невірний реєстр офлайн-моделей.");
                JSONObject registry = new JSONObject(new String(
                    bytes, 0, read, java.nio.charset.StandardCharsets.UTF_8));
                if (registry.optInt("schema") != 1 ||
                    !OfflineVoicePackRules.FORMAT.equals(registry.optString("format")))
                    throw new Exception("Непідтримуваний реєстр голосів.");
                if (!registry.optBoolean("ready", false))
                    throw new Exception("Реальні офлайн-голоси ще перевіряються. Завантаження відкриється після тестів.");
                JSONArray voices = registry.optJSONArray("voices");
                if (voices == null || voices.length() != 2 ||
                    !"dmytro".equals(voices.optString(0)) ||
                    !"lada".equals(voices.optString(1)))
                    throw new Exception("Реєстр не містить обидва потрібні голоси.");
                String url = registry.optString("url");
                String sha256 = registry.optString("sha256");
                assertActive(id);
                // Queued on this same single-thread executor after registry inspection.
                // SHA-256 is always verified again over the complete binary.
                install(url, sha256, callback);
            } catch (Exception e) {
                report(id, callback, false, e.getMessage() == null ?
                    "Не вдалося дізнатися про офлайн-голоси." : e.getMessage());
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    public void install(String url, String expectedSha256, Callback callback) {
        if (!OfflineVoicePackRules.validUrl(url) ||
            !OfflineVoicePackRules.validSha256(expectedSha256)) {
            main.post(() -> callback.onComplete(false,
                "Вкажи HTTPS-посилання на перевірений ZIP та його SHA-256 (64 символи)."));
            return;
        }
        final int id = generation.incrementAndGet();
        final String source = url.trim();
        final String trustedHash = OfflineVoicePackRules.normalizedSha256(expectedSha256);
        io.execute(() -> {
            File stage = new File(root, "incoming-" + UUID.randomUUID());
            try {
                if (!stage.mkdirs()) throw new Exception("Не вдалося підготувати сховище моделі.");
                File downloaded = new File(stage, "pack.zip");
                download(source, trustedHash, downloaded, id, callback);
                assertActive(id);
                File unpacked = new File(stage, "content");
                if (!unpacked.mkdir()) throw new Exception("Не вдалося розпакувати модель.");
                unpack(downloaded, unpacked, id);
                verifyPack(unpacked, id);
                assertActive(id);
                // Keep old verified files until the new pack is ready.
                File old = new File(root, "previous");
                removeTree(old);
                File current = new File(root, "current");
                if (current.exists() && !current.renameTo(old))
                    throw new Exception("Не вдалося зберегти попередню модель.");
                if (!unpacked.renameTo(current)) {
                    if (old.exists()) old.renameTo(current);
                    throw new Exception("Не вдалося встановити пакет голосів.");
                }
                removeTree(old);
                report(id, callback, true,
                    "Файли обох голосів перевірені та збережені. Android-інференс ще тестується.");
            } catch (Exception error) {
                report(id, callback, false, error.getMessage() == null ?
                    "Не вдалося встановити голосовий пакет." : error.getMessage());
            } finally {
                removeTree(stage);
            }
        });
    }

    public void remove() {
        generation.incrementAndGet();
        io.execute(() -> {
            removeTree(new File(root, "current"));
            removeTree(new File(root, "previous"));
        });
    }

    private void download(String rawUrl, String trustedHash, File to,
                          int id, Callback callback) throws Exception {
        HttpURLConnection connection = null;
        try {
            // GitHub Release assets use HTTPS redirects to a signed assets host.
            // Follow only a small bounded chain of HTTPS links; never allow HTTP downgrade.
            URL target = new URL(rawUrl);
            for (int redirects = 0; ; redirects++) {
                if (connection != null) connection.disconnect();
                if (!"https".equalsIgnoreCase(target.getProtocol()))
                    throw new Exception("Небезпечне перенаправлення моделі: дозволено тільки HTTPS.");
                connection = (HttpURLConnection) target.openConnection();
                connection.setRequestMethod("GET");
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(45000);
                int response = connection.getResponseCode();
                if (response == 301 || response == 302 || response == 303 ||
                    response == 307 || response == 308) {
                    if (redirects >= 5) throw new Exception("Забагато перенаправлень моделі.");
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.isEmpty())
                        throw new Exception("Сервер не надав адресу перенаправлення.");
                    URL next = new URL(target, location);
                    if (!"https".equalsIgnoreCase(next.getProtocol()) ||
                        next.getUserInfo() != null)
                        throw new Exception("Небезпечне перенаправлення моделі.");
                    target = next;
                    continue;
                }
                if (response != 200)
                    throw new Exception("Сервер моделі повернув HTTP " + response + ".");
                break;
            }
            long expected = connection.getContentLengthLong();
            if (expected > OfflineVoicePackRules.MAX_ARCHIVE_BYTES)
                throw new Exception("Пакет моделі перевищує дозволений розмір.");
            if (expected > 0 && root.getUsableSpace() < expected * 2L)
                throw new Exception("Недостатньо вільного місця для моделі та розпакування.");

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long downloaded = 0;
            int previousProgress = -1;
            try (InputStream in = connection.getInputStream();
                 OutputStream out = new FileOutputStream(to)) {
                byte[] buffer = new byte[64 * 1024];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    assertActive(id);
                    downloaded += n;
                    if (downloaded > OfflineVoicePackRules.MAX_ARCHIVE_BYTES)
                        throw new Exception("Пакет завеликий.");
                    digest.update(buffer, 0, n);
                    out.write(buffer, 0, n);
                    if (expected > 0) {
                        int percentage = (int) Math.min(95L, downloaded * 95L / expected);
                        if (percentage != previousProgress) {
                            previousProgress = percentage;
                            int progress = percentage;
                            main.post(() -> { if (active(id)) callback.onProgress(progress); });
                        }
                    }
                }
            }
            if (expected > 0 && downloaded != expected)
                throw new Exception("Завантаження обірвалося. Спробуй знову.");
            String actual = hex(digest.digest());
            if (!actual.equals(trustedHash))
                throw new Exception("SHA-256 не збігається. Файл відхилено без встановлення.");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void unpack(File source, File dest, int id) throws Exception {
        Set<String> entries = new HashSet<>();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(source))) {
            ZipEntry entry;
            byte[] buffer = new byte[64 * 1024];
            while ((entry = zip.getNextEntry()) != null) {
                assertActive(id);
                String name = entry.getName();
                if (entry.isDirectory()) {
                    zip.closeEntry();
                    continue;
                }
                if (!OfflineVoicePackRules.validEntry(name) || !entries.add(name) ||
                    entries.size() > OfflineVoicePackRules.MAX_ENTRIES)
                    throw new Exception("Некоректний склад архіву голосів.");
                File target = new File(dest, name);
                if (!target.getCanonicalPath().startsWith(dest.getCanonicalPath() + File.separator))
                    throw new Exception("Недозволений шлях у пакеті.");
                File folder = target.getParentFile();
                if (!folder.isDirectory() && !folder.mkdirs())
                    throw new Exception("Не вдалося створити папку моделі.");
                try (OutputStream out = new FileOutputStream(target)) {
                    int count;
                    while ((count = zip.read(buffer)) != -1) {
                        assertActive(id);
                        total += count;
                        if (total > OfflineVoicePackRules.MAX_EXTRACTED_BYTES)
                            throw new Exception("Розпакована модель перевищує допустимий розмір.");
                        out.write(buffer, 0, count);
                    }
                }
                zip.closeEntry();
            }
        }
        if (!entries.contains("manifest.json") || entries.size() < 2)
            throw new Exception("В архіві немає маніфесту або ONNX-файлів.");
    }

    private static void verifyPack(File dir, int id) throws Exception {
        File manifestFile = new File(dir, "manifest.json");
        if (!manifestFile.isFile() || manifestFile.length() > 2_000_000L)
            throw new Exception("Маніфест моделі відсутній або некоректний.");
        byte[] data = new byte[(int) manifestFile.length()];
        try (InputStream in = new FileInputStream(manifestFile)) {
            int pos = 0, n;
            while (pos < data.length && (n = in.read(data, pos, data.length - pos)) > 0)
                pos += n;
            if (pos != data.length) throw new Exception("Маніфест завантажений не повністю.");
        }
        JSONObject manifest = new JSONObject(new String(data, java.nio.charset.StandardCharsets.UTF_8));
        if (!OfflineVoicePackRules.FORMAT.equals(manifest.optString("format")) ||
            manifest.optInt("sample_rate") < 16000 || manifest.optInt("sample_rate") > 48000)
            throw new Exception("Непідтримуваний формат голосового пакета.");
        JSONArray voices = manifest.optJSONArray("voices");
        if (voices == null || voices.length() != 2 ||
            !("dmytro".equals(voices.optString(0)) && "lada".equals(voices.optString(1))))
            throw new Exception("Пакет повинен містити спільну модель Дмитра і Лади.");
        if (!manifest.optBoolean("smoke_verified", false))
            throw new Exception("Пакет не проходив перевірку синтезу ONNX.");
        JSONArray tokens = manifest.optJSONArray("tokens");
        if (tokens == null || tokens.length() < 40 || tokens.length() > 5000)
            throw new Exception("Неправильний словник української моделі.");
        JSONObject vectors = manifest.optJSONObject("spembs");
        if (vectors == null) throw new Exception("У пакеті відсутні голосові профілі.");
        for (String voice : new String[] {"dmytro", "lada"}) {
            JSONArray v = vectors.optJSONArray(voice);
            if (v == null || v.length() < 64 || v.length() > 512)
                throw new Exception("Неправильна голосова матриця " + voice + ".");
            for (int k = 0; k < v.length(); k++) {
                double sample = v.optDouble(k, Double.NaN);
                if (!Double.isFinite(sample))
                    throw new Exception("Некоректні дані голосу " + voice + ".");
            }
        }
        JSONObject synth = manifest.optJSONObject("synthesis");
        if (synth == null || !"Tacotron2".equals(synth.optString("model_type")) ||
            synth.optInt("dlayers") < 1 || synth.optInt("dlayers") > 8 ||
            synth.optInt("dunits") < 64 || synth.optInt("dunits") > 2048 ||
            synth.optInt("odim") < 32 || synth.optInt("odim") > 256 ||
            synth.optInt("reduction_factor") != 1 ||
            synth.optBoolean("use_att_constraint"))
            throw new Exception("Недостатньо параметрів для локального Tacotron2.");
        int melDims = synth.getInt("odim");
        JSONArray mean = synth.optJSONArray("mean");
        JSONArray std = synth.optJSONArray("std");
        if (mean == null || std == null ||
            mean.length() != melDims || std.length() != melDims)
            throw new Exception("Невірні параметри нормалізації моделі.");
        // Expected submodels for the ESPnet Tacotron2 + HiFiGAN mobile runner.
        Set<String> requiredGraphs = new HashSet<>(java.util.Arrays.asList(
            "onnx/full/tts_model_encoder.onnx",
            "onnx/full/tts_model_decoder.onnx",
            "onnx/full/predecoder_0.onnx",
            "onnx/full/postdecoder.onnx",
            "onnx/full/HiFiGANVocoder.onnx"));
        JSONArray files = manifest.optJSONArray("files");
        if (files == null || files.length() < 1 || files.length() > OfflineVoicePackRules.MAX_ENTRIES - 1)
            throw new Exception("Не вказано модельні файли.");
        Set<String> expected = new HashSet<>();
        for (int i = 0; i < files.length(); i++) {
            JSONObject spec = files.getJSONObject(i);
            String name = spec.optString("path");
            String hash = spec.optString("sha256");
            long size = spec.optLong("bytes", -1);
            if (!OfflineVoicePackRules.validEntry(name) || name.equals("manifest.json") ||
                !OfflineVoicePackRules.validSha256(hash) || size <= 0 || !expected.add(name))
                throw new Exception("Невірний опис файлів у маніфесті.");
            File model = new File(dir, name);
            if (!model.isFile() || model.length() != size)
                throw new Exception("Не вистачає ONNX-файлу: " + name);
            if (!hash.equalsIgnoreCase(sha256(model)))
                throw new Exception("Контрольна сума ONNX-файлу не збігається: " + name);
        }
        if (!expected.containsAll(requiredGraphs))
            throw new Exception("Не всі модулі Tacotron2 та HiFiGAN включені в пакет.");
        if (!expected.containsAll(modelFiles(dir)))
            throw new Exception("Архів містить невказані ONNX-файли.");
    }

    private static Set<String> modelFiles(File dir) {
        Set<String> names = new HashSet<>();
        collect(dir, dir, names);
        return names;
    }

    private static void collect(File base, File node, Set<String> names) {
        if (node.isFile()) {
            String path = base.toURI().relativize(node.toURI()).getPath();
            if (!"manifest.json".equals(path)) names.add(path);
        } else {
            File[] files = node.listFiles();
            if (files != null) for (File f : files) collect(base, f, names);
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) != -1) sha.update(buffer, 0, n);
        }
        return hex(sha.digest());
    }

    private static String hex(byte[] digest) {
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte b : digest) result.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return result.toString();
    }

    private boolean active(int id) { return !closed && generation.get() == id; }

    private void assertActive(int id) throws Exception {
        if (!active(id)) throw new Exception("Завантаження скасовано.");
    }

    private void report(int id, Callback callback, boolean installed, String message) {
        main.post(() -> { if (active(id)) callback.onComplete(installed, message); });
    }

    private static void removeTree(File file) {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) removeTree(child);
        }
        file.delete();
    }

    public void close() {
        closed = true;
        generation.incrementAndGet();
        io.shutdownNow();
    }
}
