package com.andriy.jarvis;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Optional private neural TTS backend (robinhad Ukrainian TTS: Dmytro/Lada).
 * HTTPS only, bearer code in Android Keystore, bounded WAV download, UI-safe playback.
 * The Android system TTS remains the fallback, NOT a substitute for the two models.
 */
public final class NeuralSpeechClient {
    public interface Listener {
        void onStart();
        void onDone();
        void onFailure(String message);
    }

    private static final String PREFS = "jarvis_neural_speech";
    private static final String KEY_ALIAS = "jarvis_neural_speech_token_v1";
    private final SharedPreferences prefs;
    private final File cacheDir;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AtomicInteger generation = new AtomicInteger();
    private MediaPlayer player;
    private File playingFile;
    private volatile boolean closed;

    public NeuralSpeechClient(Context context) {
        Context app = context.getApplicationContext();
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        cacheDir = app.getCacheDir();
    }

    public String endpoint() { return prefs.getString("endpoint", ""); }

    public boolean isConfigured() {
        return NeuralSpeechPolicy.validEndpoint(endpoint()) && prefs.contains("token_encrypted");
    }

    public void configure(String endpoint, String newToken) throws Exception {
        String value = endpoint == null ? "" : endpoint.trim();
        if (!NeuralSpeechPolicy.validEndpoint(value)) {
            throw new IllegalArgumentException("Потрібна HTTPS-адреса власного сервера з шляхом /tts.");
        }
        String encrypted = prefs.getString("token_encrypted", null);
        if (newToken != null && !newToken.isEmpty()) {
            if (!NeuralSpeechPolicy.validToken(newToken)) {
                throw new IllegalArgumentException("Код доступу: від 20 до 256 символів без пробілів.");
            }
            encrypted = encrypt(newToken);
        } else if (encrypted == null) {
            throw new IllegalArgumentException("Введи код доступу до TTS-сервера.");
        }
        stop();
        prefs.edit().putString("endpoint", value).putString("token_encrypted", encrypted).apply();
    }

    public void disconnect() {
        stop();
        prefs.edit().clear().apply();
    }

    /** Returns false if a server is not configured; callers then use Android TTS. */
    public boolean speak(String text, String voice, Listener callback) {
        if (closed || !isConfigured()) return false;
        final int current = generation.incrementAndGet();
        main.post(this::releasePlayer);
        if (!NeuralSpeechPolicy.validText(text)) {
            main.post(() -> {
                if (active(current)) callback.onFailure("Текст завеликий для нейроголосу.");
            });
            return true;
        }
        String url = endpoint();
        // Capture the matching encrypted token now. Reconfiguration must never send a new
        // credential to a previously configured host queued on the executor.
        String encryptedToken = prefs.getString("token_encrypted", "");
        String name = VoiceRanking.profile(voice);
        io.execute(() -> {
            File audio = null;
            try {
                if (!active(current)) return;
                String token = decrypt(encryptedToken);
                audio = download(url, token, text, name);
                if (!active(current)) { audio.delete(); return; }
                final File result = audio;
                main.post(() -> {
                    if (!active(current)) { result.delete(); return; }
                    playFile(result, current, callback);
                });
            } catch (Exception problem) {
                if (audio != null) audio.delete();
                final String reason = problem.getMessage() == null ?
                    "Нейроголос недоступний." : problem.getMessage();
                main.post(() -> {
                    if (active(current)) callback.onFailure(reason);
                });
            }
        });
        return true;
    }

    private File download(String endpoint, String token, String text, String voice) throws Exception {
        HttpURLConnection connection = null;
        File file = null;
        try {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(90000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "audio/wav");
            JSONObject message = new JSONObject().put("voice", voice).put("text", text);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(message.toString().getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            if (status == 401 || status == 403) throw new Exception("Неправильний код TTS-сервера.");
            if (status < 200 || status >= 300) throw new Exception("TTS-сервер: HTTP " + status + ".");
            String type = connection.getContentType();
            if (type == null || !type.toLowerCase(java.util.Locale.ROOT).startsWith("audio/wav")) {
                throw new Exception("TTS-сервер не повернув WAV.");
            }
            file = File.createTempFile("jarvis-voice-", ".wav", cacheDir);
            int received = 0;
            try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(file)) {
                byte[] buf = new byte[8192];
                int count;
                while ((count = in.read(buf)) != -1) {
                    received += count;
                    if (received > NeuralSpeechPolicy.MAX_WAV_BYTES) {
                        throw new Exception("Аудіо від TTS-сервера перевищує ліміт.");
                    }
                    out.write(buf, 0, count);
                }
            }
            if (received < 44) throw new Exception("TTS-сервер повернув порожній WAV.");
            return file;
        } catch (Exception error) {
            if (file != null) file.delete();
            throw error;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void playFile(File file, int current, Listener callback) {
        releasePlayer();
        try {
            playingFile = file;
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            player.setDataSource(file.getAbsolutePath());
            player.setOnPreparedListener(p -> {
                if (!active(current)) return;
                try {
                    JarvisSpeechMouth.startWav(p, file);
                    p.start();
                    callback.onStart();
                } catch (Exception error) {
                    releasePlayer();
                    if (active(current)) callback.onFailure("Неможливо відтворити WAV.");
                }
            });
            player.setOnCompletionListener(p -> {
                releasePlayer();
                if (active(current)) callback.onDone();
            });
            player.setOnErrorListener((p, what, extra) -> {
                releasePlayer();
                if (active(current)) callback.onFailure("Помилка аудіоплеєра.");
                return true;
            });
            player.prepareAsync();
        } catch (Exception error) {
            releasePlayer();
            if (active(current)) callback.onFailure("Не вдалося відкрити WAV.");
        }
    }

    private boolean active(int id) { return !closed && generation.get() == id; }

    /** Call on the main thread, cancels downloads by invalidating their generation. */
    public void stop() {
        generation.incrementAndGet();
        main.post(this::releasePlayer);
    }

    private void releasePlayer() {
        if (player != null) {
            JarvisSpeechMouth.stopWav(player);
            try { player.reset(); player.release(); } catch (Exception ignored) { }
            player = null;
        }
        if (playingFile != null) {
            playingFile.delete();
            playingFile = null;
        }
    }

    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) return (SecretKey) store.getKey(KEY_ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }

    private static String encrypt(String secret) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        return Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" +
            Base64.encodeToString(cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
    }

    private static String decrypt(String encrypted) throws Exception {
        if (encrypted == null || !encrypted.contains(":")) throw new Exception("Введи код TTS-сервера повторно.");
        String[] parts = encrypted.split(":", 2);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(),
            new GCMParameterSpec(128, Base64.decode(parts[0], Base64.DEFAULT)));
        return new String(cipher.doFinal(Base64.decode(parts[1], Base64.DEFAULT)), StandardCharsets.UTF_8);
    }

    public void close() {
        closed = true;
        stop();
        io.shutdownNow();
    }
}
