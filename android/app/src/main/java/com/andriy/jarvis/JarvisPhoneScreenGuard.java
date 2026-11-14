package com.andriy.jarvis;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.PowerManager;

/** Never wakes or bypasses the user's lock screen. */
public final class JarvisPhoneScreenGuard {
    public static final String MESSAGE = "Розблокуй телефон і ввімкни екран, потім повтори команду.";
    private JarvisPhoneScreenGuard() {}
    public static boolean available(Context context) {
        if (context == null) return false;
        KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
        PowerManager power = context.getSystemService(PowerManager.class);
        return keyguard != null && power != null && power.isInteractive() &&
            !keyguard.isDeviceLocked() && !keyguard.isKeyguardLocked();
    }
}
