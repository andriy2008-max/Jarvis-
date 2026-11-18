package com.andriy.jarvis;

import java.util.regex.Pattern;

/**
 * Watch-only two-turn Spotify dialogue. An incomplete request must never be
 * forwarded as arbitrary AI/phone text, and the next song title must never be
 * reinterpreted as a different device command.
 */
public final class JarvisWatchMusicFollowup {
    private static final Pattern WATCH_SUFFIX=Pattern.compile(
        "(?iu)\\s+(?:на|у|в)\\s+(?:(?:моєму|своєму)\\s+)?"+
        "(?:годиннику|watch7|watch)\\s*$");
    private static final Pattern MISSING_TRACK=Pattern.compile(
        "(?iu)^(?:увімкни|умімкни|увімкнм|увимкни|увімкні|"+
        "ввімкни|увімкну|включи|включить|включити|ввімкнути|"+
        "запусти|постав|поставь|вруби|проиграй|воспроизведи|сыграй)"+
        "\\s+(?:пісню|пісні|песню|песни|пісня|песня|трек|song|track)$");
    private static final Pattern MISSING_PLAYLIST=Pattern.compile(
        "(?iu)^(?:увімкни|умімкни|увімкнм|увимкни|увімкні|"+
        "ввімкни|увімкну|включи|включить|включити|ввімкнути|"+
        "запусти|постав|поставь|вруби|проиграй|воспроизведи|сыграй)"+
        "\\s+(?:(?:мій|мою)\\s+)?(?:плейлист|плейліст|playlist)$");
    private static final Pattern OTHER_COMMAND=Pattern.compile(
        "(?iu)^(?:увімкни|включи|ввімкни|вимкни|виключи|"+
        "відкрий|запусти|постав|поставь|напиши|скажи|"+
        "знайди|пошукай|проклади|побудуй|натисни|зажми|"+
        "зателефонуй|подзвони|свайпни|прокрути|"+
        "створи|згенеруй|відміни|скасуй|стоп|де\\s+я)(?:\\s+.*)?$");

    private JarvisWatchMusicFollowup() {}

    public static SpotifyMusicCommands.Kind missingTitle(String raw) {
        if(raw==null || raw.length()>450)return null;
        String text=WATCH_SUFFIX.matcher(CommandPatterns.normalize(raw))
            .replaceFirst("").trim().replaceAll("\\s+"," ");
        if(MISSING_TRACK.matcher(text).matches())
            return SpotifyMusicCommands.Kind.TRACK;
        if(MISSING_PLAYLIST.matcher(text).matches())
            return SpotifyMusicCommands.Kind.PLAYLIST;
        return null;
    }

    public static boolean isCancel(String raw) {
        String text=CommandPatterns.normalize(raw);
        return text.equals("скасуй") || text.equals("ні") ||
            text.equals("не треба") || text.equals("відміна") ||
            text.equals("заново") || text.equals("стоп");
    }

    /** Name-only answer to the latest watch prompt; null for other commands. */
    public static String titleOnly(String raw) {
        if(raw==null || raw.length()>180)return null;
        String title=CommandPatterns.normalize(raw)
            .replaceAll("^[«\\\"']+|[»\\\"']+$","").trim();
        if(title.isEmpty() || title.length()>75 ||
           title.contains("://") || OTHER_COMMAND.matcher(title).matches() ||
           isCancel(title))return null;
        return title;
    }
}
