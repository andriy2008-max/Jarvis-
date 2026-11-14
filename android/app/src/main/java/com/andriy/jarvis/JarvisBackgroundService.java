package com.andriy.jarvis;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/** Keeps the phone companion process available to its Wear Data Layer receiver.
 * No microphone, overlay, polling loop or permanent wake lock. */
public final class JarvisBackgroundService extends Service {
    private static final String PREFS = "jarvis_background";
    private static final String CHANNEL = "jarvis_watch_connection";
    private static final int NOTIFICATION = 7810;
    private static volatile boolean running;

    public static boolean enabled(Context c) {
        return c.getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("enabled",true);
    }
    public static boolean isRunning() { return running; }
    public static void setEnabled(Context c,boolean enabled) {
        c.getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("enabled",enabled).commit();
        if (enabled) startIfEnabled(c);
        else c.stopService(new Intent(c,JarvisBackgroundService.class));
    }
    /** Called from visible UI and boot/update exemptions; denial is never hidden. */
    public static void startIfEnabled(Context c) {
        if (!enabled(c) || c.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)) return;
        // Persist first activation so reboot does not enable a never-opened install.
        c.getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("activated",true).commit();
        try {
            c.startForegroundService(new Intent(c,JarvisBackgroundService.class));
        } catch (RuntimeException denied) {
            c.getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString("error",
                "Android обмежив фоновий запуск. Відкрий Джарвіса й перевір використання батареї.").apply();
        }
    }
    public static void restore(Context c) {
        if (c.getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("activated",false)) startIfEnabled(c);
    }
    public static String status(Context c) {
        if (!enabled(c)) return "Постійна фонова робота вимкнена.";
        if (running) return "Фонова служба активна. Закриття вікна не зупиняє приймання команд годинника.";
        return c.getSharedPreferences(PREFS,MODE_PRIVATE).getString("error",
            "Служба ще не активна. Увімкни фонову роботу у відкритому Джарвісі.");
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if (!enabled(this) || getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            running = false;
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                "Зв’язок Джарвіса з годинником",NotificationManager.IMPORTANCE_LOW));
            Intent open = new Intent(this,MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent tap = PendingIntent.getActivity(this,NOTIFICATION,open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification notification = new Notification.Builder(this,CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Джарвіс активний у фоні")
                .setContentText("Готовий приймати команди годинника. Натисни, щоб відкрити.")
                .setContentIntent(tap).setOngoing(true).setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE).build();
            if (Build.VERSION.SDK_INT >= 29)
                startForeground(NOTIFICATION,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            else startForeground(NOTIFICATION,notification);
            running = true;
            getSharedPreferences(PREFS,MODE_PRIVATE).edit().remove("error").apply();
            // Null intent is Android restoring the service after process reclamation.
            return START_STICKY;
        } catch (RuntimeException denied) {
            running = false;
            getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString("error",
                "Не вдалося активувати фонову службу. Відкрий налаштування Джарвіса в Android.").apply();
            stopSelf();
            return START_NOT_STICKY;
        }
    }
    @Override public void onTaskRemoved(Intent rootIntent) {
        // Swiping Recents closes only the UI. Android owns sticky service restart.
        super.onTaskRemoved(rootIntent);
    }
    @Override public void onDestroy() {
        running = false;
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
