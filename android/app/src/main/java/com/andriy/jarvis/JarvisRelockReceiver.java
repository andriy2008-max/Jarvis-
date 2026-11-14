package com.andriy.jarvis;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Fallback alarm after a user-authorized rooted one-time unlock.
 * No PIN or other credential is carried in this system broadcast.
 */
public final class JarvisRelockReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        // Root shell may take a moment; finish the broadcast after the lock.
        final PendingResult pending=goAsync();
        new Thread(() -> {
            try { JarvisRemoteUnlock.fallbackRelock(context.getApplicationContext()); }
            finally { pending.finish(); }
        },"Jarvis-relock-failsafe").start();
    }
}
