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
        if (s.isEmpty() || s.startsWith("remove ") ||
            s.startsWith("delete ") || s.startsWith("unlike ") ||
            s.startsWith("add to ") || s.startsWith("видалити ") ||
            s.startsWith("прибрати ") || s.startsWith("удалить "))
            return false;
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
                // Combined Play/Shuffle is a different action from the crossed arrows.
                if (s.matches(".*\\b(play|pause|repeat|next|back|playpause|playbutton)\\b.*") ||
                    s.startsWith("відтворити") || s.startsWith("воспроизвести")) return false;
                return s.contains("shuffle") || (" "+s).contains(" переміш") ||
                    (" "+s).contains(" перемеш") || (" "+s).contains(" випадков") ||
                    (" "+s).contains(" случайн") || containsAny(s, "random order", "play randomly");
            case PLAY:
                if (matches(Target.SHUFFLE, raw) || s.contains("shuffle")) return false;
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

    /** Bare plural arrows mean this control only within Spotify, never in other apps. */
    public static boolean shuffleRequest(String phrase) {
        String s = normalize(phrase);
        return ScreenTargetMatcher.intent(s).equals("shuffle") ||
            s.equals("стрілочки") || s.equals("стрілки") || s.equals("ці стрілочки") ||
            s.equals("ці стрілки") || s.equals("дві стрілочки") || s.equals("дві стрілки");
    }

    /** The large playlist Play is in the top-right toolbar, not the
     * bottom mini-player. Coordinates are physical pixels. */
    public static boolean playlistPlayAt(int l,int t,int r,int b,
                                         int rootL,int rootT,int rootR,int rootB,
                                         float density) {
        int w=r-l,h=b-t,rw=rootR-rootL,rh=rootB-rootT;
        return rw>=240 && rh>=400 && w>=24*density && h>=24*density &&
            w<rw*.30f && h<rh*.18f &&
            w>=h*.62f && h>=w*.62f &&
            (l+r)/2f>rootL+rw*.74f &&
            (t+b)/2f<rootT+rh*.62f;
    }

    /** The correct Shuffle button is immediately left of the big playlist
     * Play. A far-left green DOWNLOAD arrow cannot satisfy this geometry. */
    public static boolean toolbarPair(int sl,int st,int sr,int sb,
                                      int pl,int pt,int pr,int pb,float density) {
        int sw=sr-sl,sh=sb-st,pw=pr-pl,ph=pb-pt,gap=pl-sr;
        return sw>=10*density && sh>=10*density &&
            pw>=24*density && ph>=24*density &&
            sw<=pw*1.35f && sh<=ph*1.35f &&
            gap>=-pw*.10f && gap<=pw*1.4f &&
            (sl+sr)<(pl+pr) &&
            Math.abs((pt+pb)-(st+sb))/2f<=Math.max(sh,ph)*.55f;
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
