package com.andriy.jarvis;

public final class UkrainianVoiceTextTest {
    private static void eq(String a, String b) {
        if (!a.equals(b)) throw new AssertionError("Expected [" + b + "], got [" + a + "]");
    }

    public static void main(String[] args) {
        eq(UkrainianVoiceText.normalize("Привіт, Джарвіс!"), "прив+іт, дж+арвіс!");
        eq(UkrainianVoiceText.normalize("Ча\u0301с"), "ч+ас");
        eq(UkrainianVoiceText.normalize("Ча+с"), "ч+ас");
        eq(UkrainianVoiceText.normalize("Таймер на 12 хвилин."),
            "таймер н+а дванадцять хвил+ин.");
        eq(UkrainianVoiceText.numberToWords(21), "двадцять один");
        eq(UkrainianVoiceText.numberToWords(105), "сто п'ять");
        eq(UkrainianVoiceText.numberToWords(1000), "одна тисяча");
        eq(UkrainianVoiceText.numberToWords(1234), "одна тисяча двісті тридцять чотири");
        eq(UkrainianVoiceText.normalize(""), "");
        System.out.println("UkrainianVoiceTextTest: OK");
    }
}
