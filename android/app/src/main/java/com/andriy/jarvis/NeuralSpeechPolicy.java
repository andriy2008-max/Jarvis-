package com.andriy.jarvis;

import java.net.URI;

/** Validation shared by UI and the neural speech transport. No secrets or endpoint defaults. */
public final class NeuralSpeechPolicy {
    public static final int MAX_TEXT_CHARS = 1200;
    public static final int MAX_WAV_BYTES = 8 * 1024 * 1024;
    private NeuralSpeechPolicy() {}

    public static boolean validEndpoint(String value) {
        if (value == null) return false;
        try {
            URI uri = new URI(value.trim());
            return "https".equalsIgnoreCase(uri.getScheme()) &&
                uri.getHost() != null && !uri.getHost().isEmpty() &&
                uri.getUserInfo() == null && uri.getRawQuery() == null &&
                uri.getRawFragment() == null && "/tts".equals(uri.getPath());
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean validToken(String value) {
        if (value == null || value.length() < 20 || value.length() > 256) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 33 || c > 126) return false;
        }
        return true;
    }

    public static boolean validText(String value) {
        return value != null && !value.trim().isEmpty() &&
            value.length() <= MAX_TEXT_CHARS;
    }
}
