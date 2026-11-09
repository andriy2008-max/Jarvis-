package com.andriy.jarvis;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Personal chat over a user-configured HTTPS proxy. OpenAI keys are NEVER stored on Android.
 * The per-device access code is encrypted using an Android Keystore AES/GCM key.
 * Conversation context is in RAM only; clearing or restarting the app forgets it.
 */
public final class AiChatClient {
    public interface Callback { void onComplete(String reply, String error); }

    private static final String PREFS = "jarvis_ai_connection";
    private static final String ALIAS = "jarvis_ai_access_code_v1";
    private static final int MAX_REPLY_BYTES = 32 * 1024;

    private final SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<JSONObject> history = new ArrayList<>();
    private volatile boolean closed;
    private volatile int generation;

    public AiChatClient(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String endpoint() { return prefs.getString("endpoint", ""); }

    public boolean isConfigured() {
        return AiChatPolicy.isValidEndpoint(endpoint()) && prefs.contains("code_encrypted");
    }

    public void configure(String endpoint, String accessCode) throws Exception {
        String clean = endpoint == null ? "" : endpoint.trim();
        if (!AiChatPolicy.isValidEndpoint(clean)) {
            throw new IllegalArgumentException("Потрібна адреса HTTPS із шляхом /chat, без параметрів.");
        }
        String savedCipher = prefs.getString("code_encrypted", null);
        String newEncrypted = savedCipher;
        if (accessCode != null && !accessCode.isEmpty()) {
            if (!AiChatPolicy.isValidAccessCode(accessCode)) {
                throw new IllegalArgumentException("Код доступу має містити щонайменше 20 символів.");
            }
            newEncrypted = encrypt(accessCode);
        } else if (savedCipher == null) {
            throw new IllegalArgumentException("Введи код доступу до свого сервера (не ключ OpenAI).");
        }
        prefs.edit().putString("endpoint", clean).putString("code_encrypted", newEncrypted).apply();
        clearHistory();
    }

    public void disconnect() {
        clearHistory();
        prefs.edit().clear().apply();
    }

    public synchronized void clearHistory() {
        history.clear();
        generation++;
    }

    public void send(String question, Callback callback) {
        if (closed) { ui.post(() -> callback.onComplete(null, "ШІ вже вимкнено.")); return; }
        if (!isConfigured()) {
            ui.post(() -> callback.onComplete(null, "Спочатку налаштуй підключення до ШІ."));
            return;
        }
        final String message = question == null ? "" : question.trim();
        if (message.isEmpty() || message.length() > AiChatPolicy.MAX_MESSAGE_CHARS) {
            ui.post(() -> callback.onComplete(null, "Повідомлення має містити від 1 до 2000 символів."));
            return;
        }
        final int requestGeneration;
        final List<JSONObject> context;
        synchronized (this) {
            requestGeneration = generation;
            // Keep the JSON request below the proxy's byte limit, even with long
            // conversations in Ukrainian (UTF-8 can use multiple bytes/character).
            context = new ArrayList<>();
            int remainingBytes = 9000;
            for (int i = history.size() - 1; i >= 0; i--) {
                JSONObject turn = history.get(i);
                int length = turn.toString().getBytes(StandardCharsets.UTF_8).length;
                if (length > remainingBytes) break;
                context.add(0, turn);
                remainingBytes -= length;
            }
        }
        final String url = endpoint();
        executor.execute(() -> {
            String answer = null;
            String error = null;
            try {
                String token = decrypt(prefs.getString("code_encrypted", ""));
                JSONObject request = new JSONObject();
                request.put("message", message);
                JSONArray previous = new JSONArray();
                for (JSONObject turn : context) previous.put(turn);
                request.put("history", previous);
                answer = post(url, token, request);
            } catch (Exception exception) {
                error = exception.getMessage() == null ? "Не вдалося підключитися до ШІ." : exception.getMessage();
            }
            final String done = answer;
            final String problem = error;
            if (done != null) {
                synchronized (this) {
                    if (requestGeneration == generation && !closed) {
                        try {
                            history.add(turn("user", message));
                            history.add(turn("assistant", done));
                            while (history.size() > AiChatPolicy.MAX_CONTEXT_TURNS) history.remove(0);
                        } catch (Exception ignored) { }
                    }
                }
            }
            ui.post(() -> {
                if (!closed && requestGeneration == generation) callback.onComplete(done, problem);
            });
        });
    }

    private static JSONObject turn(String role, String message) throws Exception {
        JSONObject o = new JSONObject();
        o.put("role", role);
        o.put("content", message);
        return o;
    }

    private static String post(String endpoint, String clientToken, JSONObject request) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(35000);
            conn.setDoOutput(true);
            conn.setInstanceFollowRedirects(false); // Prevent leaking access tokens to redirected hosts.
            conn.setRequestProperty("Authorization", "Bearer " + clientToken);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json");
            byte[] body = request.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = conn.getOutputStream()) { output.write(body); }
            int status = conn.getResponseCode();
            if (status == 401 || status == 403) throw new Exception("Неправильний код доступу до ШІ-сервера.");
            if (status == 429) throw new Exception("Ліміт ШІ тимчасово вичерпано. Спробуй пізніше.");
            if (status < 200 || status >= 300) throw new Exception("ШІ-сервер відповів помилкою HTTP " + status + ".");
            try (InputStream input = conn.getInputStream()) {
                JSONObject reply = new JSONObject(readLimited(input));
                String answer = reply.optString("reply", "").trim();
                if (answer.isEmpty()) throw new Exception("ШІ повернув порожню відповідь.");
                return answer.length() > 6000 ? answer.substring(0, 6000) : answer;
            }
        } catch (java.net.SocketTimeoutException exception) {
            throw new Exception("ШІ не відповів вчасно. Перевір інтернет і повтори.");
        } catch (java.io.IOException exception) {
            throw new Exception("Не вдалося з'єднатися з ШІ-сервером. Перевір інтернет і HTTPS-адресу.");
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readLimited(InputStream input) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int amount;
        while ((amount = input.read(chunk)) != -1) {
            if (buffer.size() + amount > MAX_REPLY_BYTES) throw new Exception("ШІ надіслав завелику відповідь.");
            buffer.write(chunk, 0, amount);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private static SecretKey getKey() throws Exception {
        KeyStore keystore = KeyStore.getInstance("AndroidKeyStore");
        keystore.load(null);
        if (keystore.containsAlias(ALIAS)) return (SecretKey) keystore.getKey(ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build());
        return generator.generateKey();
    }

    private static String encrypt(String secret) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getKey());
        byte[] ciphertext = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" +
            Base64.encodeToString(ciphertext, Base64.NO_WRAP);
    }

    private static String decrypt(String encrypted) throws Exception {
        if (encrypted == null || !encrypted.contains(":")) throw new Exception("Налаштуй код доступу до сервера повторно.");
        String[] parts = encrypted.split(":", 2);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getKey(),
            new GCMParameterSpec(128, Base64.decode(parts[0], Base64.DEFAULT)));
        return new String(cipher.doFinal(Base64.decode(parts[1], Base64.DEFAULT)), StandardCharsets.UTF_8);
    }

    public void close() {
        closed = true;
        clearHistory();
        executor.shutdownNow();
    }
}
