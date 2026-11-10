package com.andriy.jarvis;

import java.net.URI;
import java.util.Locale;

/** Contracts for a verified, single-download ONNX model pack. */
public final class OfflineVoicePackRules {
    public static final String FORMAT = "jarvis-espnet-onnx-v1";
    public static final long MAX_ARCHIVE_BYTES = 1_500_000_000L;
    public static final long MAX_EXTRACTED_BYTES = 2_000_000_000L;
    public static final int MAX_ENTRIES = 32;

    private OfflineVoicePackRules() {}

    public static boolean validUrl(String raw) {
        if (raw == null) return false;
        try {
            URI url = new URI(raw.trim());
            return "https".equalsIgnoreCase(url.getScheme()) && url.getHost() != null &&
                url.getUserInfo() == null && url.getRawQuery() == null &&
                url.getRawFragment() == null && url.getPath() != null &&
                url.getPath().toLowerCase(Locale.ROOT).endsWith(".zip");
        } catch (Exception ignored) { return false; }
    }

    public static boolean validSha256(String value) {
        return value != null && value.matches("(?i)[0-9a-f]{64}");
    }

    public static boolean validEntry(String name) {
        if (name == null || name.isEmpty() || name.length() > 150 ||
            name.startsWith("/") || name.contains("\\") || name.contains("//") ||
            name.equals(".") || name.contains("../") || name.contains("/..") ||
            name.startsWith(".") || name.indexOf(':') >= 0) return false;
        if (name.equals("manifest.json")) return true;
        if (!name.startsWith("onnx/") || !name.endsWith(".onnx")) return false;
        return name.matches("[a-zA-Z0-9_./-]+") && !name.contains("/./");
    }

    public static String normalizedSha256(String value) {
        return validSha256(value) ? value.toLowerCase(Locale.ROOT) : "";
    }
}
