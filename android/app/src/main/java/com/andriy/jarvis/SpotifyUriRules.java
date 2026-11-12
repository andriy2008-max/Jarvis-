package com.andriy.jarvis;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure validation of user-supplied Spotify links; never opens arbitrary URLs. */
public final class SpotifyUriRules {
    private static final Pattern URI=Pattern.compile(
        "^spotify:(playlist|album|track):([A-Za-z0-9]{22})$",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern LINK=Pattern.compile(
        "^https://open\\.spotify\\.com/(?:(?:intl-[a-z]{2})/)?"
        +"(playlist|album|track)/([A-Za-z0-9]{22})/?(?:[?#].*)?$",
        Pattern.CASE_INSENSITIVE);
    private SpotifyUriRules(){}

    /** Return a canonical Spotify URI or null. No track/playlist guessing. */
    public static String canonical(String input) {
        if (input==null || input.length()>500) return null;
        String candidate=input.trim();
        Matcher m=URI.matcher(candidate);
        if (!m.matches())m=LINK.matcher(candidate);
        if (!m.matches())return null;
        return "spotify:"+m.group(1).toLowerCase(Locale.ROOT)+":"+m.group(2);
    }

    public static boolean validClientId(String input) {
        return input!=null && input.trim().matches("[a-fA-F0-9]{32}");
    }

    public static String normalizedAlias(String input) {
        return SpotifyUiLabels.normalize(input);
    }
}
