package com.andriy.jarvis;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/** No Android dependencies: validates the remote endpoint and bounds chat context. */
public final class AiChatPolicy {
    public static final int MAX_MESSAGE_CHARS = 2000;
    public static final int MAX_CONTEXT_TURNS = 14;
    private AiChatPolicy() { }

    public static boolean isValidEndpoint(String url) {
        if (url == null || url.length() > 500 || url.trim().isEmpty()) return false;
        try {
            URI uri = new URI(url.trim());
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null &&
                !uri.getHost().isEmpty() && uri.getUserInfo() == null && uri.getFragment() == null &&
                uri.getQuery() == null && uri.getPort() >= -1 && uri.getPath() != null &&
                uri.getPath().startsWith("/");
        } catch (URISyntaxException exception) { return false; }
    }

    public static boolean isValidAccessCode(String code) {
        return code != null && code.length() >= 20 && code.length() <= 300 &&
            code.equals(code.trim()) && !code.contains("\n") && !code.contains("\r");
    }

    public static List<String> keepLast(List<String> items, int max) {
        if (items == null || max <= 0) return new ArrayList<>();
        int start = Math.max(0, items.size() - max);
        return new ArrayList<>(items.subList(start, items.size()));
    }
}
