package com.andriy.jarvis;

import java.util.Arrays;
import java.util.List;

/** Pure-Java tests: no APK build or device required. */
public final class AppNameMatcherTest {
    private static int checks = 0;
    private static void require(boolean yes, String why) {
        if (!yes) throw new AssertionError(why);
        checks++;
    }
    private static AppNameMatcher.App app(String pkg, String label) {
        return new AppNameMatcher.App(pkg, pkg + ".Main", label);
    }
    private static void found(String command, String pkg, List<AppNameMatcher.App> apps) {
        AppNameMatcher.Result r = AppNameMatcher.match(command, apps);
        require(r.found() && r.app.packageName.equals(pkg), "Expected " + pkg + " for " + command);
    }
    private static void missing(String command, List<AppNameMatcher.App> apps) {
        require(!AppNameMatcher.match(command, apps).found(), "Unexpected match " + command);
    }
    public static void main(String[] args) {
        List<AppNameMatcher.App> apps = Arrays.asList(
            app("org.telegram.messenger", "Telegram"), app("org.telegram.x", "Telegram X"),
            app("com.spotify.music", "Spotify"), app("com.example.lightroom", "Adobe Lightroom"),
            app("com.example.myapp", "Мій щоденник"), app("org.example.zen", "ZenGarden"),
            app("org.example.maps", "Google Maps"), app("org.example.simple", "Камера"),
            app("org.example.more", "Мій калькулятор"), app("org.example.notes", "Нотатки"),
            app("org.example.clash1", "Clash Royale"), app("org.example.clash2", "Clash of Clans")
        );
        found("telegram", "org.telegram.messenger", apps);
        found("телеграм", "org.telegram.messenger", apps);
        found("Telegram X", "org.telegram.x", apps);
        found("телеграм!", "org.telegram.messenger", apps);
        found("spotify", "com.spotify.music", apps);
        found("спотіфай", "com.spotify.music", apps);
        found("lightroom", "com.example.lightroom", apps);
        found("Adobe Lightroom", "com.example.lightroom", apps);
        found("Мій щоденник", "com.example.myapp", apps);
        found("мiй щоденник", "com.example.myapp", apps); // Tolerates one mixed-script typo
        found("камеру", "org.example.simple", apps);
        found("камера", "org.example.simple", apps);
        found("Google Maps", "org.example.maps", apps);
        found("карта", "org.example.maps", apps);
        found("карти", "org.example.maps", apps);
        found("Zen Garden", "org.example.zen", apps); // not substring unless word form matches
        found("нотатки", "org.example.notes", apps);
        missing("невідома програма", apps);
        missing("те", apps);
        require(AppNameMatcher.match("clash", apps).ambiguous(), "Ambiguous titles must not open randomly");
        require(AppNameMatcher.match("clash", apps).alternatives.size() == 2, "Preserve both choices for a user dialog");
        List<AppNameMatcher.App> duplicate = Arrays.asList(
            app("one.pkg", "Telegram"), app("one.pkg", "Telegram"));
        found("telegram", "one.pkg", duplicate);
        require(!AppNameMatcher.match("telegram", duplicate).ambiguous(), "Same package not ambiguous");
        require(AppNameMatcher.normalize("  Foo-Bar 2.0 ").equals("foo bar 2 0"), "Punctuation normalizer");
        System.out.println("PASS " + checks + " universal app matching checks");
    }
}
