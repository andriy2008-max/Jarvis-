package com.andriy.jarvis;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Matches any launcher-visible Android application by its displayed label.
 * No package-name allowlist, QUERY_ALL_PACKAGES permission, or fuzzy guessing when ambiguous.
 * This class has no Android dependencies so its matching can be tested without building APKs.
 */
public final class AppNameMatcher {
    public static final class App {
        public final String packageName;
        public final String activityName;
        public final String label;

        public App(String packageName, String activityName, String label) {
            this.packageName = packageName;
            this.activityName = activityName;
            this.label = label;
        }
    }

    public static final class Result {
        public final App app;
        public final List<App> alternatives;

        private Result(App app, List<App> alternatives) {
            this.app = app;
            this.alternatives = alternatives;
        }

        public boolean ambiguous() { return app == null && !alternatives.isEmpty(); }
        public boolean found() { return app != null; }
    }

    private static final class Scored {
        final App app;
        final int score;
        Scored(App app, int score) { this.app = app; this.score = score; }
    }

    private AppNameMatcher() { }

    /** Normalizes punctuation, spaces, case and Unicode accent differences. */
    public static String normalize(String text) {
        if (text == null) return "";
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT).trim(), Normalizer.Form.NFKD)
            .replaceAll("\\p{M}+", "")
            .replaceAll("[^\\p{L}\\p{N}]+", " ")
            .trim().replaceAll(" +", " ");
    }

    /** Approximate Ukrainian/Russian -> Latin transliteration for voice recognition results. */
    private static String transliterate(String text) {
        StringBuilder result = new StringBuilder();
        String source = text.toLowerCase(Locale.ROOT);
        for (int i = 0; i < source.length(); i++) {
            switch (source.charAt(i)) {
                case 'а': result.append('a'); break;
                case 'б': result.append('b'); break;
                case 'в': result.append('v'); break;
                case 'г': result.append('h'); break;
                case 'ґ': result.append('g'); break;
                case 'д': result.append('d'); break;
                case 'е': case 'є': case 'э': result.append('e'); break;
                case 'ж': result.append("zh"); break;
                case 'з': result.append('z'); break;
                case 'и': case 'і': case 'ї': case 'ы': result.append('i'); break;
                case 'й': result.append('y'); break;
                case 'к': result.append('k'); break;
                case 'л': result.append('l'); break;
                case 'м': result.append('m'); break;
                case 'н': result.append('n'); break;
                case 'о': result.append('o'); break;
                case 'п': result.append('p'); break;
                case 'р': result.append('r'); break;
                case 'с': result.append('s'); break;
                case 'т': result.append('t'); break;
                case 'у': result.append('u'); break;
                case 'ф': result.append('f'); break;
                case 'х': result.append("kh"); break;
                case 'ц': result.append("ts"); break;
                case 'ч': result.append("ch"); break;
                case 'ш': result.append("sh"); break;
                case 'щ': result.append("shch"); break;
                case 'ю': result.append("yu"); break;
                case 'я': result.append("ya"); break;
                case 'ь': case 'ъ': case 'ʼ': case '\'': break;
                default: result.append(source.charAt(i));
            }
        }
        return normalize(result.toString());
    }

    private static int distanceWithin(String a, String b, int limit) {
        if (Math.abs(a.length() - b.length()) > limit) return limit + 1;
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            int best = current[0];
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(previous[j] + 1, current[j - 1] + 1), previous[j - 1] + cost);
                if (current[j] < best) best = current[j];
            }
            if (best > limit) return limit + 1;
            int[] tmp = previous; previous = current; current = tmp;
        }
        return previous[b.length()];
    }

    private static int matchScore(String search, String label) {
        if (search.isEmpty() || label.isEmpty()) return 0;
        if (search.equals(label)) return 100;
        // Speech services often insert/remove spaces in brand names.
        if (search.replace(" ", "").equals(label.replace(" ", ""))) return 96;
        // Full-word prefix/suffix and multi-word app labels (e.g. "Lightroom" in "Adobe Lightroom").
        if (search.length() >= 4 && (label.startsWith(search + " ") || label.endsWith(" " + search))) return 83;
        if (search.length() >= 4 && label.contains(" " + search + " ")) return 80;
        if (search.length() >= 5 && label.startsWith(search)) return 75;
        // Conservative typo handling: only a single edit and no automatic ties.
        if (search.length() >= 5 && label.length() >= 5 && distanceWithin(search, label, 1) == 1) return 65;
        return 0;
    }

    /** @return exact/best unique match, ambiguity with suggested labels, or empty result. */
    public static Result match(String spokenName, List<App> launchers) {
        String entered = normalize(spokenName);
        String alias = normalize(AssistantIntents.appLabel(spokenName));
        String romanized = transliterate(spokenName == null ? "" : spokenName);
        if (entered.isEmpty() || launchers == null || launchers.isEmpty()) {
            return new Result(null, new ArrayList<>());
        }

        Map<String, Scored> bestByPackage = new LinkedHashMap<>();
        for (App app : launchers) {
            if (app == null || app.packageName == null || app.packageName.isEmpty()) continue;
            String label = normalize(app.label);
            int score = matchScore(entered, label);
            if (!alias.equals(entered)) score = Math.max(score, Math.min(98, matchScore(alias, label)));
            if (!romanized.equals(entered) && !romanized.equals(alias)) {
                score = Math.max(score, Math.min(93, matchScore(romanized, label)));
            }
            if (score == 0) continue;
            Scored previous = bestByPackage.get(app.packageName);
            if (previous == null || score > previous.score) {
                bestByPackage.put(app.packageName, new Scored(app, score));
            }
        }

        int highest = 0;
        for (Scored entry : bestByPackage.values()) highest = Math.max(highest, entry.score);
        if (highest == 0) return new Result(null, new ArrayList<>());

        List<Scored> top = new ArrayList<>();
        for (Scored entry : bestByPackage.values()) {
            if (entry.score == highest) top.add(entry);
        }
        if (top.size() == 1) return new Result(top.get(0).app, new ArrayList<>());
        List<App> alternatives = new ArrayList<>();
        for (Scored entry : top) {
            alternatives.add(entry.app);
            if (alternatives.size() == 6) break;
        }
        return new Result(null, alternatives);
    }
}
