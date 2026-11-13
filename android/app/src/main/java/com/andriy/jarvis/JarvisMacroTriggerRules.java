package com.andriy.jarvis;

/** Two identical voice phrases may coexist only when their foreground
 * constraints are exact, non-empty, and mutually exclusive Android packages. */
public final class JarvisMacroTriggerRules {
    private JarvisMacroTriggerRules() {}

    public static boolean disjointApps(String first, String second) {
        return JarvisForegroundApp.validPackage(first) &&
            JarvisForegroundApp.validPackage(second) && !first.equals(second);
    }
}
