package com.andriy.jarvis;

import java.util.Locale;

/** Conservative Spotify song search UI matching, independent of Android. */
public final class SpotifyTrackSearchRules {
    private SpotifyTrackSearchRules() {}

    public static String normal(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT)
            // Russian speech recognizers and music catalogs may disagree
            // about ё vs е. This is orthographic folding, not translation.
            .replace('ё','е')
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

    /** Only a short visible label from Spotify's bottom navigation can
     * authorize fallback touch. Never assume a fixed tab coordinate. */
    public static boolean bottomSearchTabRegion(int x,int y,int w,int h,
                                                int left,int top,int right,int bottom) {
        int width=right-left,height=bottom-top;
        return width>=240 && height>=400 && w>=12 && h>=12 &&
            w<width*.45f && h<height*.19f &&
            x>left+width*.04f && x<right-width*.04f &&
            y>top+height*.72f && y<bottom-height*.012f;
    }

    /** A top-of-screen query header is different from a song result in
     * Spotify's scrolling search results or from the bottom mini-player. */
    public static boolean queryHeaderRegion(int x,int y,int w,int h,
                                            int left,int top,int right,int bottom) {
        int width=right-left,height=bottom-top;
        return width>=240 && height>=400 && w>=15 && h>=12 &&
            x>left+width*.05f && x<right-width*.05f &&
            y>top+height*.015f && y<top+height*.18f;
    }

    public static boolean searchField(String label) {
        String s=normal(label);
        return s.equals("search") || s.equals("search spotify") ||
            s.equals("search songs or artists") ||
            s.equals("what do you want to listen to") ||
            s.equals("пошук") || s.equals("пошук у spotify") ||
            s.equals("що ви хочете послухати") ||
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

    /** Spotify sometimes exposes a single combined Accessibility label
     * instead of separate title and category nodes. Accept an EXACT title
     * next to an explicit song/track category; never an artist, album or
     * playlist with a similar name. Metadata must have real delimiters. */
    public static boolean exactTypedSong(String requested,String label) {
        String wanted=normal(requested);
        if(wanted.isEmpty() || wanted.length()>100 || label==null)return false;
        String[] segments=label.split("[,•·\\n]",4);
        if(segments.length<2)return false;
        String first=normal(segments[0]),second=normal(segments[1]);
        return (wanted.equals(first) && songType(second)) ||
            (songType(first) && wanted.equals(second));
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

    /** Full horizontal song rows, excluding footer player, icons and cards. */
    public static boolean songRowRegion(int l,int t,int r,int b,int fl,int ft,int fr,int fb) {
        int w=fr-fl,h=fb-ft;
        return w>0 && h>0 && r-l>=w*.5f && r-l<=w && b-t>=h*.022f && b-t<h*.16f &&
            t>=ft+h*.16f && b<=ft+h*.82f;
    }

    public static boolean songRowEvidence(String label,String ids,boolean playlist,
                                           boolean artwork,int textCount) {
        String text=normal(label),id=normal(ids);
        if(text.isEmpty() || text.contains("додати до плейліста") ||
            text.contains("додати до плейлиста") || text.contains("add to playlist") ||
            text.contains("створити плейліст") || text.contains("створити плейлист") ||
            text.contains("create playlist") || id.contains("now playing") ||
            id.contains("mini player") || id.contains("miniplayer"))return false;
        boolean typed=false,other=false;
        for(String part:label.split("[,•·\\n]")) {
            String type=normal(part);
            typed|=type.matches("song|track|пісня|трек|песня|аудіо");
            other|=type.matches("(artist|album|playlist|podcast|episode|виконавець|альбом|плейлист|плейліст|подкаст|епізод|исполнитель)");
        }
        if(other && !typed && !playlist)return false;
        return typed || id.matches(".*(track|song) (row|title|name|item).*") ||
            playlist && artwork && textCount>=2;
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
