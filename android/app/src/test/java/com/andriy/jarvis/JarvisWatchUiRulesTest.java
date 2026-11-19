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
        System.out.println("PASS JarvisWatchUiRulesTest");
    }
}
