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
        System.out.println("PASS JarvisWatchUiRulesTest");
    }
}
