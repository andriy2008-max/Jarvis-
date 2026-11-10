package com.andriy.jarvis;

/**
 * Detects a deliberate double impulse rather than normal walking or a single
 * bump. Uses acceleration magnitude; orientation-independent, no raw samples
 * or motion history are persisted.
 */
public final class JarvisShakePattern {
    private static final float GRAVITY = 9.80665f;
    private static final float STRONG_PEAK_G = 2.35f;
    private static final float RELEASE_G = 1.55f;
    private static final long MIN_PEAK_SPACING_MS = 100;
    private static final long MAX_PEAK_SPACING_MS = 850;
    private static final long COOLDOWN_MS = 4500;

    private long firstPeakMs = -1;
    private long lastPeakMs = -1;
    private long cooldownUntilMs;
    private boolean released = true;

    public boolean onAcceleration(float x, float y, float z, long timeMs) {
        float forceG = (float) Math.sqrt(x * x + y * y + z * z) / GRAVITY;
        if (!Float.isFinite(forceG)) return false;
        if (timeMs < cooldownUntilMs) return false;
        if (forceG < RELEASE_G) {
            released = true;
            return false;
        }
        if (forceG < STRONG_PEAK_G || !released) return false;
        released = false;
        if (firstPeakMs >= 0 &&
            timeMs - firstPeakMs >= MIN_PEAK_SPACING_MS &&
            timeMs - firstPeakMs <= MAX_PEAK_SPACING_MS) {
            firstPeakMs = -1;
            lastPeakMs = timeMs;
            cooldownUntilMs = timeMs + COOLDOWN_MS;
            return true;
        }
        firstPeakMs = timeMs;
        lastPeakMs = timeMs;
        return false;
    }

    public void reset() {
        firstPeakMs = -1;
        lastPeakMs = -1;
        cooldownUntilMs = 0;
        released = true;
    }
}
