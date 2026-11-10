package com.andriy.jarvis;

import java.util.Locale;

/** Strict matching for visible Spotify controls in common interface languages. */
public final class SpotifyUiLabels {
    public enum Target { LIBRARY, LIKED_SONGS, RAP_FILTER, UKR_MUSIC, SHUFFLE, PLAY }

    private SpotifyUiLabels() {}

    public static String normalize(String raw) {
        return raw == null ? "" : raw.toLowerCase(Locale.forLanguageTag("uk-UA"))
            .replaceAll("[^a-z0-9а-яіїєґ]+", " ")
            .replaceAll("\\s+", " ").trim();
    }

    public static boolean matches(Target t, String raw) {
        String s = normalize(raw);
        if (s.isEmpty()) return false;
        switch (t) {
            case LIBRARY:
                return containsAny(s, "your library", "my library", "library", "моя бібліотека",
                    "бібліотека", "медіатека", "моя медіатека", "твоя бібліотека",
                    "моя фонотека", "ваша медиатека", "моя медиатека");
            case LIKED_SONGS:
                return containsAny(s, "liked songs", "liked tracks", "пісні що сподобалися",
                    "вподобані пісні", "улюблені пісні", "любимые треки", "любимые песни");
            case RAP_FILTER:
                return s.equals("реп") || s.equals("rap") || s.equals("hip hop") ||
                    s.equals("хіп хоп") || s.equals("хип хоп") ||
                    s.equals("фільтр реп") || s.equals("filter rap") ||
                    s.equals("rap filter") || s.equals("genre rap");
            case UKR_MUSIC:
                return s.equals("укр муз") || s.equals("укр музика") ||
                    s.equals("українська музика") || s.equals("укрмуз") ||
                    s.equals("ukr muz");
            case SHUFFLE:
                return containsAny(s, "shuffle", "переміш", "перемеш", "випадков",
                    "случайн", "random order", "play randomly");
            case PLAY:
                if (containsAny(s, "pause", "paused", "пауза", "призупинити",
                    "зупинити", "stop playing")) return false;
                return s.equals("play") || s.equals("play button") ||
                    s.startsWith("play ") || s.endsWith(" play") ||
                    s.equals("відтворити") || s.startsWith("відтворити ") ||
                    s.equals("грати") || s.startsWith("грати ") ||
                    s.equals("слухати") || s.equals("воспроизвести") ||
                    s.startsWith("воспроизвести ");
            default: return false;
        }
    }

    public static boolean shuffleOn(String label, boolean selected, boolean checked) {
        String s = normalize(label);
        if (selected || checked) return true;
        return containsAny(s, "disable shuffle", "turn shuffle off", "shuffle on",
            "shuffle enabled", "вимкнути переміш", "виключити переміш",
            "перемішування ввімк", "перемешивание включ", "smart shuffle on");
    }

    private static boolean containsAny(String s, String... values) {
        for (String part : values)
            if (s.equals(part) || s.startsWith(part + " ") || s.endsWith(" " + part) ||
                s.contains(" " + part + " ")) return true;
        return false;
    }
}
