package com.andriy.jarvis;

/** Side-effect-free safety gates for Wear OS navigation and search. */
public final class JarvisWatchUiRules {
    private JarvisWatchUiRules() {}

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

    /** An unlabelled map icon may only be clicked when all four tiles and
     * the "Recent" heading were recognized in the actual Maps view. */
    public static boolean verifiedMapsHomeGrid(boolean recent,
            boolean upperLeft,boolean upperRight,boolean lowerLeft,boolean lowerRight) {
        return recent && upperLeft && upperRight && lowerLeft && lowerRight;
    }
}
