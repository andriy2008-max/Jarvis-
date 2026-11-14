package com.andriy.jarvis;

public final class SpotifyTrackSearchRulesTest {
    private static void ok(boolean v,String message) {
        if(!v)throw new AssertionError(message);
    }

    public static void main(String[] args) {
        ok(SpotifyTrackSearchRules.searchTab("Пошук"),"Ukrainian Search tab");
        ok(SpotifyTrackSearchRules.searchTab("Search"),"English Search tab");
        ok(!SpotifyTrackSearchRules.searchTab("Моя бібліотека"),"not library");
        ok(SpotifyTrackSearchRules.searchField("Що хочеш послухати?"),"search input");
        ok(SpotifyTrackSearchRules.searchField("Search Spotify"),"English search field");
        ok(!SpotifyTrackSearchRules.searchField("Надіслати"),"not unrelated field");
        ok(SpotifyTrackSearchRules.exactSong("Без бою","Без бою"),"exact song");
        ok(SpotifyTrackSearchRules.exactSong("Без бою","Без бою!"),"punctuation");
        ok(!SpotifyTrackSearchRules.exactSong("Без бою","Без бою ремікс"),
            "never silently select a remix");
        ok(!SpotifyTrackSearchRules.exactSong("Ocean","Ocean Drive"),
            "never select a different song");
        ok(!SpotifyTrackSearchRules.exactSong("Ocean",null),"not a missing row");
        ok(SpotifyTrackSearchRules.resultRegion(360,470,530,92,0,0,684,1536),
            "normal result row");
        ok(!SpotifyTrackSearchRules.resultRegion(360,1460,530,92,0,0,684,1536),
            "never bottom miniplayer");
        ok(!SpotifyTrackSearchRules.resultRegion(360,70,530,92,0,0,684,1536),
            "never search bar");
        ok(!SpotifyTrackSearchRules.resultRegion(360,470,684,1536,0,0,684,1536),
            "never full screen control");
        System.out.println("PASS SpotifyTrackSearchRulesTest");
    }
}
