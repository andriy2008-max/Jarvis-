package com.andriy.jarvis;

import java.util.Locale;

/** Language-first best effort voice selection. Android Voice exposes NO gender metadata. */
public final class VoiceRanking {
    private VoiceRanking() {}

    public static boolean ukrainian(String languageTag) {
        if (languageTag == null) return false;
        String value = languageTag.toLowerCase(Locale.ROOT).replace('_', '-');
        return value.equals("uk") || value.startsWith("uk-");
    }

    private static boolean token(String name, String token) {
        if (name == null) return false;
        String n = name.toLowerCase(Locale.ROOT);
        // Android engines are not required to label gender in the voice name.
        return n.matches(".*(?:^|[\\\\W_])" + token + "(?:[\\\\W_]|$).*");
    }

    public static boolean labeledMale(String voiceName) {
        return !token(voiceName, "female") && !token(voiceName, "woman") &&
            (token(voiceName, "male") || token(voiceName, "man") ||
             token(voiceName, "masculine") || token(voiceName, "чоловічий"));
    }

    public static int score(String languageTag, String voiceName,
                            boolean networkRequired, int quality) {
        if (!ukrainian(languageTag)) return Integer.MIN_VALUE;
        int score = 100 + quality;
        if (languageTag != null &&
            languageTag.replace('_', '-').equalsIgnoreCase("uk-UA")) score += 50;
        if (!networkRequired) score += 30;
        if (labeledMale(voiceName)) score += 10_000;
        if (token(voiceName, "female") || token(voiceName, "woman")) score -= 500;
        return score;
    }
}
