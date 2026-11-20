package com.andriy.jarvis;

public final class JarvisWatchUiRulesTest {
    private static void yes(boolean valid,String name) {
        if(!valid)throw new AssertionError("Watch UI: "+name);
    }
    public static void main(String[] args) {
        yes(!JarvisWatchUiRules.revealSpotifyNavigation(0,3900,3000,true,true),
            "never swipe once Search is visible");
        yes(!JarvisWatchUiRules.revealSpotifyNavigation(0,3900,3000,false,false),
            "never swipe behind another app");
        yes(!JarvisWatchUiRules.revealSpotifyNavigation(0,2000,3000,true,false),
            "wait for Spotify to load");
        yes(!JarvisWatchUiRules.revealSpotifyNavigation(1,5000,1000,true,false),
            "space repeated swipes");
        yes(JarvisWatchUiRules.revealSpotifyNavigation(0,3800,2400,true,false),
            "reveal hidden Search");
        yes(JarvisWatchUiRules.revealSpotifyNavigation(2,12000,5000,true,false),
            "bounded third attempt");
        yes(!JarvisWatchUiRules.revealSpotifyNavigation(3,12000,5000,true,false),
            "never swipe a fourth time");
        yes(JarvisWatchUiRules.spotifyQueryVisible(
            "Пачка сигарет","пачка сигарет"),"Cyrillic query visible");
        yes(JarvisWatchUiRules.spotifyQueryVisible(
            "Yesterday","yesterday"),"English query visible");
        yes(!JarvisWatchUiRules.spotifyQueryVisible("Пачка сигарет",""),
            "success requires actual text");
        yes(!JarvisWatchUiRules.spotifyQueryVisible("Пачка сигарет","Привіт"),
            "wrong search must not proceed");
        yes(!JarvisWatchUiRules.verifiedMapsHomeGrid(
            false,true,true,true,true),"never tap on a map canvas");
        yes(!JarvisWatchUiRules.verifiedMapsHomeGrid(
            true,true,true,true,false),"reject incomplete tile fingerprint");
        yes(JarvisWatchUiRules.verifiedMapsHomeGrid(
            true,true,true,true,true),"top-left map tile only after home fingerprint");
        yes(JarvisWatchUiRules.spotifyHomeConfirmed(true,false,false),
            "selected Home tab identifies home");
        yes(JarvisWatchUiRules.spotifyHomeConfirmed(false,true,false),
            "visible Spotify home section identifies home");
        yes(!JarvisWatchUiRules.spotifyHomeConfirmed(false,false,false),
            "unknown screen is not assumed home");
        yes(!JarvisWatchUiRules.spotifyHomeConfirmed(true,true,true),
            "do not swipe behind an open search editor");
        yes(JarvisWatchUiRules.mayReturnToSpotifyHome(0,true),
            "return to home from player");
        yes(JarvisWatchUiRules.mayReturnToSpotifyHome(2,true),
            "bounded third navigation");
        yes(!JarvisWatchUiRules.mayReturnToSpotifyHome(3,true),
            "stop after three home attempts");
        yes(!JarvisWatchUiRules.mayReturnToSpotifyHome(0,false),
            "never navigate another foreground app");
        yes(JarvisWatchUiRules.spotifyPlaylistNameMatches(
            "Вечірній реп","ВЕЧІРНІЙ РЕП"),"library title match Ukrainian");
        yes(JarvisWatchUiRules.spotifyPlaylistNameMatches(
            "Мій плейлист","мій плейлист playlist"),"library playlist metadata");
        yes(JarvisWatchUiRules.spotifyPlaylistNameMatches(
            "Jazz Mix","jazz mix"),"library title match English");
        yes(!JarvisWatchUiRules.spotifyPlaylistNameMatches(
            "Jazz Mix","Jazz Mix 2026"),"never select approximate playlist");
        yes(!JarvisWatchUiRules.spotifyPlaylistNameMatches(
            "Мій реп","Мій реп пісня"),"never select track as playlist");
        yes(JarvisWatchUiRules.mayScrollForPlaylist(0,0,true),
            "scroll when no playlist is visible");
        yes(JarvisWatchUiRules.mayScrollForPlaylist(15,1,true),
            "bounded repeated viewport search");
        yes(!JarvisWatchUiRules.mayScrollForPlaylist(16,0,true),
            "stop after bounded scrolling");
        yes(!JarvisWatchUiRules.mayScrollForPlaylist(3,2,true),
            "stop when no viewport change");
        yes(!JarvisWatchUiRules.mayScrollForPlaylist(0,0,false),
            "never scroll another app");
        yes(JarvisWatchUiRules.MAPS_WALKING_FALLBACK_Y>.48f &&
            JarvisWatchUiRules.MAPS_WALKING_FALLBACK_Y<.67f,
            "walking card between bus and bicycle, not bottom bike row");
        yes(JarvisWatchUiRules.MAPS_ROUTE_SHORT_SWIPE_START_Y>
            JarvisWatchUiRules.MAPS_ROUTE_SHORT_SWIPE_END_Y &&
            JarvisWatchUiRules.MAPS_ROUTE_SHORT_SWIPE_START_Y-
            JarvisWatchUiRules.MAPS_ROUTE_SHORT_SWIPE_END_Y<.22f,
            "small upward swipe, not a full screen swipe");
        yes(JarvisWatchUiRules.mapsWalkingLabel("Walking"),
            "English walking accessibility icon");
        yes(JarvisWatchUiRules.mapsWalkingLabel("Пішки"),
            "Ukrainian pedestrian label");
        yes(JarvisWatchUiRules.mapsWalkingLabel("Пешеходный маршрут"),
            "Russian pedestrian label");
        yes(!JarvisWatchUiRules.mapsWalkingLabel("Велосипедом"),
            "never select bicycle route");
        yes(!JarvisWatchUiRules.mapsWalkingLabel("Автобусом"),
            "never select transit route");
        yes(JarvisWatchUiRules.mapsDistanceLabel("01:56 · 21 км"),
            "visible walking-route distance in user's screenshot");
        yes(JarvisWatchUiRules.mapsDistanceLabel("21 km"),
            "English distance marker");
        yes(!JarvisWatchUiRules.mapsDistanceLabel("2 год 10 хв"),
            "transit duration is not a walking distance");
        yes(!JarvisWatchUiRules.allowWalkingCardTap(
            true,false,true,false,false,true),
            "do not click before one short upward swipe");
        yes(!JarvisWatchUiRules.allowWalkingCardTap(
            true,true,true,false,false,false),
            "do not tap if route cards vanished");
        yes(!JarvisWatchUiRules.allowWalkingCardTap(
            true,true,false,false,false,true),
            "do not click behind other foreground apps");
        yes(!JarvisWatchUiRules.allowWalkingCardTap(
            true,true,true,true,false,true),
            "never click Maps home");
        yes(!JarvisWatchUiRules.allowWalkingCardTap(
            true,true,true,false,true,true),
            "never tap keyboard/search");
        yes(JarvisWatchUiRules.allowWalkingCardTap(
            true,true,true,false,false,true),
            "after short UP and verified route cards, tap walking row");
        yes(!JarvisWatchUiRules.allowBottomRouteModeTap(
            false,true,false,false,6000L),"no tap before Directions");
        yes(!JarvisWatchUiRules.allowBottomRouteModeTap(
            true,false,false,false,6000L),"no tap on another foreground app");
        yes(!JarvisWatchUiRules.allowBottomRouteModeTap(
            true,true,true,false,6000L),"no tap on Maps home");
        yes(!JarvisWatchUiRules.allowBottomRouteModeTap(
            true,true,false,true,6000L),"no tap while search or keyboard is open");
        yes(!JarvisWatchUiRules.allowBottomRouteModeTap(
            true,true,false,false,1599L),"wait for the route cards to appear");
        yes(JarvisWatchUiRules.allowBottomRouteModeTap(
            true,true,false,false,1600L),"after directions, click bottom walking card");
        yes(JarvisWatchUiRules.mapsRouteDurationLabel("4 год 36 хв"),
            "third Wear route is a duration label, not text Пішки");
        yes(JarvisWatchUiRules.mapsRouteDurationLabel("4 год 12 хв"),
            "transit duration label");
        yes(JarvisWatchUiRules.mapsRouteDurationLabel("34 хв"),
            "car duration label");
        yes(!JarvisWatchUiRules.mapsRouteDurationLabel("11:04"),
            "clock time is not a route");
        yes(!JarvisWatchUiRules.mapsRouteDurationLabel("Стебник"),
            "destination title is not a route");
        yes(JarvisWatchUiRules.mapsRouteChoicesConfirmed(3,true),
            "three visible mode cards identify route selection");
        yes(!JarvisWatchUiRules.mapsRouteChoicesConfirmed(1,true),
            "single unrelated duration is not route selection");
        yes(!JarvisWatchUiRules.mapsRouteChoicesConfirmed(3,false),
            "no visible bottom choice means do not bypass Directions");
        yes(JarvisWatchUiRules.allowBottomRouteModeTap(
            true,true,false,false,8000L),"fallback remains eligible until timeout");
        yes(JarvisWatchUiRules.watchSearchRecovery(0,false,true,false)==
            JarvisWatchUiRules.WatchSearchRecovery.RIGHT_THEN_DOWN,
            "missing Search after initial UP: one RIGHT then DOWN");
        yes(JarvisWatchUiRules.watchSearchRecovery(1,false,true,false)==
            JarvisWatchUiRules.WatchSearchRecovery.TWO_RIGHT_THEN_UP,
            "still missing Search: two RIGHT then UP");
        yes(JarvisWatchUiRules.watchSearchRecovery(2,false,true,false)==
            JarvisWatchUiRules.WatchSearchRecovery.EXHAUSTED,
            "never swipe forever");
        yes(JarvisWatchUiRules.watchSearchRecovery(0,true,true,false)==
            JarvisWatchUiRules.WatchSearchRecovery.NONE,
            "search found: do not swipe right");
        yes(JarvisWatchUiRules.watchSearchRecovery(0,false,false,false)==
            JarvisWatchUiRules.WatchSearchRecovery.NONE,
            "do not swipe in another app");
        yes(JarvisWatchUiRules.watchSearchRecovery(0,false,true,true)==
            JarvisWatchUiRules.WatchSearchRecovery.NONE,
            "never swipe behind an open keyboard");
        yes(JarvisWatchUiRules.watchResultTitleAboveType(
            170,195,180,182,440,440),
            "Watch7 title and song/playlist type share same card");
        yes(!JarvisWatchUiRules.watchResultTitleAboveType(
            170,300,180,182,440,440),
            "distant subtitle belongs to another result");
        yes(JarvisWatchUiRules.watchResultRegion(
            215,200,240,35,440,440),
            "song or playlist inside visible Watch result list");
        yes(!JarvisWatchUiRules.watchResultRegion(
            215,45,240,35,440,440),
            "top query is not a result");
        yes(!JarvisWatchUiRules.watchResultRegion(
            215,403,240,35,440,440),
            "bottom player is not a search result");
        yes(JarvisWatchUiRules.watchCombinedType(
            "Сексуальный плейлист • Плейліст")==
            SpotifyTrackSearchRules.SearchResultType.PLAYLIST,
            "one accessibility label can contain playlist title and type");
        yes("Сексуальный плейлист".equals(
            JarvisWatchUiRules.watchCombinedTitle(
                "Сексуальный плейлист • Плейліст")),
            "first search result title must be preserved");
        yes(JarvisWatchUiRules.watchCombinedType(
            "Романтичная • Пісня • Syava")==
            SpotifyTrackSearchRules.SearchResultType.SONG,
            "combined song label with artist");
        yes("Романтичная".equals(
            JarvisWatchUiRules.watchCombinedTitle(
                "Романтичная • Пісня • Syava")),
            "song title must not include metadata");
        yes(JarvisWatchUiRules.watchCombinedTitle("Плейлісти")==null,
            "filter title cannot be selected as song");
        System.out.println("PASS JarvisWatchUiRulesTest");
    }
}
