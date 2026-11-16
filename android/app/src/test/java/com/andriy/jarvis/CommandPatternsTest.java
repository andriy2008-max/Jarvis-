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
        eq(CommandPatterns.spokenText("Джарвіс скажи Доброго вечора, друзі!"),
            "Доброго вечора, друзі!");
        eq(CommandPatterns.spokenText("скажи Я залишаюся відкритим на Watch7."),
            "Я залишаюся відкритим на Watch7.");
        eq(CommandPatterns.spokenText("Скажи Привіт українською й англійською"),
            "Привіт українською й англійською");
        eq(CommandPatterns.spokenText("Увімкни плейлист Скажи мені"),null);
        eq(CommandPatterns.spokenText("Напиши Скажи мені"),null);
        eq(CommandPatterns.spokenText("Скажи"), "");
        eq(CommandPatterns.spokenText("Скажи: "), "");
        eq(CommandPatterns.spokenText("скажи замовкни"), "замовкни");
        eq(CommandPatterns.spokenText("розкажи, що нового?"), null);
        eq(CommandPatterns.spokenText("Скажище привіт"), null);
        eq(CommandPatterns.spokenText(null), null);
        eq(CommandPatterns.timerSeconds("постав таймер на 5 хвилин"), 300);
        eq(CommandPatterns.timerSeconds("Постав таймер на 1 хвилину"),60);
        eq(CommandPatterns.timerSeconds("Джарвіс, постав таймер на одну хвилину!"),60);
        eq(CommandPatterns.timerSeconds("постав таймер на хвилину"),60);
        eq(CommandPatterns.timerSeconds("увімкни таймер на дві хвилини"),120);
        eq(CommandPatterns.timerSeconds("постав таймер на п'ять хвилин"),300);
        eq(CommandPatterns.timerSeconds("постав таймер на сорок п'ять секунд"),45);
        eq(CommandPatterns.timerSeconds("постав таймер на 4 хвилини"),240);
        eq(CommandPatterns.timerSeconds("постав таймер на 10000 хвилин"),-1);
        eq(CommandPatterns.timerSeconds("постав таймер без часу"),-1);
        eq(CommandPatterns.timerIntent("Постав таймер на 1 хвилину"),true);
        eq(CommandPatterns.timerIntent("увімкни таймер"),true);
        eq(CommandPatterns.timerIntent("скасуй таймер"),false);
        // Watch timers must remain on Watch7: cancel and inspect the same
        // local AlarmManager, never the paired Pixel timer.
        eq(CommandPatterns.timerCancelIntent("скасуй таймер"),true);
        eq(CommandPatterns.timerCancelIntent("Джарвіс, зупини таймер"),true);
        eq(CommandPatterns.timerCancelIntent("вимкни таймер"),true);
        eq(CommandPatterns.timerCancelIntent("постав таймер на хвилину"),false);
        eq(CommandPatterns.timerRemainingIntent("скільки залишилось до кінця таймера"),true);
        eq(CommandPatterns.timerRemainingIntent("скільки часу на таймері"),true);
        eq(CommandPatterns.timerRemainingIntent("покажи таймер"),true);
        eq(CommandPatterns.timerRemainingIntent("постав таймер на 1 хвилину"),false);
        eq(CommandPatterns.timerRemainingIntent("скільки часу на телефоні"),false);
        eq(CommandPatterns.timerIntent("нагадай про таймер"),false);
        eq(CommandPatterns.timerSeconds("прочитай нотатку таймер на 1 хвилину"),null);
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
