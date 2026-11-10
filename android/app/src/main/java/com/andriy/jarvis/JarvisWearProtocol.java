package com.andriy.jarvis;

/** Shared, short-lived watch-to-phone command paths and transport limits. */
public final class JarvisWearProtocol {
    public static final String REQUEST = "/jarvis/phone-control/v1/request";
    public static final String REPLY = "/jarvis/phone-control/v1/reply";
    public static final int MAX_BYTES = 4096;
    public static final int MAX_COMMAND_LENGTH = 250;
    public static final int ACK_TIMEOUT_MS = 12000;
    public static final int SESSION_IDLE_MS = 120000;
    public static final String ENTER = "enter";
    public static final String EXIT = "exit";
    public static final String COMMAND = "command";
    public static final String SPOTIFY = "spotify";
    public static final int SPOTIFY_ACK_TIMEOUT_MS = 145000;

    private JarvisWearProtocol() {}

    public static boolean validAction(String action) {
        return ENTER.equals(action) || EXIT.equals(action) || COMMAND.equals(action) ||
            SPOTIFY.equals(action);
    }

    public static boolean validCommand(String command) {
        return command != null && !command.trim().isEmpty() &&
            command.length() <= MAX_COMMAND_LENGTH &&
            command.indexOf('\n') < 0 && command.indexOf('\r') < 0 &&
            command.indexOf('\u0000') < 0;
    }

    public static boolean validId(String id) {
        return id != null && id.matches("[0-9a-fA-F-]{36}");
    }
}
