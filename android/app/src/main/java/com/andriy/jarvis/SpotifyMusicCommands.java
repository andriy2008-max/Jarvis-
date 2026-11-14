package com.andriy.jarvis;

import java.util.Locale;

/**
 * Exact, local Ukrainian/Russian voice intents. Preserves dictated
 * Cyrillic song titles for Spotify Search; never forwards explicit music
 * commands into AI or interprets "stop music" as "start".
 */
public final class SpotifyMusicCommands {
    public enum Kind { OPEN, RAP, UKRAINIAN, PLAYLIST, TRACK }
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
            "^(?:увімкни|умімкни|увімкнм|увимкни|увімкні|ввімкни|увімкну|включи|включить|включити|ввімкнути|запусти|постав|поставь|вруби|проиграй|воспроизведи|сыграй)\\s+"+
            "(?:(?:мій|мою)\\s+)?(?:плейлист|плейліст|playlist)\\s+(.+)$"
        ).matcher(normalized);
        if (!matched.matches()) return null;
        String title=matched.group(1).trim();
        String without="\\s+без\\s+(?:перемішування|перемешивания|шафлу|shuffle)$";
        boolean shuffle=!title.matches(".*"+without);
        if (!shuffle) title=title.replaceFirst(without,"").trim();
        title=title.replaceAll("^[«\"']+|[»\"']+$","").trim();
        if (title.isEmpty() || title.length()>75 ||
            title.equals("без") || title.equals("перемішування") ||
            title.equals("без перемішування") ||
            title.equals("без перемешивания") ||
            title.equals("без шафлу") || title.equals("без shuffle"))
            return null;
        return new NamedPlaylist(title,shuffle);
    }

    /** Ukrainian/Russian track request, including a name not saved in
     * library. The phone searches Spotify only, never another app.
     * A handful of exact speech-to-text errors is allowed ONLY for explicit
     * music imperatives, never arbitrary commands or unrelated AI requests. */
    public static String namedTrack(String raw) {
        String s=CommandPatterns.normalize(raw).replaceAll("\\s+"," ").trim();
        java.util.regex.Matcher matched=java.util.regex.Pattern.compile(
            "^(?:увімкни|умімкни|увімкнм|увимкни|увімкні|ввімкни|увімкну|включи|включить|включити|ввімкнути|запусти|постав|поставь|вруби|проиграй|воспроизведи|сыграй)\\s+"+
            "(?:пісню|пісні|песню|песни|пісня|песня|трек|song|track)\\s+(.+)$").matcher(s);
        if(!matched.matches())return null;
        String title=matched.group(1).trim()
            .replaceAll("^[«\\\"']+|[»\\\"']+$","").trim();
        return title.isEmpty()||title.length()>75?null:title;
    }

    public static Kind parse(String raw) {
        if (namedPlaylist(raw)!=null) return Kind.PLAYLIST;
        if (namedTrack(raw)!=null) return Kind.TRACK;
        if (raw == null) return null;
        String s = CommandPatterns.normalize(raw).toLowerCase(Locale.forLanguageTag("uk-UA"))
            .replaceAll("[.,!?]+$", "").replaceAll("\\s+", " ").trim();
        if (!s.matches("^(увімкни|умімкни|увімкнм|увимкни|увімкні|ввімкни|увімкну|включи|включить|включити|ввімкнути|запусти|постав|поставь|вруби|проиграй|воспроизведи|сыграй)\\s+.+$"))
            return null;
        String object = s.replaceFirst(
            "^(?:увімкни|умімкни|увімкнм|увимкни|увімкні|ввімкни|увімкну|включи|включить|включити|ввімкнути|запусти|постав|поставь|вруби|проиграй|воспроизведи|сыграй)\\s+", "");
        switch (object) {
            case "музику": case "музыку": case "музон": case "музика":
            case "музыка": case "spotify":
            case "спотіфай": case "спотифай": return Kind.OPEN;
            case "реп": case "реп музику": case "репчик": case "rap":
                return Kind.RAP;
            case "українське": case "украинское":
            case "украинскую музыку": case "українську музику":
            case "укр музику": case "укр муз": case "українські пісні":
                return Kind.UKRAINIAN;
            default: return null;
        }
    }
}
