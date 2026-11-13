package com.andriy.jarvis;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.SystemClock;
import android.view.KeyEvent;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Єдиний виконавець ШІ-дій. Немає shell, довільних Intent або натискань. */
public final class JarvisToolRuntime {
    private static final JarvisToolSession SESSION = new JarvisToolSession();
    private static final String PREFS = "jarvis_tool_actions_v1";
    private static final Map<String, Executor> EXECUTORS = new HashMap<>();
    private static JarvisToolRegistry registry;
    private static String lastAction;
    private static long lastActionTime;
    private static JSONArray dialogue = new JSONArray();
    private interface Executor { Result run(Context context, JSONObject args) throws Exception; }
    private static final class Result {
        final String state, message;
        Result(String state, String message) { this.state = state; this.message = message; }
    }
    static {
        EXECUTORS.put("volume_set", (c,a) -> volume(c, a.getInt("percent")));
        EXECUTORS.put("volume_adjust", (c,a) -> volume(c,
            Math.max(0, Math.min(100, volumePercent(c) + a.getInt("delta")))));
        EXECUTORS.put("torch_set", (c,a) -> torch(c, a.getBoolean("enabled")));
        EXECUTORS.put("music_control", (c,a) -> music(c, a.getString("action")));
        EXECUTORS.put("timer_start", (c,a) -> TimerScheduler.start(c, a.getInt("seconds"))
            ? new Result("completed", "Таймер встановлено на " + a.getInt("seconds") + " с.")
            : new Result("failed", "Не вдалося поставити таймер. Перевір дозвіл точних будильників у налаштуваннях Джарвіса."));
        EXECUTORS.put("timer_cancel", (c,a) -> TimerScheduler.cancel(c)
            ? new Result("completed", "Таймер скасовано.") : new Result("failed", "Активного таймера немає."));
    }
    private JarvisToolRuntime() { }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
    private static JarvisToolRegistry registry(Context c) throws Exception {
        if (registry == null) {
            try (InputStream in = c.getAssets().open("tool-catalog.json")) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096]; int read;
                while ((read = in.read(buffer)) != -1) {
                    if (bytes.size() + read > 32768) throw new IllegalStateException("Каталог завеликий");
                    bytes.write(buffer, 0, read);
                }
                registry = new JarvisToolRegistry(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            }
        }
        return registry;
    }
    public static synchronized String beginRequest() { return SESSION.begin(SystemClock.elapsedRealtime()); }
    public static synchronized void clear() { SESSION.invalidate(); lastAction = null; dialogue = new JSONArray(); }
    public static synchronized JSONArray history() {
        try { return new JSONArray(dialogue.toString()); } catch (Exception ignored) { return new JSONArray(); }
    }
    public static synchronized void rememberDialogue(String user, String result) {
        try {
            if (user == null || result == null) return;
            JSONArray next = new JSONArray();
            for (int i = Math.max(0, dialogue.length() - 4); i < dialogue.length(); i++) next.put(dialogue.get(i));
            next.put(new JSONObject().put("role", "user").put("content", user.substring(0, Math.min(user.length(), 4000))));
            next.put(new JSONObject().put("role", "assistant").put("content", result.substring(0, Math.min(result.length(), 2000))));
            dialogue = next;
        } catch (Exception ignored) { }
    }
    public static synchronized JSONObject context() {
        try { return lastAction != null && SystemClock.elapsedRealtime() - lastActionTime <= 120000
            ? new JSONObject(lastAction) : null; }
        catch (Exception ignored) { return null; }
    }

    /** Викликається ДО всіх локальних обробників: новий ввід скасовує стару пропозицію. */
    public static synchronized String onUserInput(Context c, String raw) {
        String text = CommandPatterns.normalize(raw).replaceFirst("^(?:джарвіс|джарвис|jarvis)[, :—-]*", "");
        if (text.equals("підтверджую дію")) {
            String pending = SESSION.confirm(SystemClock.elapsedRealtime());
            SESSION.invalidate();
            if (pending == null) return "Немає чинної дії для підтвердження. Повтори команду.";
            try {
                JSONObject saved = new JSONObject(pending), decision = saved.getJSONObject("decision");
                if (!saved.getString("state").equals(state(c, decision)))
                    return "Стан телефона змінився. Повтори команду, щоб перевірити дію заново.";
                return execute(c, decision);
            } catch (Exception invalid) { return "Не вдалося перевірити дію. Повтори команду."; }
        }
        SESSION.invalidate();
        if (text.equals("скасуй дію") || text.equals("не виконуй дію")) return "Дію ШІ скасовано.";
        if (text.equals("журнал дій ші")) return journal(c);
        return null;
    }

    public static synchronized String accept(Context c, String requestId, JSONObject decision, String reply) {
        if (!SESSION.claim(requestId, SystemClock.elapsedRealtime()))
            return "Попередній запит уже скасований або застарів. Дію не виконано.";
        if (decision == null) return reply;
        try {
            if (!registry(c).valid(decision)) return "Відхилено неправильну пропозицію ШІ. Дію не виконано.";
            String tool = decision.getString("tool");
            if (tool.equals("clarify")) return decision.getJSONObject("args").getString("question");
            if (tool.equals("unsupported")) return "Поки немає інструмента: " +
                decision.getJSONObject("args").getString("capability") + ". Цю можливість можна додати окремо.";
            if (decision.getDouble("confidence") < 0.85) return "Уточни, будь ласка, яку саме дію потрібно виконати.";
            if (!EXECUTORS.containsKey(tool)) return "Виконавець цієї дії ще не встановлений.";
            if (needsConfirmation(c, decision)) {
                String snapshot = new JSONObject().put("decision", decision).put("state", state(c, decision)).toString();
                if (!audit(c, tool, "awaiting_confirmation")) return "Не вдалося записати дію в журнал. Виконання заблоковано.";
                SESSION.awaitConfirmation(snapshot, SystemClock.elapsedRealtime());
                return describe(decision) + " Скажи «Підтверджую дію» протягом 30 секунд або «Скасуй дію».";
            }
            return execute(c, decision);
        } catch (Exception invalid) { return "Не вдалося перевірити дію на телефоні. Дію не виконано."; }
    }

    private static boolean needsConfirmation(Context c, JSONObject d) throws Exception {
        String tool = d.getString("tool"); JSONObject a = d.getJSONObject("args");
        if (registry(c).confirmationRequired(d)) return true;
        if (tool.equals("timer_start")) return TimerScheduler.remainingSeconds(c) >= 0;
        if (tool.equals("volume_set")) return a.getInt("percent") >= 85;
        return tool.equals("volume_adjust") && a.getInt("delta") > 0 && volumePercent(c) + a.getInt("delta") >= 85;
    }
    private static String state(Context c, JSONObject d) {
        if (d.optString("tool").startsWith("timer_")) {
            SharedPreferences timer = c.getSharedPreferences(TimerScheduler.PREFS, Context.MODE_PRIVATE);
            return timer.getLong("timer_id", 0) + ":" + timer.getLong("deadline_wall", 0);
        }
        return Integer.toString(volumePercent(c));
    }
    private static String describe(JSONObject d) throws Exception {
        JSONObject a = d.getJSONObject("args");
        switch (d.getString("tool")) {
            case "timer_cancel": return "Скасувати поточний таймер?";
            case "timer_start": return "Замінити поточний таймер новим на " + a.getInt("seconds") + " с?";
            case "volume_set": return "Встановити медіагучність " + a.getInt("percent") + "%?";
            case "volume_adjust": return "Збільшити медіагучність на " + a.getInt("delta") + " відсоткових пунктів?";
            default: throw new IllegalArgumentException("Немає опису підтвердження");
        }
    }
    private static String execute(Context c, JSONObject d) throws Exception {
        if (!registry(c).valid(d) || d.getDouble("confidence") < 0.85)
            return "Дія не пройшла повторну перевірку.";
        String tool = d.getString("tool"); Executor executor = EXECUTORS.get(tool);
        if (executor == null) return "Недоступний інструмент.";
        // Запис ДО побічного ефекту. Повторів після невизначеного результату немає.
        if (!audit(c, tool, "started")) return "Не вдалося записати дію в журнал. Виконання заблоковано.";
        Result result;
        try { result = executor.run(c, d.getJSONObject("args")); }
        catch (Exception error) { result = new Result("failed", "Android не дозволив завершити дію. Перевір дозволи та стан пристрою."); }
        if (!result.state.equals("failed")) { lastAction = d.toString(); lastActionTime = SystemClock.elapsedRealtime(); }
        boolean recorded = audit(c, tool, result.state);
        return result.message + (recorded ? "" : " Не вдалося зберегти результат у журналі; не повторюй дію без перевірки.");
    }
    private static AudioManager audio(Context c) {
        AudioManager value = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (value == null || value.getStreamMaxVolume(AudioManager.STREAM_MUSIC) <= 0)
            throw new IllegalStateException("Аудіо недоступне");
        return value;
    }
    private static int volumePercent(Context c) {
        AudioManager a = audio(c);
        return Math.round(100f * a.getStreamVolume(AudioManager.STREAM_MUSIC) / a.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
    }
    private static Result volume(Context c, int percent) {
        AudioManager a = audio(c);
        if (a.isVolumeFixed()) return new Result("failed", "Android забороняє змінювати гучність.");
        a.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(a.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * percent / 100f), AudioManager.FLAG_SHOW_UI);
        return new Result("completed", "Медіагучність: " + volumePercent(c) + "%.");
    }
    private static Result torch(Context c, boolean enabled) throws Exception {
        if (c.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            return new Result("failed", "Для ліхтарика надай Джарвісу дозвіл камери в налаштуваннях Android і повтори команду.");
        CameraManager cameras = (CameraManager) c.getSystemService(Context.CAMERA_SERVICE);
        if (cameras != null) for (String id : cameras.getCameraIdList()) {
            CameraCharacteristics info = cameras.getCameraCharacteristics(id);
            if (Boolean.TRUE.equals(info.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)) &&
                Integer.valueOf(CameraCharacteristics.LENS_FACING_BACK).equals(info.get(CameraCharacteristics.LENS_FACING))) {
                cameras.setTorchMode(id, enabled);
                return new Result("submitted", "Передав Android команду " + (enabled ? "увімкнути" : "вимкнути") + " ліхтарик.");
            }
        }
        return new Result("failed", "На цьому пристрої немає доступного ліхтарика.");
    }
    private static Result music(Context c, String action) {
        int key;
        switch (action) {
            case "play": key = KeyEvent.KEYCODE_MEDIA_PLAY; break;
            case "pause": key = KeyEvent.KEYCODE_MEDIA_PAUSE; break;
            case "next": key = KeyEvent.KEYCODE_MEDIA_NEXT; break;
            case "previous": key = KeyEvent.KEYCODE_MEDIA_PREVIOUS; break;
            default: throw new IllegalArgumentException("Невідома дія музики");
        }
        AudioManager a = audio(c);
        a.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, key));
        a.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, key));
        return new Result("submitted", "Команду передано активному медіапрогравачу. Перевір відтворення.");
    }
    private static boolean audit(Context c, String tool, String status) {
        try {
            JSONArray old = new JSONArray(prefs(c).getString("journal", "[]")), next = new JSONArray();
            for (int i = Math.max(0, old.length() - 99); i < old.length(); i++) next.put(old.get(i));
            next.put(new JSONObject().put("time", System.currentTimeMillis()).put("tool", tool).put("status", status));
            // Не записуємо голос, ключі, історію чату чи довільний текст моделі.
            return prefs(c).edit().putString("journal", next.toString()).commit();
        } catch (Exception failed) { return false; }
    }
    private static String journal(Context c) {
        try {
            JSONArray entries = new JSONArray(prefs(c).getString("journal", "[]"));
            if (entries.length() == 0) return "Журнал дій ШІ порожній.";
            StringBuilder out = new StringBuilder("Останні дії ШІ:");
            for (int i = Math.max(0, entries.length() - 10); i < entries.length(); i++) {
                JSONObject row = entries.getJSONObject(i);
                out.append("\n").append(row.getString("tool")).append(": ").append(row.getString("status"));
            }
            return out.toString();
        } catch (Exception failed) { return "Не вдалося прочитати журнал дій."; }
    }
}
