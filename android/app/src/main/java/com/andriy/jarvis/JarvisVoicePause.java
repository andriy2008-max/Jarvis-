package com.andriy.jarvis;

import java.util.Locale;

/**
 * Last-recognized-word inactivity detector. No countdown exists until a word
 * is recognized, and repeated identical partials do not extend the deadline.
 * Reused by main/overlay recognizers even when Android ends a speech segment.
 */
public final class JarvisVoicePause {
    public static final String PREFS = "jarvis_voice_input";
    public static final String KEY_SECONDS = "pause_after_words_seconds";
    public static final int DEFAULT_SECONDS = 2;
    public static final int MIN_SECONDS = 1;
    public static final int MAX_SECONDS = 10;

    private String observed = "";
    private long lastWordAtMs = -1L;

    public static int seconds(int value) {
        return Math.max(MIN_SECONDS, Math.min(MAX_SECONDS, value));
    }

    /** Set existing text as baseline without starting a countdown (swipe correction). */
    public void reset(String baseline) {
        observed = normalize(baseline);
        lastWordAtMs = -1L;
    }

    public boolean sawWords(String text, long timeMs) {
        String cleaned = normalize(text);
        if (cleaned.isEmpty() || cleaned.equals(observed)) return false;
        observed = cleaned;
        lastWordAtMs = timeMs;
        return true;
    }

    public void sawVoice(long timeMs) {
        if (armed()) lastWordAtMs = Math.max(lastWordAtMs, timeMs);
    }

    public boolean armed() { return lastWordAtMs >= 0; }
    public long remainingMs(long nowMs, int seconds) {
        if (!armed()) return Long.MAX_VALUE;
        return Math.max(0L, lastWordAtMs + 1000L * seconds(seconds) - nowMs);
    }
    public boolean expired(long nowMs, int seconds) {
        return armed() && remainingMs(nowMs, seconds) == 0L;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String s = value.toLowerCase(Locale.forLanguageTag("uk-UA"))
            .replaceAll("[\\p{Punct}«»—–]+", " ")
            .replaceAll("\\s+", " ").trim();
        return s.matches(".*[\\p{L}\\p{N}].*") ? s : "";
    }
}
