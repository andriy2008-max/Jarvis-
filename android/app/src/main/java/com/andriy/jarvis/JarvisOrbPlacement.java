package com.andriy.jarvis;

/** Pure-Java screen-safe placement math for the movable Jarvis overlay. */
public final class JarvisOrbPlacement {
    private JarvisOrbPlacement() {}

    public static int clamp(int requested, int minimum, int maximum) {
        int high = Math.max(minimum, maximum);
        return Math.max(minimum, Math.min(high, requested));
    }

    public static float fraction(int location, int maximum) {
        if (maximum <= 0) return 0f;
        return clamp(location, 0, maximum) / (float) maximum;
    }

    public static int fromFraction(float fraction, int maximum) {
        if (!Float.isFinite(fraction)) fraction = 0.5f;
        return clamp(Math.round(Math.max(0f, Math.min(1f, fraction)) *
            Math.max(0, maximum)), 0, Math.max(0, maximum));
    }

    public static int supportedSize(int requestedDp) {
        return requestedDp == 96 || requestedDp == 112 || requestedDp == 144
            ? requestedDp : 112;
    }
}
