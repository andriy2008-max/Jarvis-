package com.andriy.jarvis;

import java.util.Locale;

/** Routes explicit news requests to a live feed rather than model guesses. */
public final class WorldNewsCommands {
    private WorldNewsCommands() {}

    public static boolean requestsWorldNews(String raw) {
        if (raw == null) return false;
        String s = CommandPatterns.normalize(raw).toLowerCase(Locale.forLanguageTag("uk-UA"))
            .replaceAll("[!?.,]+$", "").replaceAll("\\s+", " ").trim();
        if (s.equals("що нового у світі") || s.equals("що нового в світі") ||
            s.equals("які новини у світі") || s.equals("які новини в світі"))
            return true;
        for (int i = 0; i < 7; i++) {
            String next = s.replaceFirst(
                "^(?:знайди|покажи|розкажи|прочитай|озвуч|озвучити|зачитай|скажи|дай|повідом|які|мені|будь ласка|зараз)\\s+", "");
            if (next.equals(s)) break;
            s = next;
        }
        s = s.replaceFirst("\\s+(?:і\\s+)?(?:озвуч|прочитай|зачитай)(?:\\s+їх|\\s+мені)?$", "");
        s = s.replaceFirst("^(?:останні|свіжі|нові|головні|найновіші|актуальні)\\s+", "");
        return s.equals("новини") || s.equals("новини світу") ||
            s.equals("новини у світі") || s.equals("новини в світі") ||
            s.equals("світові новини") || s.equals("міжнародні новини");
    }
}
