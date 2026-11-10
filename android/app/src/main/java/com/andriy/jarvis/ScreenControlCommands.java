package com.andriy.jarvis;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deliberate, single-step Android UI actions requested verbatim by the user. */
public final class ScreenControlCommands {
    public enum Kind { ENABLE, DISABLE, SETTINGS, CLICK, TYPE_TEXT, SCROLL_UP, SCROLL_DOWN, BACK, STATUS }

    public static final class Action {
        public final Kind kind;
        public final String label;
        Action(Kind kind, String label) { this.kind = kind; this.label = label; }
    }

    private static final Pattern WRITE = Pattern.compile(
        "^(?iu)напиши(?:[\\s,:—-]+([\\s\\S]*))?$");
    private static final Pattern WAKE = Pattern.compile(
        "^(?iu)(?:джарвіс|джарвис|jarvis)[\\s,:—-]*");

    private static final Pattern CLICK = Pattern.compile(
        "^(?:натисни|нажми|торкнись|торкнися)(?:\\s+на)?(?:\\s+(?:кнопку|значок|іконку|ікону|символ))?\\s+(.+)$",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern NAVIGATE = Pattern.compile(
        "^(?:відкрий|покажи|знайди|зайди\\s+(?:в|у|до)|перейди\\s+(?:до|в|у|на)|де\\s+тут|"
        + "хочу\\s+відкрити|хочу\\s+знайти|мені\\s+потрібн(?:а|о|ий|і))\\s+(.+)$",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private ScreenControlCommands() {}

    public static Action parse(String normalized) {
        if (normalized == null) return null;
        // Extract dictated payload BEFORE normalization. Otherwise casing and
        // punctuation would be lost, and the contents could be sent to the AI.
        String original = WAKE.matcher(normalized.trim()).replaceFirst("").trim();
        Matcher write = WRITE.matcher(original);
        if (write.matches()) {
            String payload = write.group(1) == null ? "" : write.group(1).trim();
            return new Action(Kind.TYPE_TEXT, payload);
        }
        String s = original.toLowerCase(Locale.forLanguageTag("uk-UA"))
            .replaceAll("[.!?]+$", "").trim().replaceAll("\\s+", " ");
        switch (s) {
            case "увімкни керування екраном":
            case "включи керування екраном":
            case "почни керування екраном":
                return new Action(Kind.ENABLE, null);
            case "вимкни керування екраном":
            case "зупини керування екраном":
            case "джарвіс зупинись":
            case "зупинись":
                return new Action(Kind.DISABLE, null);
            case "налаштуй керування екраном":
            case "налаштування керування екраном":
            case "доступ до екрана":
                return new Action(Kind.SETTINGS, null);
            case "чи працює керування екраном":
            case "стан керування екраном":
                return new Action(Kind.STATUS, null);
            case "прокрути вниз":
            case "гортай вниз":
            case "пролистай вниз":
                return new Action(Kind.SCROLL_DOWN, null);
            case "прокрути вгору":
            case "гортай вгору":
            case "пролистай вгору":
                return new Action(Kind.SCROLL_UP, null);
            case "назад":
            case "повернись назад":
            case "повернися назад":
                return new Action(Kind.BACK, null);
            default:
                Matcher m = CLICK.matcher(s);
                if (!m.matches()) {
                    // Only infer a navigation click for a RECOGNIZED destination.
                    // "Відкрий Telegram" must continue to use Android app launching,
                    // not be intercepted as an untrusted screen action.
                    m = NAVIGATE.matcher(s);
                    if (!m.matches()) return null;
                    String goal = m.group(1).trim();
                    if (ScreenTargetMatcher.intent(goal).isEmpty()) return null;
                    if (isSensitiveLabel(goal)) return new Action(Kind.CLICK, "");
                    return new Action(Kind.CLICK, goal);
                }
                String label = m.group(1).trim();
                if (label.isEmpty() || label.length() > 90 ||
                    isSensitiveLabel(label))
                    return new Action(Kind.CLICK, "");
                return new Action(Kind.CLICK, label);
        }
    }

    /**
     * Initial release is read-only for potentially consequential actions.
     * Do not let a generic affirmative/submit button bypass confirmation.
     */
    public static boolean isSensitiveLabel(String label) {
        if (label == null) return true;
        String s = label.toLowerCase(Locale.forLanguageTag("uk-UA")).trim();
        String[] deny = {
            "оплат", "платіж", "платеж", "купити", "покуп", "переказ", "перевест",
            "відправ", "надісл", "видал", "стерти", "очисти", "скасуй",
            "підтверд", "погодж", "зберегти", "здійсн", "замов", "оформ",
            "підписат", "підписка", "установ", "встанов", "дозволити",
            "allow", "confirm", "submit", "send", "delete", "remove", "purchase",
            "pay", "checkout", "transfer", "subscribe", "install", "agree"
        };
        if (s.equals("так") || s.equals("ok") || s.equals("okay") ||
            s.equals("yes") || s.equals("продовжити") || s.equals("готово")) return true;
        for (String phrase : deny) if (s.contains(phrase)) return true;
        return false;
    }
}
