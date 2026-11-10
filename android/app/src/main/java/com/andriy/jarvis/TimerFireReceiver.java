package com.andriy.jarvis;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** AlarmManager delivers this explicit receiver even when the UI process was terminated. */
public final class TimerFireReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !TimerScheduler.FIRE.equals(intent.getAction())) return;
        long id = intent.getLongExtra("timer_id", 0);
        if (!TimerScheduler.consume(context, id)) return;
        Intent alert = new Intent(context, TimerAlertService.class);
        alert.setAction(TimerAlertService.START);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(alert);
            } else {
                context.startService(alert);
            }
        } catch (RuntimeException error) {
            // Foreground-service restrictions and some OEMs may block service starts:
            // still show a visible, audible notification and vibration when allowed.
            TimerAlertService.postFallback(context);
        }
    }
}
