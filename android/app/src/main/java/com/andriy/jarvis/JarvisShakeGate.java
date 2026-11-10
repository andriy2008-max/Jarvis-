package com.andriy.jarvis;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Two-stage voice gate: motion opens a short wake-only window;
 * after hearing "Джарвіс", local commands are allowed, but AI demands a
 * second explicit "Джарвіс" (in the request or as a standalone utterance).
 */
public final class JarvisShakeGate {
    private static final Pattern WAKE_WORD = Pattern.compile(
        "(?iu)(?<![\\p{L}\\p{N}])(?:джарвіс|джарвис|jarvis)(?![\\p{L}\\p{N}])");
    private JarvisShakeGate() {}

    public static boolean containsWakeWord(String phrase) {
        return phrase != null && WAKE_WORD.matcher(phrase).find();
    }
    public static boolean isWakeOnly(String phrase) {
        if (phrase == null) return false;
        String p = phrase.trim().toLowerCase(Locale.forLanguageTag("uk-UA"))
            .replaceAll("[!?.,;:—–]+", " ").replaceAll("\\s+", " ").trim();
        return "джарвіс".equals(p) || "джарвис".equals(p) ||
            "jarvis".equals(p);
    }
    public static boolean aiRequested(String phrase) {
        return JarvisSearchRouting.addressedToJarvis(phrase);
    }
}
