package com.andriy.jarvis;

import android.Manifest;
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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import com.google.android.gms.wearable.Wearable;

/** Утримує процес приймача Data Layer, коли вікно телефона закрите. */
public final class JarvisPhoneCompanionService extends Service {
    private static final String PREFS = "jarvis_phone_companion";
    private static final String CHANNEL = "jarvis_watch_connection";
    private static final int NOTICE = 7841;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean stopped;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (stopped) return;
            Wearable.getNodeClient(JarvisPhoneCompanionService.this).getConnectedNodes()
                .addOnSuccessListener(nodes -> update(nodes.isEmpty()
                    ? "Очікую з'єднання з годинником"
                    : ScreenControlAccessibilityService.isConnected()
                        ? "Годинник підключено. Приймання команд активне"
                        : "Годинник підключено. Увімкни керування екраном"))
                .addOnFailureListener(error -> update("Не вдалося перевірити зв'язок із годинником"));
            main.postDelayed(this, 30000L);
        }
    };

    public static boolean permitted(Context c) {
        return Build.VERSION.SDK_INT < 31 || c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
            == PackageManager.PERMISSION_GRANTED;
    }
    public static boolean enabled(Context c) {
        return c.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("enabled", false);
    }
    public static void enable(Context c) {
        c.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("enabled", true).apply();
    }
    public static boolean start(Context c) {
        if (!enabled(c) || !permitted(c) ||
            c.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)) return false;
        try {
            c.startForegroundService(new Intent(c, JarvisPhoneCompanionService.class));
            return true;
        } catch (RuntimeException denied) {
            // Android може заборонити фоновий запуск. Наступне відкриття повторить його.
            return false;
        }
    }
    private Notification notice(String text) {
        PendingIntent open = PendingIntent.getActivity(this, NOTICE,
            new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher).setContentTitle("Джарвіс — зв'язок із годинником")
            .setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text))
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE).build();
    }
    private void update(String text) {
        if (!stopped) getSystemService(NotificationManager.class).notify(NOTICE, notice(text));
    }
    @Override public void onCreate() {
        super.onCreate();
        getSystemService(NotificationManager.class).createNotificationChannel(
            new NotificationChannel(CHANNEL, "Команди з годинника", NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!permitted(this) || !enabled(this) ||
            getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            stopSelf(); return START_NOT_STICKY;
        }
        try {
            Notification n = notice("Приймач запущений. Перевіряю зв'язок…");
            if (Build.VERSION.SDK_INT >= 29)
                startForeground(NOTICE, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            else startForeground(NOTICE, n);
        } catch (RuntimeException denied) {
            stopSelf(); return START_NOT_STICKY;
        }
        main.removeCallbacks(refresh);
        main.post(refresh);
        return START_STICKY;
    }
    @Override public void onDestroy() {
        stopped = true;
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
