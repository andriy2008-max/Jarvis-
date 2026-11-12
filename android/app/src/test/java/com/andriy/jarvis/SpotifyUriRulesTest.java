package com.andriy.jarvis;

public final class SpotifyUriRulesTest {
    private static void ok(boolean v){if(!v)throw new AssertionError("Spotify URI rules");}
    public static void main(String[] args) {
        String id="37i9dQZF1DXcBWIGoYBM5M";
        ok(("spotify:playlist:"+id).equals(SpotifyUriRules.canonical(
            "https://open.spotify.com/playlist/"+id+"?si=123")));
        ok(("spotify:playlist:"+id).equals(SpotifyUriRules.canonical(
            "spotify:playlist:"+id)));
        ok(("spotify:album:"+id).equals(SpotifyUriRules.canonical(
            "https://open.spotify.com/intl-uk/album/"+id)));
        ok(SpotifyUriRules.canonical("https://evil.com/playlist/"+id)==null);
        ok(SpotifyUriRules.canonical("https://open.spotify.com/playlist/invalid")==null);
        ok(SpotifyUriRules.canonical("spotify:playlist:abc")==null);
        ok(SpotifyUriRules.canonical("javascript:alert(1)")==null);
        ok(SpotifyUriRules.validClientId("0123456789abcdef0123456789abcdef"));
        ok(!SpotifyUriRules.validClientId("client_secret"));
        ok("укр муз".equals(SpotifyUriRules.normalizedAlias("Укр. муз")));
        System.out.println("PASS SpotifyUriRulesTest");
    }
}
