package com.andriy.jarvis;

public final class JarvisVoicePauseTest {
    private static void check(boolean truth, String message) {
        if (!truth) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        JarvisVoicePause pause = new JarvisVoicePause();
        pause.reset("");
        check(!pause.armed() && !pause.expired(100000L, 2),
            "No countdown can run before first word");
        check(!pause.sawWords("?!", 1000L), "Punctuation is not a word");
        check(pause.sawWords("Привіт", 1000L), "First word arms deadline");
        check(pause.armed(), "Timer armed");
        check(!pause.expired(2999L, 2) && pause.expired(3000L, 2),
            "Default two second silence window");
        check(!pause.sawWords("привіт!", 1950L),
            "Repeated identical partial cannot prolong countdown");
        check(pause.expired(3000L, 2), "Repeated partial did not move timeout");
        check(pause.sawWords("Привіт   світ", 2700L),
            "Next word resets timer");
        check(!pause.expired(4699L, 2) && pause.expired(4700L, 2),
            "Timeout tracks latest word");
        check(!pause.sawWords("", 4000L), "Blank partial cannot extend timer");
        check(JarvisVoicePause.seconds(-2) == 1 &&
            JarvisVoicePause.seconds(22) == 10 &&
            JarvisVoicePause.seconds(2) == 2, "Clamped adjustable delay");

        // Swipe-to-correct retains earlier words as a baseline but must wait for
        // the replacement word instead of submitting old text after 2 seconds.
        pause.reset("Привіт");
        check(!pause.armed() && !pause.sawWords("Привіт", 10000L),
            "Corrected baseline must not begin a timer");
        check(pause.sawWords("Привіт ще", 11000L), "Replacement word arms");
        check(pause.expired(14000L, 2), "Replacement word deadline");
        // Regression: in continuous PHONE mode, RMS above threshold can
        // continue forever from fan noise, music, or an open car window.
        // It must NOT extend two-second command dispatch after the last word.
        pause.reset("");
        pause.sawVoice(9000L);
        check(!pause.armed(), "Noise without recognized words cannot submit a command");
        pause.sawWords("відкрий", 10000L);
        for (long ms = 10100L; ms <= 14000L; ms += 100L)
            pause.sawVoice(ms);
        check(!pause.expired(11999L, 2) && pause.expired(12000L, 2),
            "High mic RMS must never keep phone mode listening indefinitely");
        check(!pause.sawWords("ВІДКРИЙ!", 12050L),
            "Identical partial must not move the timeout");
        check(pause.expired(12050L, 2),
            "Timer remains expired despite noisy mic and repeated partial");
        check(pause.sawWords("відкрий ютуб", 12500L),
            "New recognized word renews countdown");
        check(!pause.expired(14499L, 2) && pause.expired(14500L, 2),
            "New word has a fresh two-second interval");
        pause.reset("");
        check(!pause.expired(90000L,2), "Next command may wait indefinitely for first word");
        pause.sawWords("назад",90000L);
        check(pause.expired(92000L,2), "Next command has its own silence deadline");
        System.out.println("PASS JarvisVoicePauseTest");
    }
}
