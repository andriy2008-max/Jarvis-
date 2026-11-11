package com.andriy.jarvis;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * Explicit voice-triggered Shazam launch and single center tap.
 * Uses only previously consented Accessibility, never root or silent permissions.
 * The song result remains in Shazam; we do not claim to read it.
 */
public final class SongRecognitionLauncher {
    private static final String SHAZAM = "com.shazam.android";
    private static final long POLL_MS = 400L;
    private static final long OPEN_TIMEOUT_MS = 10000L;
    private static final long SCREEN_SETTLE_MS = 1300L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static TapJob pending;

    public interface Listener {
        void onFinished(boolean success, String detail);
    }

    private SongRecognitionLauncher() {}

    public static synchronized String launch(Context context, Listener listener) {
        if (context == null)
            return "Не можу відкрити Shazam без доступу до телефона.";
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH))
            return "Shazam запускається на телефоні. Скажи «Шазам» Джарвісу на телефоні.";
        if (pending != null)
            return "Shazam уже відкривається, очікую запуску розпізнавання.";
        Intent app = context.getPackageManager().getLaunchIntentForPackage(SHAZAM);
        if (app == null)
            return "Shazam не встановлений або вимкнений. Установи чи ввімкни Shazam.";
        JarvisPhoneModeManager.Lease lease = JarvisPhoneModeManager.acquireForCommand(context);
        try {
            context.startActivity(app.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException unavailable) {
            JarvisPhoneModeManager.releaseForCommand(lease);
            return "Android не дозволив відкрити Shazam.";
        }
        if (!lease.granted)
            return "Shazam відкрито, але автоматичне натискання недоступне: " +
                lease.message + " Натисни центральну кнопку вручну.";
        TapJob job = new TapJob(lease, listener);
        pending = job;
        MAIN.postDelayed(job, POLL_MS);
        return "Відкриваю Shazam та очікую центральну кнопку розпізнавання.";
    }

    private static final class TapJob implements Runnable {
        private final JarvisPhoneModeManager.Lease lease;
        private final Listener listener;
        private final long deadline = SystemClock.elapsedRealtime() + OPEN_TIMEOUT_MS;
        private long visibleSince;

        private TapJob(JarvisPhoneModeManager.Lease lease, Listener listener) {
            this.lease = lease;
            this.listener = listener;
        }

        @Override public void run() {
            synchronized (SongRecognitionLauncher.class) {
                if (pending != this) return;
            }
            if (!ScreenControlAccessibilityService.isSessionActive()) {
                finish(false, "Керування екраном було вимкнене до натискання Shazam.");
                return;
            }
            long now = SystemClock.elapsedRealtime();
            if (!ScreenControlAccessibilityService.shazamVisible()) {
                visibleSince = 0L;
                if (now >= deadline) {
                    finish(false, "Shazam не показав головний екран. " +
                        "Перевір дозволи та натисни кнопку вручну.");
                } else MAIN.postDelayed(this, POLL_MS);
                return;
            }
            if (visibleSince == 0L) visibleSince = now;
            if (now - visibleSince < SCREEN_SETTLE_MS) {
                if (now >= deadline) {
                    finish(false, "Екран Shazam не встиг завантажитися.");
                } else MAIN.postDelayed(this, POLL_MS);
                return;
            }
            // Exactly one gesture, only if Shazam is still the foreground app.
            ScreenControlAccessibilityService.tapShazamCenter((success, detail) ->
                finish(success, success
                    ? "Натиснув центральну кнопку Shazam. Результат розпізнавання буде в Shazam."
                    : "Не вдалося натиснути кнопку Shazam: " + detail));
        }

        private void finish(boolean success, String detail) {
            synchronized (SongRecognitionLauncher.class) {
                if (pending != this) return;
                pending = null;
            }
            MAIN.removeCallbacks(this);
            JarvisPhoneModeManager.releaseForCommand(lease, success);
            if (listener != null) listener.onFinished(success, detail);
        }
    }
}
