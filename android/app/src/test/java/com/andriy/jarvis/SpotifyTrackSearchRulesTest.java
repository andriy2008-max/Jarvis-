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
        ok(SpotifyTrackSearchRules.searchField("Що ви хочете послухати?"),"reported Pixel search prompt");
        // User's Pixel screenshot: Search tab is already open, but its
        // large white input prompt is a separate non-clickable Compose node.
        // The page must be recognized and the prompt itself tapped, not the
        // bottom Search tab nor the mini-player.
        ok(SpotifyTrackSearchRules.searchPrompt("Що ви хочете послухати?"),
            "Pixel screenshot search placeholder in Ukrainian");
        ok(SpotifyTrackSearchRules.searchPrompt("What do you want to listen to?"),
            "English Spotify search prompt");
        ok(SpotifyTrackSearchRules.searchPrompt("Что вы хотите послушать?"),
            "Russian Spotify search prompt");
        ok(!SpotifyTrackSearchRules.searchPrompt("Пошук"),
            "do not confuse Search heading/tab with search prompt");
        ok(!SpotifyTrackSearchRules.searchPrompt("Музика"),
            "never tap a Spotify browse tile");
        ok(SpotifyTrackSearchRules.searchPromptRegion(
            330,272,475,67,0,0,684,1536),
            "Pixel screenshot prompt is at top center");
        ok(SpotifyTrackSearchRules.searchPromptRegion(
            280,255,265,38,0,0,684,1536),
            "non-clickable nested Compose text can be tapped");
        ok(!SpotifyTrackSearchRules.searchPromptRegion(
            330,800,475,67,0,0,684,1536),
            "never tap content tiles as search prompt");
        ok(!SpotifyTrackSearchRules.searchPromptRegion(
            330,1440,475,67,0,0,684,1536),
            "never tap Spotify miniplayer or bottom tab");
        ok(!SpotifyTrackSearchRules.searchPromptRegion(
            330,272,684,1536,0,0,684,1536),
            "never tap a full-screen window");
        ok(SpotifyTrackSearchRules.spotifyBrowseHeading("Пошук"),
            "Search landing heading visible on screenshot");
        ok(SpotifyTrackSearchRules.spotifyBrowseMusic("Музика"),
            "Music browse tile on user screenshot");
        ok(SpotifyTrackSearchRules.spotifyBrowsePodcasts("Подкасти"),
            "Podcasts browse tile on user screenshot");
        ok(!SpotifyTrackSearchRules.spotifyBrowseHeading("Нещодавні"),
            "unrelated page cannot be mistaken for Search landing");
        ok(SpotifyTrackSearchRules.searchLandingFallbackGeometry(
            130,190,363,0,1536),
            "screenshot with hidden Compose prompt can use bounded gap");
        ok(!SpotifyTrackSearchRules.searchLandingFallbackGeometry(
            130,190,205,0,1536),
            "no search row gap between heading and tiles");
        ok(!SpotifyTrackSearchRules.searchLandingFallbackGeometry(
            130,190,1110,0,1536),
            "never tap gap when browse cards are at screen bottom");
        ok(!SpotifyTrackSearchRules.searchLandingFallbackGeometry(
            770,815,1100,0,1536),
            "never tap if Search heading is not at top");
        ok(SpotifyTrackSearchRules.searchField("Search Spotify"),"English search field");
        ok(!SpotifyTrackSearchRules.searchField("Надіслати"),"not unrelated field");
        ok(SpotifyTrackSearchRules.exactSong("Без бою","Без бою"),"exact song");
        // Current Spotify accessibility may combine title + content type +
        // artist in a single label instead of exposing the title separately.
        ok(SpotifyTrackSearchRules.exactTypedSong("Морозы","Морозы, Song, Artist"),
            "Russian result with explicit song category");
        ok(SpotifyTrackSearchRules.exactTypedSong("Я хочу","Я хочу • Пісня • Виконавець"),
            "Ukrainian result with explicit song category");
        ok(SpotifyTrackSearchRules.exactTypedSong("Shape of You","Track, Shape of You"),
            "English result whose accessible label starts with Track");
        ok(!SpotifyTrackSearchRules.exactTypedSong("Я хочу","Я хочу, Playlist, Artist"),
            "never click a playlist sharing the song title");
        ok(!SpotifyTrackSearchRules.exactTypedSong("Морозы","Морозы remix, Song, Artist"),
            "never click a different recording");
        ok(!SpotifyTrackSearchRules.exactTypedSong("Я хочу","Я хочу в отпуск"),
            "untyped partial text must not match");
        ok(!SpotifyTrackSearchRules.exactTypedSong("Я хочу","Я хочу, Album, Artist"),
            "album title is not a track");
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
        // Nov 17 Pixel screen: two tracks with the same title. The first
        // is Kino at y~310, the second is Lizer at y~510 (684x1536).
        ok(SpotifyTrackSearchRules.exactTypedSong("Пачка сигарет",
            "Пачка сигарет • Пісня • Kino"),"first correct Kino song");
        ok(SpotifyTrackSearchRules.resultRegion(255,320,260,44,
            0,0,684,1536),"first result title region");
        ok(SpotifyTrackSearchRules.resultRegion(255,520,260,44,
            0,0,684,1536),"second song exists but must not win");
        ok(!SpotifyTrackSearchRules.resultRegion(340,151,530,48,
            0,0,684,1536),"exclude top search query header");
        ok(!SpotifyTrackSearchRules.exactTypedSong("Пачка сигарет",
            "Пачка сигарет • Виконавець • Kino"),"exclude artist row");
        ok(SpotifyTrackSearchRules.miniPlayerTitle("Пачка сигарет",
            "Пачка сигарет"),"playing song title in mini player");
        ok(!SpotifyTrackSearchRules.miniPlayerTitle("Пачка сигарет",
            "Пачка сигарет (Live)"),"different version not playback proof");
        ok(SpotifyTrackSearchRules.miniPlayerRegion(210,1240,240,36,
            0,0,684,1536),"mini player lower area");
        ok(!SpotifyTrackSearchRules.miniPlayerRegion(210,320,240,36,
            0,0,684,1536),"song result is not mini player");
        ok(!SpotifyTrackSearchRules.miniPlayerRegion(210,1480,240,36,
            0,0,684,1536),"navigation bar is not mini player");
        // Regression: Pixel screenshot 684x1536 highlighted a white search
        // box near x=342, y=269. The fallback is strictly bounded and uses
        // this phone's measured screen, not the Watch7 or a different app.
        ok(SpotifyTrackSearchRules.calibratedPromptY(1536)==269,
            "Pixel search prompt y follows 17.5% screenshot");
        ok(SpotifyTrackSearchRules.allowCalibratedPromptTap(
            true,true,false,684,1536,0,1800L),
            "tap Pixel prompt only after Search tab was opened");
        ok(SpotifyTrackSearchRules.allowCalibratedPromptTap(
            true,true,false,684,1536,1,2400L),
            "second and last calibrated attempt allowed");
        ok(!SpotifyTrackSearchRules.allowCalibratedPromptTap(
            false,true,false,684,1536,0,5000L),
            "never guess Search field before opening Search tab");
        ok(!SpotifyTrackSearchRules.allowCalibratedPromptTap(
            true,false,false,684,1536,0,5000L),
            "never tap if Spotify app root is not visible");
        ok(!SpotifyTrackSearchRules.allowCalibratedPromptTap(
            true,true,true,684,1536,0,5000L),
            "do not tap again if Spotify editor is ready");
        ok(!SpotifyTrackSearchRules.allowCalibratedPromptTap(
            true,true,false,684,1536,2,5000L),
            "no third calibrated tap");
        ok(!SpotifyTrackSearchRules.allowCalibratedPromptTap(
            true,true,false,684,1536,0,1799L),
            "allow normal accessibility matching to work first");
        ok(!SpotifyTrackSearchRules.allowCalibratedPromptTap(
            true,true,false,684,600,0,4000L),
            "never use phone coordinate on landscape/watch display");
        ok(!SpotifyTrackSearchRules.allowCalibratedPromptTap(
            true,true,false,400,900,0,4000L),
            "reject narrow watch-style display");


        // Regression: first result was visible, but Spotify withheld an
        // accessible exact title. Allow a calibrated tap ONLY after submit.
        ok(SpotifyTrackSearchRules.calibratedResultY(2400,0)==510,
            "first Pixel song result matches user-confirmed y510");
        ok(SpotifyTrackSearchRules.calibratedResultY(2400,1)==786,
            "next result row is below the first");
        ok(SpotifyTrackSearchRules.calibratedResultY(2400,2)==1062,
            "third result continues down the same list");
        ok(SpotifyTrackSearchRules.mayTapResultAfterSubmit(
            true,true,false,1080,2400,0,1600L),
            "first result can be tapped after Spotify search submission");
        ok(SpotifyTrackSearchRules.mayTapResultAfterSubmit(
            true,true,false,1080,2400,1,1600L),
            "second result uses same verified query");
        ok(!SpotifyTrackSearchRules.mayTapResultAfterSubmit(
            false,true,false,1080,2400,0,1600L),
            "never tap an unsubmitted query");
        ok(!SpotifyTrackSearchRules.mayTapResultAfterSubmit(
            true,false,false,1080,2400,0,1600L),
            "never tap a different foreground app");
        ok(!SpotifyTrackSearchRules.mayTapResultAfterSubmit(
            true,true,true,1080,2400,0,1600L),
            "never tap when Spotify keyboard still owns input");
        ok(!SpotifyTrackSearchRules.mayTapResultAfterSubmit(
            true,true,false,1080,2400,0,1199L),
            "wait for result rendering");
        ok(!SpotifyTrackSearchRules.mayTapResultAfterSubmit(
            true,true,false,400,400,0,1600L),
            "phone coordinates never apply to Watch7");
        ok(!SpotifyTrackSearchRules.mayTapResultAfterSubmit(
            true,true,false,1080,2400,5,1600L),
            "no guessed taps beyond calibrated visible rows");
        ok(SpotifyTrackSearchRules.resultSongLabel("Мама • Пісня • Виконавець"),
            "support Spotify combined Ukrainian song label");
        ok(SpotifyTrackSearchRules.resultSongLabel("Song, Mama, Artist"),
            "support Spotify English song label");
        ok(!SpotifyTrackSearchRules.resultSongLabel("Мама • Альбом"),
            "do not select album instead of song");
        ok(!SpotifyTrackSearchRules.resultSongLabel("Мама • Playlist"),
            "do not select playlist instead of song");
        System.out.println("PASS SpotifyTrackSearchRulesTest");
    }
}
