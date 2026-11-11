package com.andriy.jarvis;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** System-issued power and clock events only; never accepts external app intents. */
public final class JarvisMacroReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if ("com.andriy.jarvis.MACRO_TIME".equals(action)) {
            String id = intent.getStringExtra("macro_id");
            if (id != null) JarvisMacros.handleTime(context, id);
        } else if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
            JarvisMacros.handleEvent(context, "charger_on", null);
        } else if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
            JarvisMacros.handleEvent(context, "charger_off", null);
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                   Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) ||
                   Intent.ACTION_TIME_CHANGED.equals(action) ||
                   Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
            JarvisMacros.scheduleAll(context);
        }
    }
}
