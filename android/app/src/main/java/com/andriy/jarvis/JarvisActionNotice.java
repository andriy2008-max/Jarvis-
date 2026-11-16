package com.andriy.jarvis;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Toast;

/** One replaceable bottom status for phone, overlay and watch commands. */
public final class JarvisActionNotice {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static String last = "";
    private static long shownAt;
    private static Toast fallback;
    private JarvisActionNotice() {}
    public static void show(Context context, String message) {
        if (context == null || message == null || message.trim().isEmpty()) return;
        Context app = context.getApplicationContext();
        MAIN.post(() -> {
            long now = SystemClock.elapsedRealtime();
            if (message.equals(last) && now - shownAt < 1200L) return;
            last = message; shownAt = now;
            if (ScreenControlAccessibilityService.showActionNotice(message)) return;
            if (fallback != null) fallback.cancel();
            fallback = Toast.makeText(app, message, Toast.LENGTH_LONG);
            fallback.show();
        });
    }
}
