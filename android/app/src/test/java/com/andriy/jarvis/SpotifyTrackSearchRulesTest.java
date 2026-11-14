package com.andriy.jarvis;

public final class SpotifyTrackSearchRulesTest {
    private static void ok(boolean v,String message) {
        if(!v)throw new AssertionError(message);
    }

    public static void main(String[] args) {
        ok(SpotifyTrackSearchRules.searchTab("Пошук"),"Ukrainian Search tab");
        // Spotify's nav button can be a Compose label without ACTION_CLICK.
        // Fallback may tap only a real visible search label near the bottom.
        ok(SpotifyTrackSearchRules.bottomSearchTabRegion(265,1450,95,48,
            0,0,684,1536),"bottom search label");
        ok(!SpotifyTrackSearchRules.bottomSearchTabRegion(265,450,95,48,
            0,0,684,1536),"do not tap a search word in content");
        ok(!SpotifyTrackSearchRules.bottomSearchTabRegion(265,1450,684,1536,
            0,0,684,1536),"do not tap Spotify entire window");
        ok(SpotifyTrackSearchRules.queryHeaderRegion(280,145,245,62,
            0,0,684,1536),"verify deep-link upper query");
        ok(!SpotifyTrackSearchRules.queryHeaderRegion(280,470,245,62,
            0,0,684,1536),"a song-result title is not a search query");
        ok(!SpotifyTrackSearchRules.queryHeaderRegion(280,1420,245,62,
            0,0,684,1536),"not the bottom mini-player");
        ok(SpotifyTrackSearchRules.exactSong("морозы","Морозы"),
            "user's reported Russian song must be a valid exact title");
        ok(!SpotifyTrackSearchRules.exactSong("морозы","Мороз"),
            "do not guess a different title");
        ok(SpotifyTrackSearchRules.searchTab("Search"),"English Search tab");
        ok(!SpotifyTrackSearchRules.searchTab("Моя бібліотека"),"not library");
        ok(SpotifyTrackSearchRules.searchField("Що хочеш послухати?"),"search input");
        ok(SpotifyTrackSearchRules.searchField("Search Spotify"),"English search field");
        ok(!SpotifyTrackSearchRules.searchField("Надіслати"),"not unrelated field");
        ok(SpotifyTrackSearchRules.exactSong("Без бою","Без бою"),"exact song");
        ok(SpotifyTrackSearchRules.searchTab("Поиск"),"Russian Search tab");
        ok(SpotifyTrackSearchRules.searchField("Что хочешь послушать?"),"Russian Search field");
        ok(SpotifyTrackSearchRules.exactSong("Метель","Метель"),"Russian song");
        ok(SpotifyTrackSearchRules.exactSong("Все как у людей","Всё как у людей"),
            "Russian е/ё orthography");
        ok(SpotifyTrackSearchRules.exactSong("Весна прийде","Весна прийде"),
            "Ukrainian song");
        ok(SpotifyTrackSearchRules.exactSong("Shape of You","Shape of You"),
            "English song");
        ok(SpotifyTrackSearchRules.exactSong("Billie Jean","Billie Jean"),
            "English song title from a Ukrainian command");
        ok(!SpotifyTrackSearchRules.exactSong("Billie Jean","Billie Jean (Live)"),
            "do not silently pick a different rendition");
        ok(!SpotifyTrackSearchRules.exactSong("Весна прийде","Весна придёт"),
            "do not silently translate or choose a similarly named recording");
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
