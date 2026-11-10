package com.andriy.jarvis;

/** Recovery decisions for Android SpeechRecognizer; all constant values match
 * android.speech.SpeechRecognizer. Pure Java so regression tests run without
 * an Android device.
 */
public final class JarvisSpeechErrorPolicy {
    private JarvisSpeechErrorPolicy() {}

    public static final int NETWORK_TIMEOUT = 1;
    public static final int NETWORK = 2;
    public static final int AUDIO = 3;
    public static final int SERVER = 4;
    public static final int CLIENT = 5;
    public static final int SPEECH_TIMEOUT = 6;
    public static final int NO_MATCH = 7;
    public static final int BUSY = 8;
    public static final int PERMISSION = 9;
    public static final int TOO_MANY_REQUESTS = 10;
    public static final int SERVER_DISCONNECTED = 11;
    public static final int LANGUAGE_UNSUPPORTED = 12;
    public static final int LANGUAGE_UNAVAILABLE = 13;

    public static boolean lostConnection(int code) {
        return code == SERVER_DISCONNECTED || code == CLIENT ||
            code == BUSY || code == TOO_MANY_REQUESTS;
    }

    public static boolean rebuildRecognizer(int code) {
        return code == SERVER_DISCONNECTED || code == CLIENT || code == SERVER;
    }

    public static boolean isSilent(int code) {
        return code == NO_MATCH || code == SPEECH_TIMEOUT;
    }

    public static boolean needsConfiguration(int code) {
        return code == PERMISSION || code == LANGUAGE_UNAVAILABLE ||
            code == LANGUAGE_UNSUPPORTED;
    }

    public static long backoffMs(int code, int failures) {
        if (isSilent(code)) return 600L;
        int count = Math.max(1,failures);
        if (code == TOO_MANY_REQUESTS) return Math.min(9000L, 2500L * count);
        if (code == SERVER_DISCONNECTED) return Math.min(6500L, 1600L * count);
        if (code == BUSY) return Math.min(6000L, 1200L * count);
        return Math.min(6000L, 1000L * count);
    }

    public static String explanation(int code) {
        switch (code) {
            case SERVER_DISCONNECTED:
                return "Android втратив з'єднання зі службою розпізнавання мовлення (11).";
            case TOO_MANY_REQUESTS:
                return "Служба розпізнавання обмежила часті запити (10).";
            case BUSY:
                return "Служба розпізнавання зайнята іншим записом (8).";
            case LANGUAGE_UNAVAILABLE:
                return "Український пакет розпізнавання ще не завантажений (13).";
            case LANGUAGE_UNSUPPORTED:
                return "Поточний рушій не підтримує українську (12).";
            case PERMISSION:
                return "Доступ до мікрофона заборонений (9).";
            default:
                return "Служба розпізнавання повернула помилку (" + code + ").";
        }
    }
}
