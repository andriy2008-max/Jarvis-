package com.andriy.jarvis;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Temporary local user-operated UI mode. Never persists or enables itself at
 * boot; Android Accessibility authorization is handled separately.
 */
public final class JarvisPhoneMode {
    public enum Transition { ENTER, EXIT, NONE }

    private static volatile boolean active;
    private static final CopyOnWriteArrayList<Runnable> listeners =
        new CopyOnWriteArrayList<>();

    private JarvisPhoneMode() {}

    public static boolean isActive() { return active; }

    public static Transition transition(String spoken) {
        if (spoken == null) return Transition.NONE;
        String normalized = spoken.trim()
            .toLowerCase(java.util.Locale.forLanguageTag("uk-UA"))
            .replaceAll("[.,!?;:—]+$", "").trim()
            .replaceAll("\\s+", " ");
        if (normalized.equals("телефон") || normalized.equals("режим телефон") ||
            normalized.equals("режим телефона") ||
            normalized.equals("джарвіс телефон") ||
            normalized.equals("джарвис телефон") ||
            normalized.equals("jarvis phone")) return Transition.ENTER;
        if (normalized.equals("джарвіс") || normalized.equals("джарвис") ||
            normalized.equals("jarvis") || normalized.equals("вийди з режиму телефон") ||
            normalized.equals("вийди з режиму телефона")) return Transition.EXIT;
        return Transition.NONE;
    }

    public static void setActive(boolean value) {
        if (active == value) return;
        active = value;
        for (Runnable callback : listeners) {
            try { callback.run(); } catch (RuntimeException ignored) { }
        }
    }

    public static void subscribe(Runnable listener) {
        if (listener != null) listeners.addIfAbsent(listener);
    }

    public static void unsubscribe(Runnable listener) {
        if (listener != null) listeners.remove(listener);
    }
}
