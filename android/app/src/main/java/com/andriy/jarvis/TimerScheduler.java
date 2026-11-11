package com.andriy.jarvis;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import java.security.SecureRandom;

/** One durable, user-requested timer per app install. No in-process Handler for countdown. */
public final class TimerScheduler {
    static final String PREFS = "jarvis_background_timer";
    private static final String END = "deadline_wall";
    private static final String ID = "timer_id";
    private static final String FINISHED = "last_finished_id";
    static final int ALARM_REQUEST = 7714;
    static final int OPEN_REQUEST = 7715;
    static final String FIRE = "com.andriy.jarvis.TIMER_FIRE";
    static final int ALERT_NOTIFICATION = 7716;

    private TimerScheduler() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static AlarmManager manager(Context c) {
        return (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
    }

    public static boolean canScheduleExact(Context c) {
        AlarmManager alarm = manager(c);
        return alarm != null && (Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms());
    }

    private static PendingIntent fireIntent(Context c, long id) {
        Intent intent = new Intent(c, TimerFireReceiver.class);
        intent.setAction(FIRE);
        intent.putExtra(ID, id);
        return PendingIntent.getBroadcast(c, ALARM_REQUEST, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent showIntent(Context c) {
        Intent intent = new Intent(c, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, OPEN_REQUEST, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void scheduleSystemAlarm(Context c, long id, long when) {
        AlarmManager alarm = manager(c);
        if (alarm == null) throw new IllegalStateException("AlarmManager unavailable");
        alarm.setAlarmClock(new AlarmManager.AlarmClockInfo(when, showIntent(c)), fireIntent(c, id));
    }

    /** Returns false when Android has not granted exact-alarm access; caller opens settings. */
    public static synchronized boolean start(Context context, int seconds) {
        long feedback = JarvisPhoneMode.isActive() ? JarvisControlFeedback.begin() : 0L;
        boolean success = false;
        try { success = startInternal(context, seconds); return success; }
        finally { JarvisControlFeedback.finish(feedback,success,android.os.SystemClock.uptimeMillis()); }
    }

    private static boolean startInternal(Context context, int seconds) {
        if (!TimerRules.valid(seconds)) return false;
        Context c = context.getApplicationContext();
        if (!canScheduleExact(c)) return false;
        long end = TimerRules.deadline(System.currentTimeMillis(), seconds);
        long id = new SecureRandom().nextLong() & Long.MAX_VALUE;
        if (id == 0) id = 1;
        cancelScheduled(c);
        if (!prefs(c).edit().putLong(ID, id).putLong(END, end).putLong(FINISHED, 0).commit()) {
            return false;
        }
        try {
            scheduleSystemAlarm(c, id, end);
            return true;
        } catch (RuntimeException error) {
            prefs(c).edit().remove(ID).remove(END).commit();
            return false;
        }
    }

    private static void cancelScheduled(Context c) {
        AlarmManager alarm = manager(c);
        if (alarm != null) alarm.cancel(fireIntent(c, 0));
    }

    public static synchronized boolean cancel(Context context) {
        long feedback = JarvisPhoneMode.isActive() ? JarvisControlFeedback.begin() : 0L;
        boolean success = false;
        try { success = cancelInternal(context); return success; }
        finally { JarvisControlFeedback.finish(feedback,success,android.os.SystemClock.uptimeMillis()); }
    }

    private static boolean cancelInternal(Context context) {
        Context c = context.getApplicationContext();
        boolean active = prefs(c).getLong(ID, 0L) != 0L;
        cancelScheduled(c);
        prefs(c).edit().remove(ID).remove(END).remove(FINISHED).commit();
        boolean ringing = c.stopService(new Intent(c, TimerAlertService.class));
        NotificationManager notifications =
            (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notifications != null) notifications.cancel(ALERT_NOTIFICATION);
        return active || ringing;
    }

    public static synchronized long remainingSeconds(Context context) {
        SharedPreferences p = prefs(context);
        if (p.getLong(ID, 0) == 0L) return -1L;
        return TimerRules.remainingSeconds(p.getLong(END, 0L), System.currentTimeMillis());
    }

    /** Invalidates stale PendingIntents from cancelled/replaced timers. */
    static synchronized boolean consume(Context context, long id) {
        if (id == 0 || prefs(context).getLong(ID, 0L) != id) return false;
        return prefs(context).edit().remove(ID).remove(END).putLong(FINISHED, id).commit();
    }

    public static synchronized boolean consumeCompletion(Context context) {
        SharedPreferences p = prefs(context);
        boolean completed = p.getLong(FINISHED, 0) != 0;
        if (completed) p.edit().remove(FINISHED).apply();
        return completed;
    }

    static synchronized void restore(Context context) {
        Context c = context.getApplicationContext();
        SharedPreferences p = prefs(c);
        long id = p.getLong(ID, 0), end = p.getLong(END, 0);
        if (id == 0 || end == 0 || !canScheduleExact(c)) return;
        if (end > System.currentTimeMillis()) {
            try { scheduleSystemAlarm(c, id, end); }
            catch (RuntimeException ignored) { /* Retain state until user opens the app. */ }
        } else {
            // After reboot, media-playback foreground services may not be started from BOOT_COMPLETED.
            // Retain a completion marker for the next time the user opens Jarvis.
            p.edit().remove(ID).remove(END).putLong(FINISHED, id).apply();
        }
    }
}
