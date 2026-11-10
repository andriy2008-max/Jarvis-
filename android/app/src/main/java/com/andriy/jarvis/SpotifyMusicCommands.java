package com.andriy.jarvis;

import java.util.Locale;

/**
 * Exact, local Ukrainian voice intents. Never forwards a music control into AI
 * or treats "stop music" as a request to start Spotify.
 */
public final class SpotifyMusicCommands {
    public enum Kind { OPEN, RAP, UKRAINIAN }
    private SpotifyMusicCommands() {}

    public static Kind parse(String raw) {
        if (raw == null) return null;
        String s = CommandPatterns.normalize(raw).toLowerCase(Locale.forLanguageTag("uk-UA"))
            .replaceAll("[.,!?]+$", "").replaceAll("\\s+", " ").trim();
        if (!s.matches("^(увімкни|ввімкни|увімкну|включи|включити|ввімкнути|запусти|постав|вруби)\\s+.+$"))
            return null;
        String object = s.replaceFirst(
            "^(?:увімкни|ввімкни|увімкну|включи|включити|ввімкнути|запусти|постав|вруби)\\s+", "");
        switch (object) {
            case "музику": case "музон": case "музика": case "spotify":
            case "спотіфай": case "спотифай": return Kind.OPEN;
            case "реп": case "реп музику": case "репчик": case "rap":
                return Kind.RAP;
            case "українське": case "українську музику":
            case "укр музику": case "укр муз": case "українські пісні":
                return Kind.UKRAINIAN;
            default: return null;
        }
    }
}
