package com.andriy.jarvis;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Local intent-to-accessibility matching. Does not use screenshots or AI.
 * A single high-confidence actionable node is required; never guess from a
 * generic icon with no accessible label, description, or resource ID.
 */
public final class ScreenTargetMatcher {
    public static final int MIN_CONFIDENCE = 78;
    public static final int MIN_MARGIN = 14;

    // Stems cover Ukrainian case inflections and frequent English content descriptions.
    private static final String[][] ALIASES = {
        {"search", "пошук", "пошу", "шука", "луп", "search", "magnif"},
        {"chats", "чат", "діалог", "листуван", "переписк", "chats", "chat", "conversation"},
        {"messages", "повідомлен", "вхідн", "inbox", "messages", "message", "direct"},
        {"home", "головн", "домівк", "home", "dashboard"},
        {"profile", "профіл", "акаунт", "обліков", "profile", "account", "avatar"},
        {"menu", "меню", "бургер", "три крапк", "додатков", "menu", "overflow", "ellipsis", "more"},
        {"settings", "налаштуван", "параметр", "шестер", "settings", "preferences", "gear", "cog"},
        {"notifications", "сповіщен", "дзвіноч", "notification", "notifications", "bell"},
        {"photos", "галере", "фотограф", "фот", "зображен", "gallery", "photos", "images"},
        {"camera", "камер", "фотографува", "camera", "photo_camera"},
        {"attachment", "скріпк", "вкладен", "прикріп", "attachment", "attach", "paperclip"},
        {"favorites", "обран", "улюблен", "зірочк", "favorites", "bookmarks", "star"},
        {"shuffle", "переміш", "перемеш", "shuffle", "перехрещені стріл", "перехрещених стріл", "схрещені стріл", "схрещених стріл", "crossed arrows", "випадковий порядок"},
        {"play", "відтвор", "програ", "плей", "play"},
        {"pause", "пауз", "pause"},
        {"back", "поверн", "назад", "back", "arrow_back"},
        {"close", "закрий", "закрити", "хрестик", "close", "dismiss"}
    };

    public static final class Target {
        public final String text;
        public final String description;
        public final String resourceId;
        public final String parentText;
        public final String parentDescription;
        public final String parentResourceId;

        public Target(String text, String description, String resourceId) {
            this(text, description, resourceId, "", "", "");
        }

        public Target(String text, String description, String resourceId,
                      String parentText, String parentDescription, String parentResourceId) {
            this.text = text;
            this.description = description;
            this.resourceId = resourceId;
            this.parentText = parentText;
            this.parentDescription = parentDescription;
            this.parentResourceId = parentResourceId;
        }
    }

    public static final class Selection {
        /** -1 means no safe unique button. */
        public final int index;
        public final int confidence;
        public final boolean ambiguous;
        Selection(int index, int confidence, boolean ambiguous) {
            this.index = index;
            this.confidence = confidence;
            this.ambiguous = ambiguous;
        }
    }

    private ScreenTargetMatcher() {}

    private static String normal(String source) {
        if (source == null) return "";
        return source.replaceAll("([a-zа-яіїєґ])([A-Z])", "$1 $2")
            .toLowerCase(Locale.forLanguageTag("uk-UA"))
            .replace('’', '\'').replace('ʼ', '\'')
            .replaceAll("([a-zа-яіїєґ])([A-Z])", "$1 $2")
            .replaceAll("[^a-z0-9а-яіїєґ]+", " ").trim().replaceAll("\\s+", " ");
    }

    public static String intent(String phrase) {
        String p = normal(phrase);
        if (p.isEmpty()) return "";
        for (String[] group : ALIASES) {
            for (int i = 1; i < group.length; i++) {
                if (containsAlias(p, group[i])) return group[0];
            }
        }
        return "";
    }

