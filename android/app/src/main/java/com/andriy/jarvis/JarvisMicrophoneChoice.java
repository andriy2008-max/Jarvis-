package com.andriy.jarvis;

/** Stable user preference shared by Jarvis phone, floating and shake mics.
 * Watch microphone remains local to the Wear OS app. */
public final class JarvisMicrophoneChoice {
    public enum Mode { AUTO, PHONE, HEADSET }

    private JarvisMicrophoneChoice() {}

    public static Mode fromStored(String raw) {
        if ("phone".equals(raw)) return Mode.PHONE;
        if ("headset".equals(raw)) return Mode.HEADSET;
        return Mode.AUTO; // preserve existing automatic behavior
    }

    public static String stored(Mode mode) {
        switch(mode == null ? Mode.AUTO : mode) {
            case PHONE: return "phone";
            case HEADSET: return "headset";
            default: return "auto";
        }
    }

    public static String label(Mode mode) {
        switch(mode == null ? Mode.AUTO : mode) {
            case PHONE: return "Телефон";
            case HEADSET: return "Гарнітура";
            default: return "Автоматично";
        }
    }
}
