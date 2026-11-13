package com.andriy.jarvis;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Відновлення лише раніше ввімкненого приймача після завантаження або оновлення. */
public final class JarvisPhoneCompanionRestoreReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent != null && (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) ||
            Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())))
            JarvisPhoneCompanionService.start(context);
    }
}
