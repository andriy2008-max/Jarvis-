package com.andriy.jarvis;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * User-owned automation data and fail-closed runtime. No AI-generated shell commands.
 * Root commands are optional and only execute when explicitly saved in the editor.
 */
public final class JarvisMacros {
    public interface Listener { void onResult(String result); }

    private static final String PREFS = "jarvis_local_macros_v1";
    private static final String KEY = "macros";
    private static final String CHANNEL = "jarvis_macros";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static long lastVoiceMs;
    private static String lastVoice;

    private JarvisMacros() {}

    public static List<JSONObject> all(Context context) {
        ArrayList<JSONObject> items = new ArrayList<>();
        String data = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]");
        try {
            JSONArray array = new JSONArray(data);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item != null) items.add(item);
            }
        } catch (Exception ignored) { }
        return items;
    }

    public static synchronized void save(Context context, JSONObject macro) {
        if (!macro.has("id")) {
            try { macro.put("id", UUID.randomUUID().toString()); }
            catch (Exception ignored) {}
        }
        List<JSONObject> items = all(context);
        String id = macro.optString("id");
        boolean found = false;
        for (int i = 0; i < items.size(); i++) {
            if (id.equals(items.get(i).optString("id"))) {
                items.set(i, macro);
                found = true;
                break;
            }
        }
        if (!found) items.add(macro);
        store(context, items);
        scheduleAll(context);
    }

    public static synchronized void delete(Context context, String id) {
        List<JSONObject> items = all(context);
        items.removeIf(m -> id.equals(m.optString("id")));
        store(context, items);
        cancelAlarm(context, id);
        scheduleAll(context);
    }

    private static void store(Context context, List<JSONObject> items) {
        JSONArray data = new JSONArray();
        for (JSONObject item : items) data.put(item);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, data.toString()).commit();
    }

    public static boolean handleVoice(Context context, String words, Listener listener) {
        String normalized = CommandPatterns.normalize(words).replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) return false;
        for (JSONObject m : all(context)) {
            if (!m.optBoolean("enabled", true) ||
                !"voice".equals(m.optString("trigger", "voice"))) continue;
            String phrase = CommandPatterns.normalize(m.optString("triggerValue"))
                .replaceAll("\\s+", " ").trim();
            if (!phrase.isEmpty() && phrase.equals(normalized)) {
                long now = SystemClock.elapsedRealtime();
                if (phrase.equals(lastVoice) && now - lastVoiceMs < 2200) return true;
                lastVoice = phrase;
                lastVoiceMs = now;
                execute(context, m, listener);
                return true;
            }
        }
        return false;
    }

    public static boolean handleEvent(Context context, String trigger, Listener listener) {
        boolean matched = false;
        for (JSONObject m : all(context)) {
            if (m.optBoolean("enabled", true) && trigger.equals(m.optString("trigger"))) {
                matched = true;
                execute(context, m, listener);
            }
        }
        return matched;
    }

    public static void handleTime(Context context, String id, Listener listener) {
        boolean fired = false;
        for (JSONObject m : all(context)) {
            if (id.equals(m.optString("id"))) {
                if (m.optBoolean("enabled", true) && "time".equals(m.optString("trigger"))) {
                    fired = true;
                    execute(context, m, listener);
                }
                break;
            }
        }
        scheduleAll(context);
        if (!fired) report(listener, "Макрос неактивний.");
    }

    public static boolean allowed(Context context, JSONObject m) {
        JSONObject c = m.optJSONObject("constraints");
        if (c == null) return true; // no constraints = always allowed
        Calendar now = Calendar.getInstance();
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        String exact = c.optString("date").trim();
        if (!exact.isEmpty() && !date.equals(exact)) return false;
        String days = c.optString("days").trim();
        if (!days.isEmpty()) {
            int mondayBased = (now.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1;
            boolean matches = false;
            for (String part : days.split(",")) {
                if (part.trim().equals(Integer.toString(mondayBased))) matches = true;
            }
            if (!matches) return false;
        }
        String from = c.optString("timeFrom").trim();
        String until = c.optString("timeTo").trim();
        if (!from.isEmpty() || !until.isEmpty()) {
            if (!validTime(from) || !validTime(until)) return false;
            int start = minutes(from), end = minutes(until);
            int current = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
            boolean valid = start <= end ? current >= start && current <= end
                : current >= start || current <= end;
            if (!valid) return false;
        }
        int minBattery = c.optInt("minBattery", -1);
        if (minBattery >= 0) {
            BatteryManager battery = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            if (battery == null) return false;
            int charge = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            if (charge < 0 || charge < minBattery) return false;
        }
        String charger = c.optString("charging", "any");
        if (!"any".equals(charger)) {
            android.content.IntentFilter filter =
                new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent state = context.registerReceiver(null, filter);
            if (state == null) return false;
            int status = state.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL;
            if (charging != "yes".equals(charger)) return false;
        }
        String ssid = c.optString("ssid").trim();
        if (!ssid.isEmpty()) {
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return false;
            WifiManager wifi = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
            WifiInfo info = wifi == null ? null : wifi.getConnectionInfo();
            if (info == null || !ssid.equals(info.getSSID().replace("\"", ""))) return false;
        }
        if (c.has("lat") || c.has("lon")) {
            if (!c.has("lat") || !c.has("lon") || !c.has("radius") ||
                context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) return false;
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return false;
            Location best = null;
            try {
                for (String provider : lm.getProviders(true)) {
                    Location candidate = lm.getLastKnownLocation(provider);
                    if (candidate != null && (best == null || candidate.getTime() > best.getTime()))
                        best = candidate;
                }
            } catch (SecurityException ignored) { return false; }
            if (best == null || Math.abs(System.currentTimeMillis() - best.getTime()) > 600000L)
                return false; // a stale position never authorizes automation
            Location target = new Location("macro");
            target.setLatitude(c.optDouble("lat"));
            target.setLongitude(c.optDouble("lon"));
            if (best.distanceTo(target) > c.optDouble("radius", 100.0)) return false;
        }
        return true;
    }

    private static int minutes(String value) {
        String[] p = value.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }
    public static boolean validTime(String value) {
        if (value == null || !value.matches("\\d\\d:\\d\\d")) return false;
        int n = minutes(value);
        return n >= 0 && n <= 1439;
    }

    public static void execute(Context context, JSONObject macro, Listener listener) {
        if (!allowed(context, macro)) {
            report(listener, "Макрос «" + macro.optString("name") +
                "» не виконано: обмеження не виконуються.");
            return;
        }
        JSONArray actions = macro.optJSONArray("actions");
        if (actions == null || actions.length() == 0) {
            report(listener, "У макросі немає дій.");
            return;
        }
        if (!RUNNING.compareAndSet(false, true)) {
            report(listener, "Інший макрос ще виконується.");
            return;
        }
        new Execution(context.getApplicationContext(), macro.optString("name"),
            actions, listener).next();
    }

    private static void report(Listener listener, String result) {
        if (listener != null) MAIN.post(() -> listener.onResult(result));
    }

    private static final class Execution {
        final Context context;
        final String name;
        final JSONArray steps;
        final Listener listener;
        int index;
        boolean finished;
        Execution(Context context, String name, JSONArray steps, Listener listener) {
            this.context = context;
            this.name = name;
            this.steps = steps;
            this.listener = listener;
        }
        void complete(boolean ok, String info) {
            if (finished) return;
            finished = true;
            RUNNING.set(false);
            report(listener, (ok ? "Макрос «" + name + "» завершено." :
                "Макрос «" + name + "» зупинено: " + info));
        }
        void next() {
            MAIN.post(() -> {
                if (finished) return;
                if (index >= steps.length()) { complete(true, ""); return; }
                JSONObject step = steps.optJSONObject(index++);
                if (step == null) { complete(false, "пошкоджений крок"); return; }
                String type = step.optString("type");
                String value = step.optString("value").trim();
                try {
                    switch (type) {
                        case "wait": {
                            int ms = Integer.parseInt(value);
                            if (ms < 0 || ms > 60000) { complete(false, "пауза 0–60000 мс"); return; }
                            MAIN.postDelayed(this::next, ms);
                            return;
                        }
                        case "notification":
                            notifyUser(context, name, value);
                            next();
                            return;
                        case "vibrate": {
                            int ms = Integer.parseInt(value);
                            if (ms < 1 || ms > 5000) { complete(false, "вібрація 1–5000 мс"); return; }
                            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                            if (vibrator == null) { complete(false, "вібрація недоступна"); return; }
                            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
                            next();
                            return;
                        }
                        case "command": {
                            DeviceCommands.Action device = DeviceCommands.parse(
                                CommandPatterns.normalize(value));
                            if (device != null) {
                                DeviceActions performer = new DeviceActions(context);
                                String result;
                                try { result = performer.perform(device); }
                                finally { performer.close(); }
                                if (result == null || result.contains("не дозвол") ||
                                    result.contains("недоступ") || result.contains("не підтрим") ||
                                    result.contains("потрібен дозвіл") ||
                                    result.contains("Відкрий Джарвіс"))
                                    complete(false, result == null ? "дія не виконана" : result);
                                else next();
                                return;
                            }
                            JarvisCommandSequence.Plan plan = JarvisCommandSequence.parse(value);
                            if (plan == null || !plan.valid()) {
                                complete(false, "непідтримувана команда «" + value + "»");
                                return;
                            }
                            JarvisSequenceExecutor executor = new JarvisSequenceExecutor(context);
                            executor.start(plan, new JarvisSequenceExecutor.Listener() {
                                @Override public void onProgress(int n, int total, String info) { }
                                @Override public void onFinished(boolean ok, String info) {
                                    if (ok) next(); else complete(false, info);
                                }
                            });
                            return;
                        }
                        case "root": {
                            if (!step.optBoolean("rootApproved", false)) {
                                complete(false, "root-дія не підтверджена"); return;
                            }
                            if (value.isEmpty() || value.length() > 600) {
                                complete(false, "порожня або завелика root-команда"); return;
                            }
                            new Thread(() -> {
                                boolean ok = false;
                                Process process = null;
                                try {
                                    process = new ProcessBuilder("su", "-c", value)
                                        .redirectErrorStream(true).start();
                                    ok = process.waitFor(8, TimeUnit.SECONDS) &&
                                        process.exitValue() == 0;
                                } catch (Exception ignored) { }
                                finally { if (process != null) process.destroyForcibly(); }
                                boolean success = ok;
                                MAIN.post(() -> {
                                    if (success) next();
                                    else complete(false, "root-команда не виконана або root не надано");
                                });
                            }, "JarvisMacroRoot").start();
                            return;
                        }
                        default:
                            complete(false, "невідомий тип дії " + type);
                    }
                } catch (Exception e) { complete(false, "помилка дії " + type); }
            });
        }
    }

    private static void notifyUser(Context context, String title, String text) {
        NotificationManager manager = (NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(
            Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        manager.createNotificationChannel(new NotificationChannel(
            CHANNEL, "Джарвіс — макроси", NotificationManager.IMPORTANCE_DEFAULT));
        Notification notification = new Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText(text).setAutoCancel(true).build();
        manager.notify((int)(SystemClock.elapsedRealtime() & 0x7fffffff), notification);
    }

    private static PendingIntent pending(Context context, String id) {
        Intent intent = new Intent(context, JarvisMacroReceiver.class);
        intent.setAction("com.andriy.jarvis.MACRO_TIME");
        intent.putExtra("macro_id", id);
        intent.setData(android.net.Uri.parse("jarvis-macro://time/" + id));
        return PendingIntent.getBroadcast(context, id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    private static void cancelAlarm(Context context, String id) {
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm != null) alarm.cancel(pending(context, id));
    }
    public static void scheduleAll(Context context) {
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null) return;
        for (JSONObject m : all(context)) {
            String id = m.optString("id");
            if (id.isEmpty()) continue;
            cancelAlarm(context, id);
            if (!m.optBoolean("enabled", true) ||
                !"time".equals(m.optString("trigger"))) continue;
            String time = m.optString("triggerValue");
            if (!validTime(time)) continue;
            Calendar c = Calendar.getInstance();
            c.set(Calendar.HOUR_OF_DAY, minutes(time) / 60);
            c.set(Calendar.MINUTE, minutes(time) % 60);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            if (c.getTimeInMillis() <= System.currentTimeMillis())
                c.add(Calendar.DAY_OF_YEAR, 1);
            try {
                if (Build.VERSION.SDK_INT >= 31 && !alarm.canScheduleExactAlarms())
                    alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                        c.getTimeInMillis(), pending(context,id));
                else alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                        c.getTimeInMillis(), pending(context,id));
            } catch (SecurityException denied) {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                    c.getTimeInMillis(), pending(context,id));
            }
        }
    }
}
