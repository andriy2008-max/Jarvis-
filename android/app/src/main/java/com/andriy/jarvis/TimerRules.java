package com.andriy.jarvis;

/** Platform-free timer arithmetic and duration limits. */
public final class TimerRules {
    private TimerRules() {}
    public static final int MAX_SECONDS = 3 * 60 * 60;
    public static boolean valid(int seconds) {
        return seconds >= 1 && seconds <= MAX_SECONDS;
    }
    public static long deadline(long nowWallMs, int seconds) {
        if (!valid(seconds)) throw new IllegalArgumentException("timer duration");
        return Math.addExact(nowWallMs, seconds * 1000L);
    }
    public static long remainingSeconds(long deadlineWallMs, long nowWallMs) {
        if (deadlineWallMs <= nowWallMs) return 0;
        return (deadlineWallMs - nowWallMs + 999L) / 1000L;
    }
}
