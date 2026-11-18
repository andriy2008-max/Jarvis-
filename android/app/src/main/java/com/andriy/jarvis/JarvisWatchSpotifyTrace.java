package com.andriy.jarvis;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;

/** Minimal crash-surviving on-device diagnostics for Watch7 Spotify control.
 * Records only a fixed stage name, timestamp and status. Never a song title,
 * search query, transcription, token, account or accessibility node.
 */
public final class JarvisWatchSpotifyTrace {
    private static final String PREFS="dimon_watch_spotify_trace_v1";
    private static final String TAG="DIMON_WATCH_SPOTIFY";
    private JarvisWatchSpotifyTrace() {}

    public static void mark(Context context,String stage) {
        if(context==null || stage==null)return;
        if(!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH))
            return;
        // Only internal fixed stage strings may be stored.
        if(!stage.matches("[A-Z0-9_]{2,48}"))return;
        try {
            SharedPreferences p=context.getApplicationContext()
                .getSharedPreferences(PREFS,Context.MODE_PRIVATE);
            // Commit, not apply, so a sudden process exit does not lose
            // the last stage reached immediately before it.
            p.edit().putString("stage",stage)
                .putLong("at",System.currentTimeMillis()).commit();
            Log.i(TAG,"stage="+stage);
        } catch(RuntimeException e) {
            Log.w(TAG,"Unable to save diagnostic stage",e);
        }
    }

    public static String report(Context context) {
        if(context==null)return "Діагностика Spotify недоступна.";
        try {
            SharedPreferences p=context.getApplicationContext()
                .getSharedPreferences(PREFS,Context.MODE_PRIVATE);
            String stage=p.getString("stage","");
            if(stage==null || stage.isEmpty())
                return "Spotify ще не запускався з Дімона після встановлення цієї версії.";
            long at=p.getLong("at",0L);
            long seconds=at>0?Math.max(0L,
                (System.currentTimeMillis()-at)/1000L):-1L;
            return "Останній етап Spotify: "+stage+
                (seconds>=0?", "+seconds+" с тому.":".");
        } catch(RuntimeException e) {
            return "Не вдалося прочитати діагностику Spotify.";
        }
    }
}
