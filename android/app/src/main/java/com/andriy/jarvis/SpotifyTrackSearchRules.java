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

    /** The big, white Spotify Search prompt on the Pixel is a different
     * control from the bottom Search tab and the Search page heading.
     * Match only explicit placeholder text before trying a gesture. */
    public static boolean searchPrompt(String label) {
        String s=normal(label);
        return s.equals("що ви хочете послухати") ||
            s.equals("що хочете послухати") ||
            s.equals("що хочеш послухати") ||
            s.equals("що ти хочеш послухати") ||
            s.equals("what do you want to listen to") ||
            s.equals("what would you like to listen to") ||
            s.equals("что вы хотите послушать") ||
            s.equals("что хочешь послушать") ||
            s.equals("что ты хочешь послушать");
    }

    /** A prompt near the TOP of the Spotify Search page. No fixed screen
     * coordinates: the user screenshot supplies only the relative region. */
    public static boolean searchPromptRegion(int x,int y,int w,int h,
                                              int left,int top,int right,int bottom) {
        int pw=right-left,ph=bottom-top;
        return pw>=240 && ph>=400 && w>=28 && h>=14 &&
            w<pw*.96f && h<ph*.16f &&
            x>left+pw*.09f && x<right-pw*.09f &&
            y>top+ph*.07f && y<top+ph*.38f;
    }

    /** Screenshot-matched fallback when Spotify Compose does not expose the
     * white search prompt as an Accessibility node. Both the top Search
     * heading and the Music/Podcasts browse cards must be visible. */
    public static boolean spotifyBrowseHeading(String label) {
        String s=normal(label);
        return s.equals("пошук") || s.equals("search") || s.equals("поиск");
    }

    public static boolean spotifyBrowseMusic(String label) {
        String s=normal(label);
        return s.equals("музика") || s.equals("music") || s.equals("музыка");
    }

    public static boolean spotifyBrowsePodcasts(String label) {
        String s=normal(label);
        return s.equals("подкасти") || s.equals("podcasts") ||
            s.equals("подкасты");
    }

    /** The tappable gap between the heading and browse-card row is exactly
     * where the user highlighted Spotify's Search prompt. This is a WATCH/
     * PHONE-independent relative layout test, never an unguarded fixed tap. */
    public static boolean searchLandingFallbackGeometry(
            int headingTop,int headingBottom,int tileTop,
            int frameTop,int frameBottom) {
        int height=frameBottom-frameTop;
        return height>=400 &&
            headingTop>frameTop+height*.01f &&
            headingBottom<frameTop+height*.25f &&
            tileTop>frameTop+height*.18f &&
            tileTop<frameTop+height*.53f &&
            tileTop-headingBottom>height*.035f;
    }

    /** Pixel user-provided screenshot: white Search field is centred at
     * approx. 17.5% of full portrait height (screen 684x1536). This is a
     * LAST fallback only after the Spotify Search-tab step completed. */
    public static final float PIXEL_SEARCH_PROMPT_Y=0.175f;

    public static int calibratedPromptY(int screenHeight) {
        return Math.round(screenHeight*PIXEL_SEARCH_PROMPT_Y);
    }

    /** Do not click by geometry before trying Spotify's accessible Search.
     * At most two user-calibrated taps, only in an explicit track command on
     * a portrait phone after Search navigation, never on the Watch7. */
    public static boolean allowCalibratedPromptTap(boolean searchStepCompleted,
            boolean spotifyRootVisible,boolean editorReady,
            int screenWidth,int screenHeight,int tapAttempts,long focusAgeMs) {
        return searchStepCompleted && spotifyRootVisible && !editorReady &&
            screenWidth>=450 && screenHeight>=screenWidth*1.60f &&
            screenHeight>=700 && tapAttempts>=0 && tapAttempts<2 &&
            focusAgeMs>=1800L;
    }

    /** Owner-calibrated search-result rows for the Pixel. Spotify Compose
     * sometimes hides ALL result labels from Accessibility. The first result
     * was reported near Y=510 on a 2400px screen; subsequent visible rows
     * step by approx. 0.115 of display height. Never target bottom player. */
    public static int calibratedResultY(int displayHeight,int ordinal) {
        if(ordinal<0 || ordinal>4)return -1;
        return Math.round(displayHeight*(0.2125f+ordinal*.115f));
    }

    public static boolean mayTapResultAfterSubmit(boolean queryWasSubmitted,
            boolean spotifyVisible,boolean imeOpen,int width,int height,
            int ordinal,long elapsedMs) {
        if(!queryWasSubmitted || !spotifyVisible || imeOpen ||
            ordinal<0 || ordinal>4 || width<450 ||
            height<width*1.6f || elapsedMs<1200L)return false;
        int y=calibratedResultY(height,ordinal);
        return y>height*.16f && y<height*.78f;
    }

    /** Strict metadata classification for the MIXED Spotify search list.
     * Category pills use plural "Пісні"/"Плейлісти"; each result uses a
     * singular "Пісня"/"Плейліст", often followed by an artist. */
    public enum SearchResultType { SONG, PLAYLIST, UNKNOWN }

    public static SearchResultType mixedResultType(String raw) {
        String s=normal(raw);
        if(s.isEmpty())return SearchResultType.UNKNOWN;
        if(s.equals("плейліст") || s.equals("плейлист") ||
           s.equals("playlist") || s.startsWith("плейліст ") ||
           s.startsWith("плейлист ") || s.startsWith("playlist ") ||
           s.contains(" плейліст ") || s.contains(" плейлист ") ||
           s.contains(" playlist "))
            return SearchResultType.PLAYLIST;
        if(s.equals("пісня") || s.equals("песня") ||
           s.equals("song") || s.equals("track") ||
           s.startsWith("пісня ") || s.startsWith("песня ") ||
           s.startsWith("song ") || s.startsWith("track ") ||
           s.contains(" пісня ") || s.contains(" песня ") ||
           s.contains(" song ") || s.contains(" track "))
            return SearchResultType.SONG;
        return SearchResultType.UNKNOWN;
    }

    /** Spotify Compose may publish a complete row in one accessibility
     * description: "Сексуальный плейлист • Плейліст". A result name is
     * NEVER inferred from the spoken query: the query can match result #4. */
    public static String mixedCombinedTitle(String label) {
        if(label==null || label.length()>260)return null;
        String[] fields=label.split("[,•·\\n]",5);
        if(fields.length<2)return null;
        int typeAt=-1;
        for(int i=0;i<fields.length;i++) {
            String field=normal(fields[i]);
            if(field.equals("плейліст") || field.equals("плейлист") ||
               field.equals("playlist") || field.equals("пісня") ||
               field.equals("песня") || field.equals("song") ||
               field.equals("track")) {typeAt=i;break;}
        }
        if(typeAt<0)return null;
        int nameAt=typeAt==0?1:0;
        if(nameAt>=fields.length)return null;
        String title=fields[nameAt].trim();
        String normalized=normal(title);
        if(normalized.isEmpty() || normalized.length()>120 ||
           mixedResultType(title)!=SearchResultType.UNKNOWN ||
           normalized.equals("плейлісти") || normalized.equals("пісні") ||
           normalized.equals("playlists") || normalized.equals("songs") ||
           normalized.equals("пошук") || normalized.equals("search"))
            return null;
        return title;
    }

    /** When Spotify withholds every Song/Playlist subtitle, prefer the
     * first VISIBLE result-title node after the filter chips, not a
     * same-name track lower down. Geometry is guarded by the actual
     * Spotify query, phone viewport and content region. */
    public static boolean untypedResultTitleCandidate(String text,
            int x,int y,int w,int h,
            int left,int top,int right,int bottom) {
        int pw=right-left,ph=bottom-top;
        String s=normal(text);
        if(s.isEmpty() || s.length()>120 ||
           mixedResultType(text)!=SearchResultType.UNKNOWN ||
           searchTab(text) || searchPrompt(text) ||
           s.equals("пісні") || s.equals("плейлісти") ||
           s.equals("песни") || s.equals("плейлисты") ||
           s.equals("songs") || s.equals("playlists") ||
           s.equals("відео") || s.equals("альбоми") ||
           s.equals("жанри") || s.equals("video") ||
           s.equals("albums") || s.equals("genres") ||
           s.equals("моя бібліотека") || s.equals("створити"))
            return false;
        return resultRegion(x,y,w,h,left,top,right,bottom) &&
            pw>=450 && ph>=700 &&
            x>left+pw*.18f && x<left+pw*.79f &&
            y>top+ph*.185f && y<top+ph*.78f &&
            h<ph*.095f;
    }

    /** A title is selected only from the same visual row as a typed
     * metadata label, away from filter pills and the bottom mini-player. */
    public static boolean mixedResultTitleCandidate(String title,
            int titleCenterY,int metadataCenterY,int displayHeight) {
        String s=normal(title);
        return displayHeight>=700 && !s.isEmpty() &&
            mixedResultType(title)==SearchResultType.UNKNOWN &&
            !s.equals("пісні") && !s.equals("плейлісти") &&
            !s.equals("songs") && !s.equals("playlists") &&
            !s.equals("відео") && !s.equals("albums") &&
            !s.equals("альбоми") && !s.equals("жанри") &&
            !s.equals("пошук") && !s.equals("search") &&
            metadataCenterY>titleCenterY &&
            metadataCenterY-titleCenterY<=displayHeight*.065f;
    }

    /** A visible Spotify song/track result, not an artist, album or playlist.
     * Accept combined Compose labels with song category delimiters. */
    public static boolean resultSongLabel(String raw) {
        String label=normal(raw);
        if(label.isEmpty() || label.contains("playlist") ||
           label.contains("плейлист") || label.contains("альбом") ||
           label.contains("album") || label.contains("подкаст") ||
           label.contains("podcast"))return false;
        return label.equals("song") || label.equals("track") ||
            label.equals("пісня") || label.equals("песня") ||
            label.startsWith("song ") || label.startsWith("track ") ||
            label.startsWith("пісня ") || label.startsWith("песня ") ||
            label.endsWith(" song") || label.endsWith(" track") ||
            label.endsWith(" пісня") || label.endsWith(" песня") ||
            label.contains(" song ") || label.contains(" track ") ||
            label.contains(" пісня ") || label.contains(" песня ");
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

    /** Mini-player track title appears above the bottom navigation bar.
     * Require a precise title, not just a substring of another track. */
    public static boolean miniPlayerTitle(String requested,String label) {
        String wanted=normal(requested),shown=normal(label);
        if(wanted.isEmpty() || shown.isEmpty())return false;
        if(shown.equals(wanted))return true;
        // A real combined mini-player label separates title and metadata.
        // A plain prefix such as "Пачка сигарет (Live)" is NOT a match.
        String[] parts=label.split("[,•·\\n]",2);
        return parts.length==2 && normal(parts[0]).equals(wanted);
    }

    public static boolean miniPlayerRegion(int x,int y,int w,int h,
                                           int left,int top,int right,int bottom) {
        int pw=right-left,ph=bottom-top;
        return pw>=240 && ph>=400 && w>12 && h>8 &&
            w<pw*.93f && h<ph*.16f &&
            x>left+pw*.035f && x<right-pw*.035f &&
            y>top+ph*.73f && y<top+ph*.95f;
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
