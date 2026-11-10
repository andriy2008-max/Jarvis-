package com.andriy.jarvis;

public final class JarvisOrbTranscriptTest {
    private static int checks = 0;

    private static void eq(String actual, String expected) {
        if (!expected.equals(actual))
            throw new AssertionError("Expected: " + expected + " but got: " + actual);
        checks++;
    }

    public static void main(String[] args) {
        eq(JarvisOrbTranscript.visibleTail(null), "");
        eq(JarvisOrbTranscript.visibleTail("  "), "");
        eq(JarvisOrbTranscript.visibleTail("Привіт, Джарвіс!"), "Привіт, Джарвіс!");
        eq(JarvisOrbTranscript.visibleTail("  Напиши   Привіт,   як справи? "),
            "Напиши Привіт, як справи?");
        eq(JarvisOrbTranscript.visibleTail("Слава Україні 🇺🇦"), "Слава Україні 🇺🇦");
        String longText = "Це дуже довга фраза, яку Джарвіс поступово розпізнає " +
            "і показує останні слова прямо на своєму маленькому круглому обличчі";
        String shortened = JarvisOrbTranscript.visibleTail(longText);
        if (!shortened.startsWith("…") || !shortened.endsWith("круглому обличчі"))
            throw new AssertionError("Expected readable trailing words: " + shortened);
        checks++;
        // A pair of surrogate halves must never be cut in the middle.
        String emojis = "😀".repeat(80);
        String tail = JarvisOrbTranscript.visibleTail(emojis);
        if (!tail.endsWith("😀") || tail.contains("\uFFFD") ||
            Character.isLowSurrogate(tail.charAt(1)))
            throw new AssertionError("Unicode emoji was split");
        checks++;
        System.out.println("PASS " + checks + " Jarvis floating transcript checks");
    }
}
