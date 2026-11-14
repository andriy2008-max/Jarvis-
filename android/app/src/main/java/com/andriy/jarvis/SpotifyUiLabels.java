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

    /** Exact playlist title, never substring matching another playlist or song.
     * Spotify may add a playlist-type prefix/suffix to accessibility labels. */
    public static boolean namedPlaylist(String wanted,String visible) {
        String title=normalize(wanted),label=normalize(visible);
        if(title.isEmpty() || title.length()>75) return false;
        return label.equals(title) || label.equals("плейлист "+title) ||
            label.equals(title+" плейлист") ||
            label.equals("плейліст "+title) || label.equals(title+" плейліст") ||
            label.equals("playlist "+title) || label.equals(title+" playlist");
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

    /** Prefer the current actual Spotify playlist action circle over a
     * screenshot coordinate. The green Play is the large RIGHTMOST button;
     * crossed-arrows Shuffle and three-dot menus have smaller bounds.
     * Only use after confirming the exact playlist header and its page. */
    public static boolean unlabelledPlaylistPlayCircle(int l,int t,int r,int b,
                                                        int rootL,int rootT,
                                                        int rootR,int rootB,
                                                        float density) {
        int w=r-l,h=b-t,rw=rootR-rootL,rh=rootB-rootT;
        return rw>=240 && rh>=400 && density>0 &&
            w>=28*density && h>=28*density &&
            w<rw*.19f && h<rh*.13f &&
            w>h*.72f && h>w*.72f &&
            (l+r)/2f>rootL+rw*.84f && (l+r)/2f<rootR-rw*.018f &&
            (t+b)/2f>rootT+rh*.16f && (t+b)/2f<rootT+rh*.66f;
    }

    /** Last-resort Play tap calibrated from the user's Spotify playlist
     * screenshot (684×1536), x≈616,y≈369. Only use after identifying a
     * PLAYLIST page in Spotify; never on library/search/mini-player screens. */
    public static int[] screenshotPlaylistPlayPoint(int width,int height) {
        if(width<360 || height<width*2.05f || height>width*2.45f)return null;
        int x=Math.round(width*(616f/684f));
        int y=Math.round(height*(369f/1536f));
        return safePlaylistPlayPoint(x,y,0,0,width,height)
            ? new int[]{x,y} : null;
    }

    public static boolean safePlaylistPlayPoint(int x,int y,int left,int top,
                                                 int right,int bottom) {
        int width=right-left,height=bottom-top;
        return width>0 && height>0 &&
            x>left+width*.84f && x<left+width*.98f &&
            y>top+height*.12f && y<top+height*.48f;
    }

    /** A title in the upper main-page header is different from a lower
     * library row. This is required before emulating an unlabeled Play. */
    public static boolean headerRegion(int x,int y,int w,int h,
                                       int left,int top,int right,int bottom) {
        int pageW=right-left,pageH=bottom-top;
        return pageW>=240 && pageH>=400 && w>=22 && h>=15 &&
            x>left+pageW*.05f && x<left+pageW*.70f &&
            y>top+pageH*.08f && y<top+pageH*.60f;
    }

    /** If the large playlist button already says Pause, that specific
     * playlist is playing; never tap it a second time and stop the music. */
    public static boolean largePause(String raw) {
        String s=normalize(raw);
        return s.equals("pause") || s.equals("pause button") ||
            s.equals("пауза") || s.equals("призупинити") ||
            s.equals("зупинити відтворення") || s.equals("приостановить") ||
            s.equals("пауза воспроизведения");
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

    /** A tap position to the LEFT of the large top-right Play button.
     * This uses visible Play bounds, not the label of another arrow icon.
     * The gap in the Spotify screenshot is approximately one Play diameter. */
    public static int[] shufflePointFromPlay(int pl,int pt,int pr,int pb,
                                              int fl,int ft,int fr,int fb,
                                              float density) {
        if (!playlistPlayAt(pl,pt,pr,pb,fl,ft,fr,fb,density)) return null;
        int diameter = Math.max(pr-pl,pb-pt);
        int x = Math.round((pl+pr)/2f - diameter*1.04f);
        int y = Math.round((pt+pb)/2f);
        return safeRightShufflePoint(x,y,fl,ft,fr,fb) ? new int[]{x,y} : null;
    }

    /** Verified reference: user's Pixel screenshot, 684 × 1536 pixels,
     * right shuffle centre (530, 369), not left download centre (48, 369).
     * Proportionally scale only for near-identical portrait screen shapes. */
    public static int[] screenshotShufflePoint(int width,int height) {
        if (width<360 || height<width*2.05f || height>width*2.45f) return null;
        int x=Math.round(width*(530f/684f));
        int y=Math.round(height*(369f/1536f));
        return safeRightShufflePoint(x,y,0,0,width,height)
            ? new int[]{x,y} : null;
    }

    /** Refuse any coordinate outside the right-hand playlist action toolbar. */
    public static boolean safeRightShufflePoint(int x,int y,int left,int top,
                                                int right,int bottom) {
        int w=right-left,h=bottom-top;
        return w>0 && h>0 &&
            x>left+w*.66f && x<left+w*.87f &&
            y>top+h*.10f && y<top+h*.53f;
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
