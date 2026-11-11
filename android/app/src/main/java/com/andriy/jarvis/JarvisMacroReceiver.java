package com.andriy.jarvis;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * System-issued power and clock events only; never accepts external app intents.
 * goAsync keeps the process eligible to finish short, background-safe automations.
 * Longer workflows need a separately user-approved foreground execution path.
 */
public final class JarvisMacroReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
            Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) ||
            Intent.ACTION_TIME_CHANGED.equals(action) ||
            Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
            JarvisMacros.scheduleAll(context);
            return;
        }
        if (!"com.andriy.jarvis.MACRO_TIME".equals(action) &&
            !Intent.ACTION_POWER_CONNECTED.equals(action) &&
            !Intent.ACTION_POWER_DISCONNECTED.equals(action)) return;

        PendingResult pending = goAsync();
        AtomicBoolean closed = new AtomicBoolean(false);
        Runnable finish = () -> {
            if (closed.compareAndSet(false, true)) pending.finish();
        };
        // A manifest receiver cannot safely hold a process alive indefinitely.
        new Handler(Looper.getMainLooper()).postDelayed(finish, 8500L);
        JarvisMacros.Listener listener = result -> finish.run();
        if ("com.andriy.jarvis.MACRO_TIME".equals(action)) {
            String id = intent.getStringExtra("macro_id");
            if (id == null) finish.run();
            else JarvisMacros.handleTime(context, id, listener);
        } else if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
            JarvisMacros.handleEvent(context, "charger_on", listener);
            finish.run();
        } else {
            JarvisMacros.handleEvent(context, "charger_off", listener);
            finish.run();
        }
    }
}
