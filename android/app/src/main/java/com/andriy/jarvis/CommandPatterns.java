package com.andriy.jarvis;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small dependency-free parser; tested with plain Java without invoking an APK build. */
public final class CommandPatterns {
    private static final Pattern TIMER = Pattern.compile(
        "(?:^|\\s)таймер\\s+(?:на\\s+)?(\\d{1,4}|один|одна|одну|два|дві|три|чотири|п'ять|пʼять|пять|шість|сім|вісім|дев'ять|девʼять|девять|десять|п'ятнадцять|пʼятнадцять|пятнадцять|двадцять|тридцять|сорок|сорок п'ять|сорок пʼять|п'ятдесят|пʼятдесят)\\s*(секунд(?:у|и|ів)?|сек|с|хвилин(?:у|и)?|хв|годин(?:у|и)?|год)(?=$|\\s|[.,!?])",
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
     * Canonicalize only the FIRST imperative, never the user's dictated
     * text, playlist name or note contents.
     * «включи» / «увімкни» and «виключи» / «вимкни» are
     * equivalent control verbs.
     */
    public static String normalizeControlVerb(String raw) {
        String normalized = normalize(raw);
        return normalized.replaceFirst("^(?:включи|ввімкни)(?=\\s|$)", "увімкни")
            .replaceFirst("^виключи(?=\\s|$)", "вимкни");
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

    /** A short explicit timer request should never fall through to AI. */
    public static boolean timerIntent(String raw) {
        String s=normalize(raw);
        return s.matches("^(?:(?:постав|поставити|встанови|встановити|запусти|увімкни|включи|заведи)\\s+)?таймер(?:\\s+.*)?$");
    }

    private static int timerNumber(String raw) {
        switch(raw) {
            case "один": case "одна": case "одну": return 1;
            case "два": case "дві": return 2;
            case "три": return 3;
            case "чотири": return 4;
            case "п'ять": case "пʼять": case "пять": return 5;
            case "шість": return 6;
            case "сім": return 7;
            case "вісім": return 8;
            case "дев'ять": case "девʼять": case "девять": return 9;
            case "десять": return 10;
            case "п'ятнадцять": case "пʼятнадцять": case "пятнадцять": return 15;
            case "двадцять": return 20;
            case "тридцять": return 30;
            case "сорок": return 40;
            case "сорок п'ять": case "сорок пʼять": return 45;
            case "п'ятдесят": case "пʼятдесят": return 50;
            default:
                try {return Integer.parseInt(raw);}
                catch(NumberFormatException error){return -1;}
        }
    }

    /** Null when no valid timer command matches; -1 for invalid duration. */
    public static Integer timerSeconds(String raw) {
        if(!timerIntent(raw))return null;
        String command=normalize(raw);
        Matcher m=TIMER.matcher(command);
        if(!m.find()) {
            // Speech recognition often drops the numeral in "на хвилину".
            if(command.matches(".*\\bтаймер\\s+(?:на\\s+)?хвилину$"))
                return 60;
            return -1;
        }
        long number=timerNumber(m.group(1));
        String unit=m.group(2);
        long factor=unit.startsWith("год")?3600:unit.startsWith("хв")?60:1;
        long seconds=number*factor;
        if(seconds<1 || seconds>10800)return -1;
        return (int)seconds;
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
