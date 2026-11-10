package com.andriy.jarvis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strict, local grammar for sequential, explicitly spoken UI commands.
 * No open-ended AI action planning and no arbitrary or unsafe screen actions.
 */
public final class JarvisCommandSequence {
    public enum Kind { OPEN_APP, CLICK, TYPE_TEXT, MEDIA_VOLUME }

    public static final class Step {
        public final Kind kind;
        public final String text;
        public final int volume;
        private Step(Kind kind, String text, int volume) {
            this.kind = kind;
            this.text = text;
            this.volume = volume;
        }
    }

    public static final class Plan {
        public final List<Step> steps;
        public final String error;
        public boolean valid() { return error == null && !steps.isEmpty(); }
        public boolean multiple() { return steps.size() > 1; }
        private Plan(List<Step> steps, String error) {
            this.steps = Collections.unmodifiableList(steps);
            this.error = error;
        }
    }

    public static final int MAX_STEPS = 6;

    private static final Pattern WAKE = Pattern.compile(
        "^(?:(?:джарвіс|джарвис|jarvis)[\\s,:—-]+)", Pattern.CASE_INSENSITIVE |
        Pattern.UNICODE_CASE);

    // Splits only at a recognized *next command*, never just at "і".
    // Therefore "напиши Добрий день і гарного настрою" stays literal text.
    private static final Pattern NEXT = Pattern.compile(
        "(?iu)\\s+(?:(?:і|а|потім|тоді|далі|після цього)\\s+)*(?=" +
        "(?:відкрий|запусти|натисни|нажми|торкнись|торкнися|напиши)\\s+" +
        "|(?:постав|встанови|зроби|зміни|задай)\\s+(?:медіа\\s*)?(?:гучність|звук)\\b)");

    private JarvisCommandSequence() {}

    public static Plan parse(String original) {
        if (original == null) return null;
        String input = WAKE.matcher(original.trim()).replaceFirst("").trim();
        if (input.isEmpty() || input.length() > 1300) return null;
        String[] segments = NEXT.split(input, -1);
        if (segments.length > MAX_STEPS)
            return new Plan(Collections.emptyList(),
                "Забагато дій у команді. Максимум шість кроків за один раз.");
        List<Step> steps = new ArrayList<>();
        for (String segment : segments) {
            String part = segment.trim().replaceFirst(
                "(?iu)^(?:(?:і|а|потім|тоді|далі|після цього)\\s+)", "").trim();
            if (part.isEmpty())
                return new Plan(Collections.emptyList(),
                    "Не вдалося розділити команди. Назви дії окремо.");
            Step step = parseStep(part);
            if (step == null) {
                if (segments.length == 1) return null; // Other Jarvis commands.
                return new Plan(Collections.emptyList(),
                    "Не розумію крок «" + shorten(part) + "». Жодну дію не починав.");
            }
            steps.add(step);
        }
        // Must not accidentally send a single app command through the screen
        // flow unless the caller intentionally opts into this parser.
        return new Plan(steps, null);
    }

    private static String shorten(String part) {
        return part.length() > 70 ? part.substring(0, 70) + "…" : part;
    }

    private static Step parseStep(String part) {
        ScreenControlCommands.Action screen = ScreenControlCommands.parse(part);
        if (screen != null) {
            if (screen.kind == ScreenControlCommands.Kind.CLICK &&
                !screen.label.isEmpty() &&
                !ScreenControlCommands.isSensitiveLabel(screen.label))
                return new Step(Kind.CLICK, screen.label, -1);
            if (screen.kind == ScreenControlCommands.Kind.TYPE_TEXT &&
                !screen.label.trim().isEmpty() &&
                screen.label.length() <= ScreenTextInsertion.MAX_DICTATION_LENGTH)
                return new Step(Kind.TYPE_TEXT, screen.label, -1);
            return null;
        }
        String normalized = part.toLowerCase(Locale.forLanguageTag("uk-UA"))
            .replaceAll("\\s+", " ").trim();
        String app = AssistantIntents.requestedApp(normalized);
        if (app != null) app = app.replaceAll("[,;.!?]+$", "").trim();
        if (app != null && !app.isEmpty() &&
            !ScreenControlCommands.isSensitiveLabel(app))
            return new Step(Kind.OPEN_APP, app, -1);

        DeviceCommands.Action device = DeviceCommands.parse(normalized);
        if (device != null && device.kind == DeviceCommands.Kind.VOLUME_PERCENT &&
            device.percent >= 0 && device.percent <= 100)
            return new Step(Kind.MEDIA_VOLUME, "", device.percent);
        return null;
    }
}
