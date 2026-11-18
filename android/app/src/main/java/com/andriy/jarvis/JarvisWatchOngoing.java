package com.andriy.jarvis;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.wear.ongoing.OngoingActivity;

/**
 * Wear OS 5+ ongoing session while the owner has opened Jarvis on the watch.
 *
 * The system is free to dim the display and honor a deliberate Home press.
 * OngoingActivity prevents the routine idle timeout from losing the current
 * task and exposes an icon on the watch face to return to Jarvis.
 * It does not keep the microphone recording in the background.
 */
final class JarvisWatchOngoing {
    private static final String TAG = "JARVIS_WATCH_LIFECYCLE";
    private static final String CHANNEL = "jarvis_watch_active_session";
    private static final int ID = 81748;
    private JarvisWatchOngoing() {}

    static void show(Context context) {
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH))
            return;
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Ongoing session unavailable: notification permission denied");
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        try {
            manager.createNotificationChannel(new NotificationChannel(
                CHANNEL, "Дімон — активний сеанс", NotificationManager.IMPORTANCE_LOW));

            Intent launch = new Intent(context, MainActivity.class);
            launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent resume = PendingIntent.getActivity(context, ID, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            NotificationCompat.Builder notification = new NotificationCompat.Builder(
                context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Дімон активний на годиннику")
                .setContentText("Натисни, щоб повернутися до Дімона")
                .setContentIntent(resume)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);
            OngoingActivity ongoing = new OngoingActivity.Builder(context, ID, notification)
                .setStaticIcon(R.drawable.ic_launcher)
                .setTouchIntent(resume)
                .build();
            ongoing.apply(context);
            manager.notify(ID, notification.build());
            Log.i(TAG, "Ongoing Wear OS Jarvis session posted");
        } catch (RuntimeException problem) {
            // The assistant must still work if notifications are disabled.
            Log.w(TAG, "Cannot show ongoing Wear OS activity", problem);
        }
    }

    static void hide(Context context) {
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH))
            return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(ID);
    }
}
