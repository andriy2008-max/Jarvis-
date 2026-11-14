package com.andriy.jarvis;

import java.util.Locale;

/** Conservative Spotify song search UI matching, independent of Android. */
public final class SpotifyTrackSearchRules {
    private SpotifyTrackSearchRules() {}

    public static String normal(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{N}]+"," ").replaceAll("\\s+"," ").trim();
    }

    public static boolean searchTab(String label) {
        String s=normal(label);
        return s.equals("search") || s.equals("search tab") ||
            s.equals("search tab selected") || s.equals("search selected") ||
            s.equals("пошук") || s.equals("пошук вибрано") ||
            s.equals("вкладка пошук") || s.equals("вкладка пошук вибрано") ||
            s.equals("поиск") || s.equals("поиск выбрано") ||
            s.equals("вкладка поиск");
    }

    public static boolean searchField(String label) {
        String s=normal(label);
        return s.equals("search") || s.equals("search spotify") ||
            s.equals("search songs or artists") ||
            s.equals("what do you want to listen to") ||
            s.equals("пошук") || s.equals("пошук у spotify") ||
            s.equals("що хочеш послухати") ||
            s.equals("що ти хочеш послухати") ||
            s.equals("знайти") || s.equals("пошук музики") ||
            s.equals("search for songs") || s.equals("search for songs or podcasts") ||
            s.equals("пошук пісень") ||
            s.equals("поиск") || s.equals("что хочешь послушать");
    }

    /** Exact title, or accessibility combined label beginning with that
     * title plus a song/track type. Never match arbitrary subtitles. */
    public static boolean exactSong(String requested,String text) {
        String title=normal(requested),visible=normal(text);
        if(title.isEmpty() || title.length()>100)return false;
        return visible.equals(title);
    }

    public static boolean songType(String text) {
        String s=normal(text);
        return s.equals("song") || s.equals("track") ||
            s.equals("пісня") || s.equals("трек") ||
            s.equals("песня") || s.equals("аудіо") ||
            s.contains(" song ") || s.startsWith("song ") ||
            s.contains(" track ") || s.startsWith("track ") ||
            s.startsWith("пісня ") || s.contains(" пісня ");
    }

    /** A search result must be in the content area and not be a bottom
     * player, input field, filter tab, or full-screen carousel. */
    public static boolean resultRegion(int x,int y,int w,int h,
                                       int left,int top,int right,int bottom) {
        int pw=right-left,ph=bottom-top;
        return pw>=240 && ph>=400 && w>=30 && h>=18 &&
            w<pw*.97f && h<ph*.18f &&
            x>left+pw*.04f && x<left+pw*.95f &&
            y>top+ph*.14f && y<top+ph*.84f;
    }
}
