package com.andriy.jarvis;

/** Side-effect-free safety gates for Wear OS navigation and search. */
public final class JarvisWatchUiRules {
    private JarvisWatchUiRules() {}

    /** Near the bottom CENTER of a round Watch7 display, inside its usable
     * touch area. This is intentionally NOT a Pixel screen coordinate. */
    public static final float MAPS_ROUTE_MODE_BOTTOM_Y=0.88f;

    /** Only after a Maps directions screen was reached, its walking-label
     * search has been attempted, and a user-requested fallback is needed. */
    public static boolean allowBottomRouteModeTap(boolean directionsOpened,
            boolean mapsForeground,boolean mapsHome,boolean searchOrImeOpen,
            long walkingStageMs) {
        return directionsOpened && mapsForeground && !mapsHome &&
            !searchOrImeOpen && walkingStageMs>=4200L;
    }

    /** Never swipe if Search is already visible or the target app isn't foreground. */
    public static boolean revealSpotifyNavigation(int attempts,long searchAgeMs,
            long sinceLastRevealMs,boolean spotifyVisible,boolean searchAvailable) {
        return attempts>=0 && attempts<3 && searchAgeMs>=3800L &&
            sinceLastRevealMs>=2400L && spotifyVisible && !searchAvailable;
    }

    /** ACTION_SET_TEXT returning true is not evidence that text was inserted. */
    public static boolean spotifyQueryVisible(String expected,String actual) {
        if(expected==null || expected.trim().isEmpty() || actual==null)return false;
        return expected.trim().toLowerCase(java.util.Locale.ROOT)
            .replaceAll("\\s+"," ").equals(actual.trim()
                .toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+"," "));
    }

    /** Spotify home has to be identified before attempting a watch-only
     * navigation gesture. A selected Home tab is stronger evidence than a
     * passive label. Recently-played sections can also identify home. */
    public static boolean spotifyHomeConfirmed(boolean homeTabSelected,
            boolean homeSectionVisible,boolean hasEditableSearch) {
        return !hasEditableSearch && (homeTabSelected || homeSectionVisible);
    }

    /** Never loop indefinitely through an unknown Spotify page. */
    public static boolean mayReturnToSpotifyHome(int completedSwipes,
            boolean spotifyForeground) {
        return spotifyForeground && completedSwipes>=0 && completedSwipes<3;
    }

    /** A named playlist must be discovered directly in the library,
     * never by submitting its name into the Spotify Search tab. */
    public static boolean spotifyPlaylistNameMatches(String wanted,String visible) {
        if(wanted==null || visible==null || wanted.trim().isEmpty())return false;
        String target=wanted.trim().toLowerCase(java.util.Locale.ROOT)
            .replaceAll("\\s+"," ");
        String actual=visible.trim().toLowerCase(java.util.Locale.ROOT)
            .replaceAll("\\s+"," ");
        return actual.equals(target) ||
            actual.equals(target+" playlist") ||
            actual.equals(target+" плейлист") ||
            actual.equals(target+" плейліст");
    }

    /** Retry bounded scrolling only after a visible-name lookup fails.
     * If the accessibility viewport does not change twice, stop. */
    public static boolean mayScrollForPlaylist(int scrollAttempts,
            int unchangedViewports,boolean spotifyForeground) {
        return spotifyForeground && scrollAttempts>=0 &&
            scrollAttempts<16 && unchangedViewports<2;
    }

    /** An unlabelled map icon may only be clicked when all four tiles and
     * the "Recent" heading were recognized in the actual Maps view. */
    public static boolean verifiedMapsHomeGrid(boolean recent,
            boolean upperLeft,boolean upperRight,boolean lowerLeft,boolean lowerRight) {
        return recent && upperLeft && upperRight && lowerLeft && lowerRight;
    }
}
