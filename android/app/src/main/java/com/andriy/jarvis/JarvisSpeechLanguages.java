package com.andriy.jarvis;

/** SpeechRecognizer language preferences, independent of Android APIs.
 * AUTO prefers Ukrainian and requests bilingual detection/switching on API 34+.
 * Providers may ignore language switching, so explicit UA/RU choices remain.
 */
public final class JarvisSpeechLanguages {
    public static final String PREF = "jarvis_speech_language";
    public static final String AUTO = "auto";
    public static final String UKRAINIAN = "uk";
    public static final String RUSSIAN = "ru";

    private JarvisSpeechLanguages() {}

    public static String mode(String value) {
        return UKRAINIAN.equals(value) || RUSSIAN.equals(value) ? value : AUTO;
    }

    public static String initialLanguage(String value) {
        return RUSSIAN.equals(mode(value)) ? "ru-RU" : "uk-UA";
    }

    public static boolean automatic(String value) {
        return AUTO.equals(mode(value));
    }

    public static String label(String value) {
        switch(mode(value)) {
            case UKRAINIAN: return "Українська";
            case RUSSIAN: return "Російська";
            default: return "Авто UA/RU";
        }
    }
}
