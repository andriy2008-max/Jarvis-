package com.andriy.jarvis;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Normal boot after first unlock, or an in-place update; never bypass force-stop. */
public final class JarvisBackgroundRestoreReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        if (intent != null && (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) ||
                Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())))
            JarvisBackgroundService.restore(context);
    }
}
