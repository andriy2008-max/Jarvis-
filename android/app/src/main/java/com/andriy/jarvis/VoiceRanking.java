package com.andriy.jarvis;

import java.util.Locale;

/** Ukrainian system TTS ranking; Android does not expose voice gender metadata. */
public final class VoiceRanking {
    public static final String DMYTRO = "dmytro";
    public static final String LADA = "lada";

    private VoiceRanking() {}

    public static boolean ukrainian(String languageTag) {
        if (languageTag == null) return false;
        String value = languageTag.toLowerCase(Locale.ROOT).replace('_', '-');
        return value.equals("uk") || value.startsWith("uk-");
    }

    private static boolean token(String name, String token) {
        if (name == null) return false;
        String normalized = name.toLowerCase(Locale.ROOT);
        return ("-" + normalized.replaceAll("[^\\p{L}]+", "-") + "-")
            .contains("-" + token + "-");
    }

    public static boolean labeledMale(String voiceName) {
        return !token(voiceName, "female") && !token(voiceName, "woman") &&
            (token(voiceName, "male") || token(voiceName, "man") ||
             token(voiceName, "masculine") || token(voiceName, "чоловічий"));
    }

    public static String profile(String candidate) {
        return LADA.equalsIgnoreCase(candidate) ? LADA : DMYTRO;
    }

    /** Only treat a voice as a named profile when its Android TTS name identifies it. */
    public static boolean matchesProfile(String voiceName, String wantedProfile) {
        if (voiceName == null) return false;
        String name = voiceName.toLowerCase(Locale.ROOT);
        if (LADA.equals(profile(wantedProfile))) {
            return name.contains("lada") || name.contains("лада");
        }
        return name.contains("dmytro") || name.contains("dmitro") ||
            name.contains("дмитро");
    }

    public static int score(String languageTag, String voiceName,
                            boolean networkRequired, int quality) {
        if (!ukrainian(languageTag)) return Integer.MIN_VALUE;
        int score = 100 + quality;
        if (languageTag != null &&
            languageTag.replace('_', '-').equalsIgnoreCase("uk-UA")) score += 50;
        if (!networkRequired) score += 30;
        if (labeledMale(voiceName)) score += 10_000;
        if (matchesProfile(voiceName, DMYTRO)) score += 20_000;
        if (matchesProfile(voiceName, LADA)) score += 19_000;
        if (token(voiceName, "female") || token(voiceName, "woman")) score -= 500;
        return score;
    }
}
