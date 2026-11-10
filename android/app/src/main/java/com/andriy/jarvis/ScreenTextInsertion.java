package com.andriy.jarvis;

/**
 * Pure, deterministic text editing plan for an explicitly focused input.
 * No clipboard access and no implicit submit/send action.
 */
public final class ScreenTextInsertion {
    public static final int MAX_DICTATION_LENGTH = 1000;
    public static final int MAX_FIELD_LENGTH = 10000;

    public static final class Plan {
        public final String result;
        public final int cursor;
        Plan(String result, int cursor) {
            this.result = result;
            this.cursor = cursor;
        }
    }

    private ScreenTextInsertion() {}

    /**
     * Insertion is made at the cursor or replaces the explicitly selected range.
     * A field with existing content but unavailable cursor information is left untouched.
     */
    public static Plan plan(String current, int selectionStart, int selectionEnd,
                            String spoken) {
        if (spoken == null || spoken.trim().isEmpty())
            throw new IllegalArgumentException("Назви текст після команди «Напиши».");
        if (spoken.length() > MAX_DICTATION_LENGTH)
            throw new IllegalArgumentException("Фраза занадто довга: максимум 1000 символів.");
        if (spoken.indexOf('\u0000') >= 0)
            throw new IllegalArgumentException("Текст містить неприпустимі символи.");
        if (current == null) current = "";
        if (current.length() > MAX_FIELD_LENGTH)
            throw new IllegalArgumentException("У цьому полі вже занадто багато тексту.");

        if (selectionStart < 0 || selectionEnd < 0) {
            if (current.isEmpty()) {
                selectionStart = 0;
                selectionEnd = 0;
            } else {
                throw new IllegalArgumentException(
                    "Не бачу положення курсора. Торкнися текстового поля та повтори.");
            }
        }
        if (selectionStart > selectionEnd || selectionEnd > current.length())
            throw new IllegalArgumentException("Положення курсора змінилося. Повтори команду.");
        if ((selectionStart > 0 && selectionStart < current.length() &&
                Character.isHighSurrogate(current.charAt(selectionStart - 1)) &&
                Character.isLowSurrogate(current.charAt(selectionStart))) ||
            (selectionEnd > 0 && selectionEnd < current.length() &&
                Character.isHighSurrogate(current.charAt(selectionEnd - 1)) &&
                Character.isLowSurrogate(current.charAt(selectionEnd))))
            throw new IllegalArgumentException("Позиція курсора некоректна. Повтори команду.");

        long newLength = (long) current.length() -
            (selectionEnd - selectionStart) + spoken.length();
        if (newLength > MAX_FIELD_LENGTH)
            throw new IllegalArgumentException("Текст перевищить максимальну довжину поля.");
        String result = current.substring(0, selectionStart) + spoken +
            current.substring(selectionEnd);
        return new Plan(result, selectionStart + spoken.length());
    }
}
