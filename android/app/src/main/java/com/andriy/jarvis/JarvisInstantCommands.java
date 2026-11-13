package com.andriy.jarvis;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Only exact, stand-alone one/two-word phrases. Never use startsWith:
 * unfinished text, playlist names, multi-step instructions, Telegram and
 * dictation MUST retain the configured after-last-word silence timeout.
 */
public final class JarvisInstantCommands {
    private static final Set<String> EXACT=Collections.unmodifiableSet(new HashSet<>(
        Arrays.asList(
            "назад", "гучніше", "тихіше", "досить",
            "наступна пісня", "наступний трек",
            "попередня пісня", "попередній трек",
            "пауза музики", "пауза пісні",
            "продовж музику", "віднови музику",
            "вимкни музику", "увімкни музику",
            "увімкни реп", "включи реп", "увімкни українське",
            "увімкни ліхтарик", "ввімкни ліхтарик", "включи ліхтарик",
            "вимкни ліхтарик", "виключи ліхтарик",
            "увімкни звук", "вимкни звук",
            "покажи нотатки", "покажи будильники",
            "скасуй таймер", "зупини таймер", "вимкни таймер"
        )
    ));

    private JarvisInstantCommands() {}

    public static boolean isComplete(String utterance) {
        if(utterance==null)return false;
        String text=CommandPatterns.normalizeControlVerb(utterance)
            .replaceAll("\\s+"," ").trim();
        if(text.isEmpty())return false;
        int words=text.split(" ").length;
        return words<=2 && EXACT.contains(text);
    }

    public static int stableDelayMs() { return 320; }
}
