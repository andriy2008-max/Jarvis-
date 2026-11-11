package com.andriy.jarvis;

import java.util.HashSet;
import java.util.Set;

/** Shared presentation state. A send/dispatch is not a completed operation. */
public final class JarvisControlFeedback {
    public enum Phase { IDLE, RUNNING, SUCCESS, FAILURE }
    public static final long RESULT_MS = 1500L;
    private static final Set<Long> pending = new HashSet<>();
    private static long serial;
    private static long resultUntil;
    private static boolean failed;
    private static Phase result = Phase.IDLE;

    private JarvisControlFeedback() {}

    public static synchronized long begin() {
        if (pending.isEmpty()) failed = false;
        result = Phase.IDLE;
        resultUntil = 0;
        long token = ++serial;
        pending.add(token);
        return token;
    }

    public static synchronized void finish(long token, boolean success, long now) {
        if (!pending.remove(token)) return; // stale or already completed callback
        failed |= !success;
        if (pending.isEmpty()) {
            result = failed ? Phase.FAILURE : Phase.SUCCESS;
            resultUntil = now + RESULT_MS;
        }
    }

    public static synchronized Phase phase(long now) {
        if (!pending.isEmpty()) return Phase.RUNNING;
        return now < resultUntil ? result : Phase.IDLE;
    }

    public static synchronized void clear() {
        pending.clear();
        result = Phase.IDLE;
        resultUntil = 0;
        failed = false;
    }

    /** Positive results of legacy synchronous Android actions only.
     * Unknown/error/permission/help text never earns a green checkmark. */
    public static boolean successfulReply(String text) {
        if (text == null) return false;
        String[] prefixes = { "Перемішування вже ввімкнено.", "Жест виконано.", "Знайшов потрібну кнопку й натиснув.", "Текст введено",
            "Повертаюся назад.", "Виконав довге натискання", "Надіслав довге натискання",
            "Виконую свайп", "Прокрутив", "Прокручую", "Відкриваю",
            "Медіагучність:", "Медіагучність відновлено:", "Медіазвук увімкнено.",
            "Музику вимкнено.", "Ліхтарик увімкнено.", "Ліхтарик вимкнено.",
            "Надіслав команду", "Команду медіапрогравачу надіслано.",
            "Режим «Не турбувати» активовано.", "Режим «Не турбувати» вимкнено.",
            "Звук дзвінка увімкнено.", "Звук дзвінка вимкнено.", "Вібрацію дзвінка увімкнено.",
            "Автоповорот екрана увімкнено.", "Автоповорот екрана вимкнено.",
            "Автоматична яскравість увімкнено.", "Автоматична яскравість вимкнено." };
        for (String prefix : prefixes) if (text.startsWith(prefix)) return true;
        return false;
    }
}
