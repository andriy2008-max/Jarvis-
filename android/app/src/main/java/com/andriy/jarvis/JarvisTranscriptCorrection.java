package com.andriy.jarvis;

/** Unicode-safe helpers for correcting the last spoken word. */
public final class JarvisTranscriptCorrection {
    private JarvisTranscriptCorrection() {}

    public static String join(String saved, String partial) {
        String prefix = saved == null ? "" : saved.trim();
        String suffix = partial == null ? "" : partial.trim();
        if (prefix.isEmpty()) return suffix;
        if (suffix.isEmpty()) return prefix;
        return prefix + " " + suffix;
    }

    /** Remove precisely one final whitespace-delimited word, punctuation included. */
    public static String removeLastWord(String text) {
        if (text == null) return "";
        String normalized = text.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) return "";
        int space = normalized.lastIndexOf(' ');
        return space < 0 ? "" : normalized.substring(0, space);
    }

    public static boolean isLeftSwipe(float dx, float dy, float minDistance) {
        return dx <= -minDistance && -dx >= Math.abs(dy) * 1.30f;
    }
}
