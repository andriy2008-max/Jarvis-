package com.andriy.jarvis;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Re-register a future AlarmManager event after reboot or app upgrade. */
public final class TimerRestoreReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
            Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            TimerScheduler.restore(context);
            JarvisWearAlarmScheduler.restore(context);
            // On boot or APK replacement, restore the phone-side companion
            // connection only if the owner left it enabled. Android can still
            // reject background FGS start; WearableListenerService is the fallback.
            JarvisCompanionService.startIfEnabled(context);
        }
    }
}
