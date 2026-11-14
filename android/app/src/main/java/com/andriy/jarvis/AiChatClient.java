package com.andriy.jarvis;

import android.content.Context;
import android.net.Uri;
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

    /** Detailed result for chosen pictures, documents, live search and generated art. */
    public interface DetailedCallback { void onComplete(AiResponse result, String error); }

    public static final class AiResponse {
        public final String reply;
        public final String sources;
        public final byte[] image;
        private AiResponse(String reply, String sources, byte[] image) {
            this.reply = reply; this.sources = sources; this.image = image;
        }
    }

    private static final String PREFS = "jarvis_ai_connection";
    private static final String ALIAS = "jarvis_ai_access_code_v1";
    private static final int MAX_REPLY_BYTES = 16 * 1024 * 1024;

    private final SharedPreferences prefs;
    private final Context appContext;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<JSONObject> history = new ArrayList<>();
    private volatile boolean closed;
    private volatile int generation;

    public AiChatClient(Context context) {
        appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String endpoint() { return prefs.getString("endpoint", ""); }

    public String getReasoningEffort() {
        String value = prefs.getString("reasoning_effort", "medium");
        return "none".equals(value) || "low".equals(value) ||
               "medium".equals(value) || "high".equals(value) ? value : "medium";
    }

    public void setReasoningEffort(String value) {
        if (!("none".equals(value) || "low".equals(value) ||
              "medium".equals(value) || "high".equals(value)))
            throw new IllegalArgumentException("Недопустима глибина міркування ШІ.");
        prefs.edit().putString("reasoning_effort", value).apply();
    }

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
        sendDetailed(question, "chat", null, (response, error) ->
            callback.onComplete(response == null ? null : response.reply, error));
    }

    /** Real authenticated model request, isolated from conversation history. */
    public void checkConnection(Callback callback) {
        if (closed || !isConfigured()) {
            ui.post(() -> callback.onComplete(null,"ШІ не налаштований або вимкнений."));
            return;
        }
        final String url = endpoint();
        executor.execute(() -> {
            String answer = null;
            String error = null;
            try {
                JSONObject request = new JSONObject();
                request.put("message","Відповідай одним словом: OK");
                request.put("mode","chat");
                request.put("history",new JSONArray());
                String token = decrypt(prefs.getString("code_encrypted",""));
                AiResponse response = post(url,token,request);
                if (response == null || response.reply == null || response.reply.trim().isEmpty())
                    throw new Exception("ШІ повернув порожню відповідь.");
                answer = response.reply;
            } catch (Exception failure) {
                error = failure.getMessage() == null ? "Не вдалося перевірити ШІ." : failure.getMessage();
            }
            final String reply = answer, problem = error;
            ui.post(() -> callback.onComplete(reply,problem));
        });
    }

    /**
     * Explicit modes, optional user-picked URI. Only the selected file is read.
     * Parsing, encoding and network I/O all happen on our worker thread.
     */
    public void sendDetailed(String question, String mode, Uri attachment,
                             DetailedCallback callback) {
        if (closed) {
            ui.post(() -> callback.onComplete(null, "ШІ вже вимкнено."));
            return;
        }
        if (!isConfigured()) {
            ui.post(() -> callback.onComplete(null, "Спочатку налаштуй підключення до ШІ."));
            return;
        }
        final String message = question == null ? "" : question.trim();
        if (message.isEmpty() || message.length() > AiChatPolicy.MAX_MESSAGE_CHARS) {
            ui.post(() -> callback.onComplete(null,
                "Повідомлення повинне містити 1–4000 символів."));
            return;
        }
        final String requestMode = mode == null ? "chat" : mode;
        if (!("chat".equals(requestMode) || "web".equals(requestMode) ||
                "image".equals(requestMode) || "analysis".equals(requestMode)) ||
            (attachment != null && "web".equals(requestMode))) {
            ui.post(() -> callback.onComplete(null, "Недоступний режим ШІ."));
            return;
        }
        final int currentGeneration;
        final List<JSONObject> context;
        synchronized (this) {
            currentGeneration = generation;
            context = new ArrayList<>();
            int remainingBytes = 9000;
            for (int i = history.size() - 1; i >= 0; i--) {
                JSONObject turn = history.get(i);
                int bytes = turn.toString().getBytes(StandardCharsets.UTF_8).length;
                if (bytes > remainingBytes) break;
                context.add(0, turn);
                remainingBytes -= bytes;
            }
        }
        final String url = endpoint();
        executor.execute(() -> {
            AiResponse answer = null;
            String error = null;
            try {
                String token = decrypt(prefs.getString("code_encrypted", ""));
                JSONObject request = new JSONObject();
                request.put("message", message);
                request.put("mode", requestMode);
                request.put("effort", getReasoningEffort());
                JSONArray previous = new JSONArray();
                for (JSONObject turn : context) previous.put(turn);
                request.put("history", previous);
                if (attachment != null)
                    request.put("attachment", AiAttachmentLoader.read(appContext, attachment));
                answer = post(url, token, request);
            } catch (Exception ex) {
                error = ex.getMessage() == null ? "Не вдалося звернутися до ШІ." : ex.getMessage();
            }
            final AiResponse done = answer;
            final String failure = error;
            if (done != null) {
                synchronized (this) {
                    if (!closed && currentGeneration == generation) {
                        try {
                            history.add(turn("user", attachment == null ? message :
                                "[Обраний файл] " + message));
                            history.add(turn("assistant",
                                done.reply.length() > 5000
                                    ? done.reply.substring(0, 5000) : done.reply));
                            while (history.size() > AiChatPolicy.MAX_CONTEXT_TURNS)
                                history.remove(0);
                        } catch (Exception ignored) { }
                    }
                }
            }
            ui.post(() -> {
                if (!closed && currentGeneration == generation)
                    callback.onComplete(done, failure);
            });
        });
    }

    private static JSONObject turn(String role, String message) throws Exception {
        JSONObject o = new JSONObject();
        o.put("role", role);
        o.put("content", message);
        return o;
    }

    private static AiResponse post(String endpoint, String clientToken,
                                   JSONObject request) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            // Image editing and web searches can take longer than text chat.
            conn.setReadTimeout("image".equals(request.optString("mode")) ? 120000 : 65000);
            conn.setDoOutput(true);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("Authorization", "Bearer " + clientToken);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json");
            byte[] data = request.toString().getBytes(StandardCharsets.UTF_8);
            if (data.length > 5 * 1024 * 1024)
                throw new Exception("Обраний файл завеликий для ШІ.");
            conn.setFixedLengthStreamingMode(data.length);
            try (OutputStream output = conn.getOutputStream()) { output.write(data); }
            int status = conn.getResponseCode();
            if (status == 401 || status == 403)
                throw new Exception("Неправильний код доступу до ШІ-сервера.");
            if (status == 429)
                throw new Exception("Ліміт ШІ тимчасово вичерпано. Перевір витрати API.");
            if (status < 200 || status >= 300) {
                if (status == 400) throw new Exception(
                    "Сервер не прийняв фото або документ. Перевір, чи оновлено Worker.");
                if (status == 413) throw new Exception("Файл завеликий для сервера.");
                if (status == 502) {
                    String reason = "";
                    int providerStatus = 0;
                    try (InputStream details = conn.getErrorStream()) {
                        if (details != null) {
                            JSONObject diagnostic = new JSONObject(readLimited(details));
                            reason = diagnostic.optString("reason","");
                            providerStatus = diagnostic.optInt("provider_status",0);
                        }
                    } catch (Exception ignored) { }
                    if ("invalid_api_key".equals(reason))
                        throw new Exception("OpenAI API відхилив ключ. Перевір OPENAI_API_KEY у Cloudflare.");
                    if ("billing_limit".equals(reason))
                        throw new Exception("Недостатньо коштів або досягнуто ліміт OpenAI API. Перевір Billing.");
                    if ("verification_required".equals(reason))
                        throw new Exception("Для генерації зображень потрібна перевірка організації OpenAI API.");
                    if ("model_unavailable".equals(reason))
                        throw new Exception("Обрана модель недоступна цьому API-проєкту. Перевір OPENAI_IMAGE_MODEL у Cloudflare.");
                    if ("permission_denied".equals(reason))
                        throw new Exception("API-проєкт не має дозволу на цей інструмент або модель.");
                    if ("invalid_request".equals(reason))
                        throw new Exception("OpenAI API відхилив параметри запиту. Код HTTP " + providerStatus + ".");
                    if ("provider_unavailable".equals(reason))
                        throw new Exception("Сервіс OpenAI тимчасово недоступний. Спробуй пізніше.");
                    throw new Exception("Модель або інструмент ШІ недоступний. Перевір налаштування API.");
                }
                throw new Exception("ШІ-сервер відповів помилкою HTTP " + status + ".");
            }
            try (InputStream input = conn.getInputStream()) {
                JSONObject result = new JSONObject(readLimited(input));
                String response = result.optString("reply", "").trim();
                if (response.isEmpty()) throw new Exception("ШІ повернув порожню відповідь.");
                JSONArray citations = result.optJSONArray("sources");
                StringBuilder links = new StringBuilder();
                if (citations != null) {
                    for (int i = 0; i < Math.min(8,citations.length()); i++) {
                        JSONObject source = citations.optJSONObject(i);
                        if (source == null) continue;
                        String title = source.optString("title", "").trim();
                        String link = source.optString("url", "");
                        if (!link.startsWith("https://") || link.length() > 2000) continue;
                        links.append(title.isEmpty() ? "Джерело" : title)
                            .append("\n").append(link).append("\n");
                    }
                }
                byte[] image = null;
                String encoded = result.optString("image_base64", "");
                if (!encoded.isEmpty()) {
                    if (encoded.length() > 11 * 1024 * 1024)
                        throw new Exception("Створене зображення завелике.");
                    image = Base64.decode(encoded, Base64.DEFAULT);
                    if (image.length > 9 * 1024 * 1024)
                        throw new Exception("Зображення перевищує ліміт.");
                }
                return new AiResponse(
                    response.length() > 12000 ? response.substring(0, 12000) : response,
                    links.toString().trim(), image);
            }
        } catch (java.net.SocketTimeoutException exception) {
            throw new Exception("ШІ не відповів вчасно. Перевір інтернет або повтори запит.");
        } catch (java.io.IOException exception) {
            throw new Exception("Не вдалося зв'язатися з ШІ-сервером. Перевір HTTPS та інтернет.");
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
