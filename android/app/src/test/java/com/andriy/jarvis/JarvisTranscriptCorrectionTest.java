package com.andriy.jarvis;

public final class JarvisTranscriptCorrectionTest {
    private static void eq(String got, String want) {
        if (!want.equals(got))
            throw new AssertionError("Expected [" + want + "] but found [" + got + "]");
    }
    public static void main(String[] args) {
        eq(JarvisTranscriptCorrection.removeLastWord(null), "");
        eq(JarvisTranscriptCorrection.removeLastWord("Джарвіс"), "");
        eq(JarvisTranscriptCorrection.removeLastWord("Відкрий Ютуб"), "Відкрий");
        eq(JarvisTranscriptCorrection.removeLastWord(
            "Відкрий   Ютуб   натисни пошук"), "Відкрий Ютуб натисни");
        eq(JarvisTranscriptCorrection.removeLastWord("Привіт 🇺🇦"), "Привіт");
        eq(JarvisTranscriptCorrection.join("Відкрий Ютуб", "натисни пошук"),
            "Відкрий Ютуб натисни пошук");
        eq(JarvisTranscriptCorrection.join("", "повтори"), "повтори");
        if (!JarvisTranscriptCorrection.isLeftSwipe(-70, 6, 30) ||
            JarvisTranscriptCorrection.isLeftSwipe(70, 0, 30) ||
            JarvisTranscriptCorrection.isLeftSwipe(-12, 0, 30) ||
            JarvisTranscriptCorrection.isLeftSwipe(-50, 50, 30))
            throw new AssertionError("Gesture classified incorrectly");
        final int[] calls = {0};
        JarvisLiveTranscript.setCorrectionHandler("main", () -> calls[0]++);
        JarvisLiveTranscript.begin("main");
        JarvisLiveTranscript.update("main", "Відкрий Ютуб", false, 100);
        if (!JarvisLiveTranscript.canCorrect() || !JarvisLiveTranscript.requestCorrection()
            || calls[0] != 1) throw new AssertionError("Undo not delivered to current owner");
        JarvisLiveTranscript.setCorrectionHandler("floating", () -> calls[0] += 10);
        JarvisLiveTranscript.begin("floating");
        JarvisLiveTranscript.update("floating", "Натисни пошук", false, 100);
        if (!JarvisLiveTranscript.requestCorrection() || calls[0] != 11)
            throw new AssertionError("Wrong recognizer received correction");
        JarvisLiveTranscript.clear("floating");
        if (JarvisLiveTranscript.requestCorrection())
            throw new AssertionError("No correction allowed without words");
        JarvisLiveTranscript.setCorrectionHandler("main", null);
        JarvisLiveTranscript.setCorrectionHandler("floating", null);
        System.out.println("PASS Jarvis transcript swipe correction");
    }
}
