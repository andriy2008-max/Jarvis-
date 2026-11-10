package com.andriy.jarvis;

public final class JarvisCommandSequenceTest {
    private static int assertions;
    private static void eq(Object actual, Object expected) {
        if (actual == null ? expected != null : !actual.equals(expected))
            throw new AssertionError("Expected " + expected + " but got " + actual);
        assertions++;
    }
    private static JarvisCommandSequence.Plan valid(String phrase, int count) {
        JarvisCommandSequence.Plan result = JarvisCommandSequence.parse(phrase);
        if (result == null || !result.valid())
            throw new AssertionError("Invalid plan: " + phrase +
                " / " + (result == null ? "not a sequence" : result.error));
        eq(result.steps.size(), count);
        return result;
    }
    public static void main(String[] args) {
        JarvisCommandSequence.Plan youtube = valid(
            "Відкрий Ютуб натисни пошук напиши котики і постав гучність на 70", 4);
        eq(youtube.steps.get(0).kind, JarvisCommandSequence.Kind.OPEN_APP);
        eq(youtube.steps.get(0).text, "ютуб");
        eq(youtube.steps.get(1).kind, JarvisCommandSequence.Kind.CLICK);
        eq(youtube.steps.get(1).text, "пошук");
        eq(youtube.steps.get(2).kind, JarvisCommandSequence.Kind.TYPE_TEXT);
        eq(youtube.steps.get(2).text, "котики");
        eq(youtube.steps.get(3).kind, JarvisCommandSequence.Kind.MEDIA_VOLUME);
        eq(youtube.steps.get(3).volume, 70);

        JarvisCommandSequence.Plan natural = valid(
            "Джарвіс, відкрий YouTube, потім натисни лупу, потім напиши " +
            "Привіт, як справи? і постав гучність на 70", 4);
        eq(natural.steps.get(0).text, "youtube");
        eq(natural.steps.get(1).text, "лупу");
        eq(natural.steps.get(2).text, "Привіт, як справи?");
        eq(natural.steps.get(3).volume, 70);

        JarvisCommandSequence.Plan literal = valid(
            "напиши Добрий день і гарного настрою", 1);
        eq(literal.steps.get(0).text, "Добрий день і гарного настрою");

        JarvisCommandSequence.Plan telegram = valid(
            "відкрий Телеграм і натисни пошук а потім напиши Тест!", 3);
        eq(telegram.steps.get(0).text, "телеграм");
        eq(telegram.steps.get(1).kind, JarvisCommandSequence.Kind.CLICK);
        eq(telegram.steps.get(2).text, "Тест!");

        JarvisCommandSequence.Plan justClickAndType =
            valid("Натисни кнопку Пошук потім напиши Відео про спорт", 2);
        eq(justClickAndType.steps.get(0).kind, JarvisCommandSequence.Kind.CLICK);
        eq(justClickAndType.steps.get(1).kind, JarvisCommandSequence.Kind.TYPE_TEXT);

        JarvisCommandSequence.Plan unsafe =
            JarvisCommandSequence.parse("Відкрий YouTube натисни підтвердити");
        eq(unsafe.valid(), false);
        eq(unsafe.steps.size(), 0);
        JarvisCommandSequence.Plan invalidVolume =
            JarvisCommandSequence.parse("Відкрий YouTube і постав гучність на 101");
        eq(invalidVolume.valid(), false);
        eq(invalidVolume.steps.size(), 0);
        eq(JarvisCommandSequence.parse("Скажи Привіт"), null);
        eq(JarvisCommandSequence.parse("Постав таймер на 3 хвилини"), null);
        eq(valid("відкрий Ютуб", 1).steps.get(0).kind,
            JarvisCommandSequence.Kind.OPEN_APP);
        eq(valid("постав гучність на 70", 1).steps.get(0).volume, 70);

        JarvisCommandSequence.Plan many = JarvisCommandSequence.parse(
            "відкрий YouTube натисни пошук напиши тест натисни пошук " +
            "напиши ще натисни пошук напиши багато");
        eq(many.valid(), false);
        eq(many.steps.size(), 0);
        System.out.println("PASS " + assertions + " Jarvis ordered command parser checks");
    }
}
