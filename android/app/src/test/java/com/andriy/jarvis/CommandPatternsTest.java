package com.andriy.jarvis;

public final class CommandPatternsTest {
    private static int assertions = 0;
    private static void eq(Object actual, Object expected) {
        if (actual == null ? expected != null : !actual.equals(expected)) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
        assertions++;
    }
    public static void main(String[] args) {
        eq(CommandPatterns.normalize("Джарвіс, Таймер на 5 хвилин!"), "таймер на 5 хвилин");
        eq(CommandPatterns.normalize("Jarvis: ЗНАЙДИ КОТА."), "знайди кота");
        eq(CommandPatterns.normalizeControlVerb("Джарвіс, включи ліхтарик!"), "увімкни ліхтарик");
        eq(CommandPatterns.normalizeControlVerb("Джарвіс, увімкни ліхтарик!"), "увімкни ліхтарик");
        eq(CommandPatterns.normalizeControlVerb("виключи wifi"), "вимкни wifi");
        eq(CommandPatterns.normalizeControlVerb("вимкни wifi"), "вимкни wifi");
        eq(CommandPatterns.normalizeControlVerb("ввімкни голос"), "увімкни голос");
        eq(CommandPatterns.normalizeControlVerb("увімкни плейлист Включи світло"),
            "увімкни плейлист включи світло");
        eq(CommandPatterns.normalizeControlVerb("напиши включи ліхтарик"),
            "напиши включи ліхтарик");
        eq(CommandPatterns.normalizeControlVerb("розкажи про виключи звук"),
            "розкажи про виключи звук");
        eq(CommandPatterns.spokenText("Скажи Привіт!"), "Привіт!");
        eq(CommandPatterns.spokenText("Джарвіс, скажи: Я тут. Як справи?"), "Я тут. Як справи?");
        eq(CommandPatterns.spokenText("Jarvis — СКАЖИ 100%"), "100%");
        eq(CommandPatterns.spokenText("Скажи"), "");
        eq(CommandPatterns.spokenText("Скажи: "), "");
        eq(CommandPatterns.spokenText("скажи замовкни"), "замовкни");
        eq(CommandPatterns.spokenText("розкажи, що нового?"), null);
        eq(CommandPatterns.spokenText("Скажище привіт"), null);
        eq(CommandPatterns.spokenText(null), null);
        eq(CommandPatterns.timerSeconds("постав таймер на 5 хвилин"), 300);
        eq(CommandPatterns.timerSeconds("таймер 30 секунд"), 30);
        eq(CommandPatterns.timerSeconds("таймер на 1 годину"), 3600);
        eq(CommandPatterns.timerSeconds("таймер на 3 години"), 10800);
        eq(CommandPatterns.timerSeconds("таймер 4 години"), -1);
        eq(CommandPatterns.timerSeconds("таймер на 0 секунд"), -1);
        eq(CommandPatterns.timerSeconds("запиши купити молоко"), null);
        eq(CommandPatterns.searchTerms("знайди рецепти борщу"), "рецепти борщу");
        eq(CommandPatterns.searchTerms("пошукай в інтернеті прогноз погоди"), "прогноз погоди");
        eq(CommandPatterns.searchTerms("привіт"), null);
        eq(CommandPatterns.dialNumber("подзвони +380 (67) 123-45-67"), "+380671234567");
        eq(CommandPatterns.dialNumber("набери номер 050 123 45 67"), "0501234567");
        eq(CommandPatterns.dialNumber("подзвони 123"), null);
        eq(CommandPatterns.dialNumber("подзвони +380671234567;rm"), null);
        eq(CommandPatterns.noteIndex("видали нотатку 2"), 2);
        eq(CommandPatterns.noteIndex("видали нотатку 0"), 0);
        eq(CommandPatterns.noteIndex("видали всі нотатки"), null);
        System.out.println("PASS " + assertions + " parser assertions");
    }
}
