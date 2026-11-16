package com.andriy.jarvis;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.view.Display;
import android.view.WindowManager;
import java.util.function.Supplier;

/** Resolve the phone's primary screen from a non-visual Service context.
 * Context.getDisplay() can throw before a null fallback is reached on Android 11+.
 * Do not invent dimensions or select an arbitrary external display.
 */
public final class JarvisServiceDisplay {
    private JarvisServiceDisplay() {}
    public static Display resolve(Context context) {
        if (context == null) return null;
        return firstAvailable(() -> {
            DisplayManager manager=(DisplayManager)context.getSystemService(Context.DISPLAY_SERVICE);
            return manager==null ? null : manager.getDisplay(Display.DEFAULT_DISPLAY);
        }, () -> {
            WindowManager manager=(WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
            return manager==null ? null : manager.getDefaultDisplay();
        });
    }
    static <T> T firstAvailable(Supplier<T> primary,Supplier<T> fallback) {
        try {T value=primary.get();if(value!=null)return value;}
        catch(RuntimeException unavailable) { /* OEM service unavailable; try documented fallback. */ }
        try {return fallback.get();}
        catch(RuntimeException unavailable) {return null;}
    }
}