    private static boolean containsAlias(String text, String alias) {
        if (text.isEmpty() || alias.isEmpty()) return false;
        for (String part : text.split(" ")) {
            if (part.equals(alias) || (!alias.equals("play") && alias.length() >= 3 && part.startsWith(alias)))
                return true;
        }
        // Multi-word phrases such as "три крапки" are valid when all words appear.
        if (alias.indexOf(' ') >= 0) return text.contains(alias);
        return false;
    }

    public static boolean dangerous(Target target) {
        if (target == null) return true;
        // Only evaluate action-bearing control metadata, not unrelated paragraphs.
        for (String value : new String[]{
            target.text, target.description, target.resourceId,
            target.parentText, target.parentDescription, target.parentResourceId}) {
            if (value != null && !value.trim().isEmpty() &&
                ScreenControlCommands.isSensitiveLabel(value)) return true;
        }
        return false;
    }

    private static boolean mentionsIntent(String field, String groupName) {
        for (String[] aliases : ALIASES) {
            if (!aliases[0].equals(groupName)) continue;
            for (int i = 1; i < aliases.length; i++) {
                if (containsAlias(field, aliases[i])) return true;
            }
        }
        return false;
    }

    private static int fieldScore(String request, String requestedIntent,
                                  String field, int exactScore, int semanticScore) {
        String value = normal(field);
        if (value.isEmpty()) return 0;
        if (requestedIntent.equals("shuffle") &&
            !SpotifyUiLabels.matches(SpotifyUiLabels.Target.SHUFFLE, field)) return 0;
        if (requestedIntent.equals("play") &&
            SpotifyUiLabels.matches(SpotifyUiLabels.Target.SHUFFLE, field)) return 0;
        if (value.equals(request)) return exactScore;
        // A "button search", "icon search", or "пошук у чатах" descriptor may be
        // longer than the request yet still name exactly its UI action.
        if (!requestedIntent.isEmpty() && mentionsIntent(value, requestedIntent))
            return semanticScore;
        if (request.length() >= 4 && (" " + value + " ").contains(" " + request + " "))
            return Math.max(MIN_CONFIDENCE, semanticScore - 8);
        return 0;
    }

    /** Score one control, but never accept an unsafe action based on its icon ID. */
    public static int score(String request, Target target) {
        if (target == null || request == null || request.trim().isEmpty()) return 0;
        if (ScreenControlCommands.isSensitiveLabel(request) || dangerous(target)) return 0;
        String normalized = normal(request);
        String kind = intent(normalized);
        int score = 0;
        score = Math.max(score, fieldScore(normalized, kind, target.text, 110, 94));
        score = Math.max(score, fieldScore(normalized, kind, target.description, 108, 93));
        score = Math.max(score, fieldScore(normalized, kind, target.resourceId, 102, 87));
        score = Math.max(score, fieldScore(normalized, kind, target.parentText, 98, 84));
        score = Math.max(score, fieldScore(normalized, kind, target.parentDescription, 98, 84));
        score = Math.max(score, fieldScore(normalized, kind, target.parentResourceId, 92, 82));
        return score;
    }

    public static Selection select(String requested, List<Target> targets) {
        if (targets == null || requested == null) return new Selection(-1, 0, false);
        int best = -1, bestScore = 0, runnerUp = 0;
        for (int i = 0; i < targets.size(); i++) {
            int score = score(requested, targets.get(i));
            if (score > bestScore) {
                runnerUp = bestScore;
                bestScore = score;
                best = i;
            } else if (score > runnerUp) {
                runnerUp = score;
            }
        }
        boolean ambiguous = bestScore >= MIN_CONFIDENCE &&
            runnerUp >= MIN_CONFIDENCE && bestScore - runnerUp < MIN_MARGIN;
        if (bestScore < MIN_CONFIDENCE || ambiguous)
            return new Selection(-1, bestScore, ambiguous);
        return new Selection(best, bestScore, false);
    }

    public static List<Target> examples(Target... targets) {
        List<Target> values = new ArrayList<>();
        for (Target value : targets) values.add(value);
        return values;
    }
}
