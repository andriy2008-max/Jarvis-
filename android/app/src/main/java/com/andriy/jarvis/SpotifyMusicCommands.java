package com.andriy.jarvis;

import java.util.Locale;

/**
 * Exact, local Ukrainian voice intents. Never forwards a music control into AI
 * or treats "stop music" as a request to start Spotify.
 */
public final class SpotifyMusicCommands {
    public enum Kind { OPEN, RAP, UKRAINIAN, PLAYLIST }
    private SpotifyMusicCommands() {}

    public static final class NamedPlaylist {
        public final String name;
        public final boolean shuffle;
        private NamedPlaylist(String name,boolean shuffle) {
            this.name=name; this.shuffle=shuffle;
        }
    }

    /** Named playlists are local voice commands, never sent to the AI.
     * The optional suffix explicitly asks for original playlist order. */
    public static NamedPlaylist namedPlaylist(String raw) {
        String normalized=CommandPatterns.normalize(raw)
            .replaceAll("\\s+"," ").trim();
        java.util.regex.Matcher matched=java.util.regex.Pattern.compile(
            "^(?:увімкни|ввімкни|увімкну|включи|включити|ввімкнути|запусти|постав|вруби)\\s+"+
            "(?:(?:мій|мою)\\s+)?(?:плейлист|плейліст|playlist)\\s+(.+)$"
        ).matcher(normalized);
        if (!matched.matches()) return null;
        String title=matched.group(1).trim();
        String without="\\s+без\\s+(?:перемішування|перемешивания|шафлу|shuffle)$";
        boolean shuffle=!title.matches(".*"+without);
        if (!shuffle) title=title.replaceFirst(without,"").trim();
        title=title.replaceAll("^[«\"']+|[»\"']+$","").trim();
        if (title.isEmpty() || title.length()>75 ||
            title.equals("без") || title.equals("перемішування")) return null;
        return new NamedPlaylist(title,shuffle);
    }

    public static Kind parse(String raw) {
        if (namedPlaylist(raw)!=null) return Kind.PLAYLIST;
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
