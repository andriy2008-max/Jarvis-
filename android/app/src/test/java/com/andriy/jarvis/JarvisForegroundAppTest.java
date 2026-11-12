package com.andriy.jarvis;

public final class JarvisForegroundAppTest {
    private static void yes(boolean condition) {
        if(!condition) throw new AssertionError("Foreground app constraint");
    }
    public static void main(String[] args) {
        yes(JarvisForegroundApp.validPackage("com.spotify.music"));
        yes(JarvisForegroundApp.validPackage("org.telegram.messenger"));
        yes(JarvisForegroundApp.validPackage("ee.mtakso.client"));
        yes(!JarvisForegroundApp.validPackage(""));
        yes(!JarvisForegroundApp.validPackage("Spotify"));
        yes(!JarvisForegroundApp.validPackage("com.spotify.*"));
        yes(!JarvisForegroundApp.validPackage("com.spotify.music;rm -rf /"));
        yes(!JarvisForegroundApp.validPackage(null));
        yes(JarvisForegroundApp.matches("com.spotify.music","com.spotify.music"));
        yes(!JarvisForegroundApp.matches("com.spotify.music","com.spotify.music.lite"));
        yes(!JarvisForegroundApp.matches("com.spotify.music","org.telegram.messenger"));
        yes(!JarvisForegroundApp.matches("com.spotify.music",null));
        yes(!JarvisForegroundApp.matches("com.spotify.music",""));
        yes(!JarvisForegroundApp.matches("com.spotify.*","com.spotify.music"));
        System.out.println("PASS JarvisForegroundAppTest");
    }
}
