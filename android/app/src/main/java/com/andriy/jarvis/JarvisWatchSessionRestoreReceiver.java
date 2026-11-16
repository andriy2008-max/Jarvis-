package com.andriy.jarvis;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Restore the owner's manually enabled Wear OS ongoing session after reboot
 * or an app update. Never records audio or opens an Activity in background.
 * Android may defer a foreground-service start for power reasons.
 */
public final class JarvisWatchSessionRestoreReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        if(intent==null)return;
        String action=intent.getAction();
        if(!Intent.ACTION_BOOT_COMPLETED.equals(action) &&
           !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action))return;
        JarvisWatchSessionService.restoreIfPreviouslyEnabled(context);
    }
}
