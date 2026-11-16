package com.andriy.jarvis;

import android.Manifest;
import android.app.Notification;
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

    /**
     * Build an actual connected-device foreground-service notification.
     * The OngoingActivity must be owned by the service, not by MainActivity
     * (whose destruction previously removed the return-to-Jarvis affordance).
     */
    static Notification notificationForService(Context context) {
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        if(manager!=null) {
            manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                "Джарвіс — активний сеанс",NotificationManager.IMPORTANCE_LOW));
        }
        Intent launch=new Intent(context,MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent resume=PendingIntent.getActivity(context,ID,launch,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent end=new Intent(context,JarvisWatchSessionService.class)
            .setAction(JarvisWatchSessionService.ACTION_STOP);
        PendingIntent stop=PendingIntent.getService(context,ID+1,end,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder=new NotificationCompat.Builder(context,CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Джарвіс активний на Watch7")
            .setContentText("Повернутися до Джарвіса")
            .setContentIntent(resume)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_pause,"Завершити Джарвіса",stop);
        if(Build.VERSION.SDK_INT<33 ||
           context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==
                PackageManager.PERMISSION_GRANTED) {
            try {
                OngoingActivity ongoing=new OngoingActivity.Builder(context,ID,builder)
                    .setStaticIcon(R.drawable.ic_launcher)
                    .setTouchIntent(resume)
                    .build();
                ongoing.apply(context);
            } catch(RuntimeException failure) {
                Log.w(TAG,"Wear ongoing UI unavailable; FGS notification remains",failure);
            }
        }
        return builder.build();
    }

    /** Re-publish after the owner grants notifications. */
    static void show(Context context) {
        if(!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH))
            return;
        if(Build.VERSION.SDK_INT>=33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=
                PackageManager.PERMISSION_GRANTED)return;
        NotificationManager nm=context.getSystemService(NotificationManager.class);
        if(nm!=null)try {
            nm.notify(ID,notificationForService(context));
        } catch(RuntimeException err) {
            Log.w(TAG,"Failed to re-publish Watch ongoing notification",err);
        }
    }

    static void hide(Context context) {
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH))
            return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(ID);
    }
}
