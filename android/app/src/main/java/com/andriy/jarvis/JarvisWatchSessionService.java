package com.andriy.jarvis;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

/**
 * User-started Wear OS session that survives MainActivity recreation,
 * normal backgrounding and the operating system removing the UI task.
 *
 * This service does NOT record audio, hold wake locks, launch an Activity
 * from the background or dispatch phone actions. The user may return to
 * Jarvis using the Wear OS ongoing notification. Android may still stop a
 * service for policy/resource reasons; START_STICKY is not a guarantee of
 * uninterrupted operation or auto-resurrection after a force-stop.
 */
public final class JarvisWatchSessionService extends Service {
    private static final String TAG="JARVIS_WATCH_LIFECYCLE";
    private static final String PREFS="jarvis_watch_session_v1";
    private static final String KEY_ENABLED="enabled";
    private static final String START="com.andriy.jarvis.WATCH_SESSION_START";
    static final String ACTION_STOP="com.andriy.jarvis.WATCH_SESSION_STOP";
    private static final int ID=81748;

    private static boolean isWatch(Context c) {
        return c.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
    }

    private static boolean enabled(Context c) {
        return c.getSharedPreferences(PREFS,MODE_PRIVATE)
            .getBoolean(KEY_ENABLED,false);
    }

    /** Only call while the user visibly opened Jarvis on the watch. */
    static void startFromVisibleWatch(Context c) {
        if(!isWatch(c))return;
        c.getSharedPreferences(PREFS,MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED,true).apply();
        Intent i=new Intent(c,JarvisWatchSessionService.class).setAction(START);
        try {
            if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);
            else c.startService(i);
        } catch(RuntimeException error) {
            Log.w(TAG,"Watch session foreground service could not start",error);
            // The visible Activity and its existing functionality still work.
        }
    }

    /** Explicit owner choice; never called as a result of a voice reply. */
    static void stopByOwner(Context c) {
        c.getSharedPreferences(PREFS,MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED,false).apply();
        try {
            c.startService(new Intent(c,JarvisWatchSessionService.class).setAction(ACTION_STOP));
        } catch(RuntimeException ignored) {
            try {c.stopService(new Intent(c,JarvisWatchSessionService.class));}
            catch(RuntimeException alsoIgnored){}
            JarvisWatchOngoing.hide(c);
        }
    }

    @Override public IBinder onBind(Intent intent){return null;}

    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(!isWatch(this) || !enabled(this) ||
            (intent!=null && ACTION_STOP.equals(intent.getAction()))) {
            getSharedPreferences(PREFS,MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED,false).apply();
            JarvisWatchOngoing.hide(this);
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            Notification ongoing=JarvisWatchOngoing.notificationForService(this);
            if(Build.VERSION.SDK_INT>=29)
                startForeground(ID,ongoing,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            else startForeground(ID,ongoing);
            Log.i(TAG,"Watch foreground session active; no microphone in service");
            return START_STICKY;
        } catch(RuntimeException e) {
            Log.e(TAG,"Cannot keep watch session foreground",e);
            stopSelf();
            return START_NOT_STICKY;
        }
    }
}
