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
import android.util.Log;

/**
 * User-enabled foreground companion connection for Pixel <-> Galaxy Watch.
 *
 * The microphone is NOT used. WearableListenerService delivers user voice
 * commands on demand and JarvisWearPhoneAssistant runs phone-side APIs.
 * Keeping this process foreground protects asynchronous replies when the
 * MainActivity is dismissed; it does not bypass a user force-stop or Android
 * background activity/lock-screen restrictions.
 */
public final class JarvisCompanionService extends Service {
    private static final String TAG = "JARVIS_COMPANION";
    private static final String PREFS = "jarvis_companion_background_v1";
    private static final String ENABLED = "enabled";
    private static final String CHANNEL = "jarvis_watch_connection";
    private static final int NOTIFICATION_ID = 81743;
    private static final String ACTION_START = "com.andriy.jarvis.COMPANION_START";
    private static final String ACTION_STOP = "com.andriy.jarvis.COMPANION_STOP";
    private boolean foregroundStarted;

    private static boolean isWatch(Context context) {
        return context.getPackageManager().hasSystemFeature(
            PackageManager.FEATURE_WATCH);
    }

    /** Enabled by the owner's choice; never enabled on Wear OS itself. */
    public static boolean enabled(Context context) {
        return !isWatch(context) &&
            context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
                .getBoolean(ENABLED,true);
    }

    /** Must be initiated while the activity is visible, or by allowed boot delivery. */
    public static boolean startIfEnabled(Context context) {
        if (!enabled(context)) return false;
        Intent start = new Intent(context,JarvisCompanionService.class)
            .setAction(ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(start);
            else context.startService(start);
            return true;
        } catch (RuntimeException error) {
            Log.w(TAG,"Android did not allow starting the companion foreground service",error);
            // The manifest WearableListenerService still receives new events.
            return false;
        }
    }

    /** Explicit user control. Turn off in settings or from the notification. */
    public static boolean setEnabled(Context context, boolean value) {
        if (isWatch(context)) return false;
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .edit().putBoolean(ENABLED,value).apply();
        if (value) return startIfEnabled(context);
        try {
            context.stopService(new Intent(context,JarvisCompanionService.class));
        } catch (RuntimeException ignored) { }
        return true;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if (isWatch(this) || (intent != null && ACTION_STOP.equals(intent.getAction()))) {
            getSharedPreferences(PREFS,MODE_PRIVATE)
                .edit().putBoolean(ENABLED,false).apply();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!enabled(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            NotificationManager manager =
                (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) {
                NotificationChannel channel = new NotificationChannel(
                    CHANNEL,"Джарвіс — зв'язок із годинником",
                    NotificationManager.IMPORTANCE_LOW);
                channel.setDescription("Телефон приймає запити Galaxy Watch, навіть коли Джарвіс згорнуто.");
                manager.createNotificationChannel(channel);
            }
            Intent open = new Intent(this,MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent openUi = PendingIntent.getActivity(this,81743,open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Intent turnOff = new Intent(this,JarvisCompanionService.class)
                .setAction(ACTION_STOP);
            PendingIntent stop = PendingIntent.getService(this,81744,turnOff,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification notification = new Notification.Builder(this,CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Джарвіс активний")
                .setContentText("Приймаю команди з годинника. Мікрофон телефона вимкнений.")
                .setContentIntent(openUi)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(new Notification.Action.Builder(
                    null,"Вимкнути фон",stop).build())
                .build();
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID,notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            } else {
                startForeground(NOTIFICATION_ID,notification);
            }
            foregroundStarted = true;
            return START_STICKY;
        } catch (RuntimeException error) {
            Log.e(TAG,"Cannot start connected-device foreground service",error);
            stopSelf();
            return START_NOT_STICKY;
        }
    }

    @Override public void onDestroy() {
        foregroundStarted = false;
        super.onDestroy();
    }
}
