package com.andriy.jarvis;

/** Shared, short-lived watch-to-phone command paths and transport limits. */
public final class JarvisWearProtocol {
    public static final String REQUEST = "/jarvis/phone-control/v1/request";
    public static final String REPLY = "/jarvis/phone-control/v1/reply";
    public static final int MAX_BYTES = 4096;
    public static final int MAX_COMMAND_LENGTH = 250;
    public static final int ACK_TIMEOUT_MS = 12000;
    public static final int SESSION_IDLE_MS = 120000;
    public static final String SYNC = "sync";
    public static final String ENTER = "enter";
    public static final String EXIT = "exit";
    public static final String COMMAND = "command";
    public static final String TAXI = "taxi";
    /** General voice request evaluated on the phone, with no secrets on Wear OS. */
    public static final String ASSISTANT = "assistant";
    public static final String SPOTIFY = "spotify";
    public static final String CANCEL_SPOTIFY = "cancel_spotify";
    public static final int SPOTIFY_ACK_TIMEOUT_MS = 145000;
    // Bolt UI flow is bounded to 45 s; allow delivery and result latency.
    public static final int TAXI_ACK_TIMEOUT_MS = 65000;
    // Includes slow personal AI requests and consented phone-side app workflows.
    public static final int ASSISTANT_ACK_TIMEOUT_MS = 150000;

    // Six sequential screen steps can each take 18 seconds.
    public static final int COMMAND_ACK_TIMEOUT_MS = 150000;

    public static int timeoutMs(String action) {
        if (SPOTIFY.equals(action)) return SPOTIFY_ACK_TIMEOUT_MS;
        if (TAXI.equals(action)) return TAXI_ACK_TIMEOUT_MS;
        if (ASSISTANT.equals(action)) return ASSISTANT_ACK_TIMEOUT_MS;
        if (COMMAND.equals(action)) return COMMAND_ACK_TIMEOUT_MS;
        return ACK_TIMEOUT_MS;
    }

    private JarvisWearProtocol() {}

    public static boolean validAction(String action) {
        return SYNC.equals(action) || ENTER.equals(action) || EXIT.equals(action) || COMMAND.equals(action) ||
            TAXI.equals(action) || ASSISTANT.equals(action) ||
            SPOTIFY.equals(action) || CANCEL_SPOTIFY.equals(action);
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
