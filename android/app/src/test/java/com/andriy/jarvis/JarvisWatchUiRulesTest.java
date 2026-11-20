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
        yes(JarvisWatchUiRules.MAPS_ROUTE_MODE_BOTTOM_Y>.80f &&
            JarvisWatchUiRules.MAPS_ROUTE_MODE_BOTTOM_Y<.94f,
            "mode tap stays at bottom center of round Watch screen");
        yes(!JarvisWatchUiRules.allowBottomRouteModeTap(
            false,true,false,false,6000L),"no tap before Directions");
        yes(!JarvisWatchUiRules.allowBottomRouteModeTap(
            true,false,false,false,6000L),"no tap on another foreground app");
        yes(!JarvisWatchUiRules.allowBottomRouteModeTap(
            true,true,true,false,6000L),"no tap on Maps home");
        yes(!JarvisWatchUiRules.allowBottomRouteModeTap(
            true,true,false,true,6000L),"no tap while search or keyboard is open");
        yes(!JarvisWatchUiRules.allowBottomRouteModeTap(
            true,true,false,false,4199L),"first attempt labelled walking action");
        yes(JarvisWatchUiRules.allowBottomRouteModeTap(
            true,true,false,false,4200L),"after route screen only, tap bottom");
        yes(JarvisWatchUiRules.allowBottomRouteModeTap(
            true,true,false,false,8000L),"fallback remains eligible until timeout");
        yes(JarvisWatchUiRules.spotifyHomeConfirmed(false,false,true,false),
            "player list icon is enough without title or playing song");
        yes(!JarvisWatchUiRules.spotifyHomeConfirmed(false,false,true,true),
            "search editor is not the player even with a list icon");
        for(String label:new String[]{"Queue","Черга","Очередь","Browse"})
            yes(JarvisWatchUiRules.spotifyPlayerListButton(label,null,
                227,380,50,50,0,0,454,454),"bottom list icon: "+label);
        yes(JarvisWatchUiRules.spotifyPlayerListButton("","com.spotify.music:id/queue_button",
            227,380,50,50,0,0,454,454),"icon can be identified by view ID");
        yes(!JarvisWatchUiRules.spotifyPlayerListButton("Queue",null,
            227,90,50,50,0,0,454,454),"queue heading is not bottom button");
        yes(!JarvisWatchUiRules.spotifyPlayerListButton("Play",null,
            227,380,50,50,0,0,454,454),"play is not the list button");
        yes(!JarvisWatchUiRules.spotifyPlayerListButton("",null,
            227,380,50,50,0,0,454,454),"unknown icon does not prove home");
        yes(JarvisWatchUiRules.SPOTIFY_DOWN_END-JarvisWatchUiRules.SPOTIFY_DOWN_START>=.75f &&
            JarvisWatchUiRules.SPOTIFY_DOWN_DURATION_MS>=650L,
            "long downward drag across at least three quarters of screen");
        for(String label:new String[]{"4 год 36 хв, Пішки", "Walking, 21 km", "Пешком 30 мин", "пішохіднии маршрут", "пешии маршрут"})
            yes(JarvisWatchUiRules.mapsWalkingLabel(label),"walking in combined ETA: "+label);
        yes(!JarvisWatchUiRules.mapsWalkingLabel("Walkington"),"place name is not walking mode");
        for(String label:new String[]{"34 хв", "4 год 12 хв", "4 год 36 хв", "30 min", "1 h 20 min"})
            yes(JarvisWatchUiRules.mapsDurationLabel(label),"route duration: "+label);
        yes(!JarvisWatchUiRules.mapsDurationLabel("11:35 · 23 км"),"arrival/distance are not durations");
        yes(JarvisWatchUiRules.mapsThreeModeCards(true,false,3),"three modes for requested destination");
        yes(!JarvisWatchUiRules.mapsThreeModeCards(false,false,3),"no destination match");
        yes(!JarvisWatchUiRules.mapsThreeModeCards(true,true,3),"not a search list");
        yes(!JarvisWatchUiRules.mapsThreeModeCards(true,false,2),"do not choose transit when walking absent");
        yes(!JarvisWatchUiRules.mapsThreeModeCards(true,false,4),"unknown layout must not guess third row");
        yes(JarvisWatchUiRules.mapsModeCardLayout(0,454,new int[]{210,310,405}),
            "three large cards with clipped walking card at bottom");
        yes(JarvisWatchUiRules.mapsModeCardLayout(20,400,new int[]{200,290,370}),
            "layout uses current window origin and size");
        yes(!JarvisWatchUiRules.mapsModeCardLayout(0,454,new int[]{210,230,250}),
            "three duration nodes in one card are not three modes");
        yes(!JarvisWatchUiRules.mapsModeCardLayout(0,454,new int[]{210,310,470}),
            "walking row outside screen is never tapped");
        yes(!JarvisWatchUiRules.mapsModeCardLayout(0,454,new int[]{310,210,405}),
            "reversed or overlapping rows rejected");
        yes(!JarvisWatchUiRules.mapsModeCardLayout(0,454,new int[]{210,310}),
            "two visible cards cannot identify third mode");
        System.out.println("PASS JarvisWatchUiRulesTest");
    }
}
