package com.andriy.jarvis;

public final class JarvisMacroTriggerRulesTest {
    private static void check(boolean value) {
        if(!value)throw new AssertionError("Macro trigger overlap");
    }
    public static void main(String[] args) {
        check(JarvisMacroTriggerRules.disjointApps(
            "com.spotify.music","com.google.android.youtube"));
        check(JarvisMacroTriggerRules.disjointApps(
            "org.telegram.messenger","com.spotify.music"));
        check(!JarvisMacroTriggerRules.disjointApps(
            "com.spotify.music","com.spotify.music"));
        check(!JarvisMacroTriggerRules.disjointApps("","com.spotify.music"));
        check(!JarvisMacroTriggerRules.disjointApps(null,"com.spotify.music"));
        check(!JarvisMacroTriggerRules.disjointApps("com.spotify.music",null));
        check(!JarvisMacroTriggerRules.disjointApps("Spotify","YouTube"));
        check(!JarvisMacroTriggerRules.disjointApps("com.spotify.*","com.spotify.music"));
        System.out.println("PASS macro context-specific triggers");
    }
}
