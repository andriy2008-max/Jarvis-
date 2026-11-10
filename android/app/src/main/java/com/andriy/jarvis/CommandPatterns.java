package com.andriy.jarvis;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small dependency-free parser; tested with plain Java without invoking an APK build. */
public final class CommandPatterns {
    private static final Pattern TIMER = Pattern.compile(
        "(?:^|\\s)таймер\\s+(?:на\\s+)?(\\d{1,4})\\s*(секунд(?:у|и|ів)?|сек|с|хвилин(?:у|и)?|хв|годин(?:у|и)?|год)(?=$|\\s|[.,!?])",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern DIAL = Pattern.compile(
        "^(?:подзвони|зателефонуй|набери(?:\\s+номер)?|дзвони)\\s+([+0-9()\\s-]+)$",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern SEARCH = Pattern.compile(
        "^(?:знайди|пошукай|пошук(?:ай)?)(?:\\s+в\\s+інтернеті|\\s+у\\s+гуглі|\\s+в\\s+гуглі)?\\s+(.+)$",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern NOTE_INDEX = Pattern.compile(
        "^видали\\s+нотатк(?:у|и)\\s+(\\d{1,3})$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private CommandPatterns() {}

    public static String normalize(String raw) {
        if (raw == null) return "";
        return raw.trim().toLowerCase(new Locale("uk", "UA"))
            .replaceFirst("^(?:джарвіс|джарвис|jarvis)[,\\s:—-]*", "")
            .replaceAll("[.!?]+$", "").trim();
    }

    /**
     * Spoken command «Скажи [текст]»: null means another command, empty string
     * means the user did not supply anything to say. Preserve user punctuation
     * and case; do not route requested speech through the conversational AI.
     */
    public static String spokenText(String raw) {
        if (raw == null) return null;
        String text = raw.trim()
            .replaceFirst("(?iu)^(?:джарвіс|джарвис|jarvis)[,\\s:—-]*", "")
            .trim();
        Matcher m = Pattern.compile("(?iu)^скажи(?:[\\s,:—-]+([\\s\\S]*))?$").matcher(text);
        if (!m.matches()) return null;
        return m.group(1) == null ? "" : m.group(1).trim();
    }

    /** Null when no timer command (including invalid units) matches; -1 for excessive durations. */
    public static Integer timerSeconds(String command) {
        Matcher m = TIMER.matcher(command);
        if (!m.find()) return null;
        long number;
        try { number = Long.parseLong(m.group(1)); }
        catch (NumberFormatException ex) { return null; }
        String unit = m.group(2);
        long factor = unit.startsWith("год") ? 3600 : unit.startsWith("хв") ? 60 : 1;
        long seconds = number * factor;
        if (seconds < 1 || seconds > 10800) return -1;
        return (int) seconds;
    }

    public static String searchTerms(String command) {
        Matcher m = SEARCH.matcher(command);
        if (!m.matches()) return null;
        String terms = m.group(1).trim();
        return terms.isEmpty() ? null : terms;
    }

    /** Digits and an optional initial + only; never actually places a call. */
    public static String dialNumber(String command) {
        Matcher m = DIAL.matcher(command);
        if (!m.matches()) return null;
        String text = m.group(1).replaceAll("[()\\s-]", "");
        if (!text.matches("\\+?[0-9]{5,15}")) return null;
        return text;
    }

    /** 1-based note index; null for other commands. */
    public static Integer noteIndex(String command) {
        Matcher m = NOTE_INDEX.matcher(command);
        if (!m.matches()) return null;
        try { return Integer.parseInt(m.group(1)); }
        catch (NumberFormatException ex) { return null; }
    }
}
