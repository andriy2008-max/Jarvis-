package com.andriy.jarvis;

public final class ScreenTextInsertionTest {
    private static int checks;
    private static void eq(Object got, Object want) {
        if (got == null ? want != null : !got.equals(want))
            throw new AssertionError("Expected [" + want + "], got [" + got + "]");
        checks++;
    }
    private static void insertion(String current, int from, int to, String said,
                                  String expected, int caret) {
        ScreenTextInsertion.Plan plan =
            ScreenTextInsertion.plan(current, from, to, said);
        eq(plan.result, expected);
        eq(plan.cursor, caret);
    }
    private static void rejects(String current, int from, int to, String said) {
        try {
            ScreenTextInsertion.plan(current, from, to, said);
            throw new AssertionError("Expected refusal");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
    }
    public static void main(String[] args) {
        insertion("", 0, 0, "Привіт, як справи?", "Привіт, як справи?", 18);
        insertion(null, -1, -1, "Тест!", "Тест!", 5);
        insertion("До побачення", 2, 2, "брого", "Доброго побачення", 7);
        insertion("Привіт світ", 7, 11, "Україно!", "Привіт Україно!", 15);
        insertion("Нотатка", 7, 7, " 42", "Нотатка 42", 10);
        insertion("Стара фраза", 0, 11, "Нова", "Нова", 4);
        insertion("🇺🇦!", 5, 5, " Мир", "🇺🇦! Мир", 9);

        rejects("Вже є текст", -1, -1, "Ще");
        rejects("AB", 2, 1, "X");
        rejects("AB", 3, 3, "X");
        rejects("AB", 0, 3, "X");
        rejects("", 0, 0, "");
        rejects("", 0, 0, "    ");
        rejects("", 0, 0, "x".repeat(ScreenTextInsertion.MAX_DICTATION_LENGTH + 1));
        rejects("x".repeat(ScreenTextInsertion.MAX_FIELD_LENGTH), 0, 0, "y");
        rejects("", 0, 0, "a\u0000b");
        rejects("😀", 1, 1, "X");
        System.out.println("PASS " + checks + " screen text insertion assertions");
    }
}
