package com.andriy.jarvis;

/**
 * A short visible tail of a live voice recognition hypothesis.
 * No logging, disk persistence, network transmission or extra recognition.
 */
public final class JarvisOrbTranscript {
    public static final int MAX_VISIBLE_CODEPOINTS = 52;

    private JarvisOrbTranscript() {}

    public static String visibleTail(String recognized) {
        if (recognized == null) return "";
        String normalized = recognized.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) return "";
        int points = normalized.codePointCount(0, normalized.length());
        if (points <= MAX_VISIBLE_CODEPOINTS) return normalized;
        int offset = normalized.offsetByCodePoints(
            normalized.length(), -MAX_VISIBLE_CODEPOINTS);
        String suffix = normalized.substring(offset);
        // Avoid starting at the middle of a word when a full trailing word exists.
        int space = suffix.indexOf(' ');
        if (space > 0 && space < 12 && space + 1 < suffix.length())
            suffix = suffix.substring(space + 1);
        return "…" + suffix;
    }
}
