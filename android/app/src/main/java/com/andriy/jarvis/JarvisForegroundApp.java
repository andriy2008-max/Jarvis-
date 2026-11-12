package com.andriy.jarvis;

/** Pure rules for a user-selected foreground-app macro constraint.
 * The selected target is an Android package name, never a display label. */
public final class JarvisForegroundApp {
    private JarvisForegroundApp() {}

    public static boolean validPackage(String name) {
        if (name == null || name.length() > 200) return false;
        // App ID has at least two segments and no wildcard or shell syntax.
        return name.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+");
    }

    public static boolean matches(String selected, String foreground) {
        return validPackage(selected) && selected.equals(foreground);
    }
}
