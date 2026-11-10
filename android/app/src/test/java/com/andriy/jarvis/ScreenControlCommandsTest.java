package com.andriy.jarvis;

public final class ScreenControlCommandsTest {
    private static int assertions;
    private static void eq(Object actual, Object expected) {
        if (actual == null ? expected != null : !actual.equals(expected))
            throw new AssertionError("Expected " + expected + ", got " + actual);
        assertions++;
    }
    private static void is(String command, ScreenControlCommands.Kind expected, String label) {
        ScreenControlCommands.Action a = ScreenControlCommands.parse(command);
        if (a == null) throw new AssertionError("No action for " + command);
        eq(a.kind, expected);
        eq(a.label, label);
    }
    public static void main(String[] args) {
        is("увімкни керування екраном", ScreenControlCommands.Kind.ENABLE, null);
        is("вимкни керування екраном", ScreenControlCommands.Kind.DISABLE, null);
        is("зупинись", ScreenControlCommands.Kind.DISABLE, null);
        is("доступ до екрана", ScreenControlCommands.Kind.SETTINGS, null);
        is("стан керування екраном", ScreenControlCommands.Kind.STATUS, null);
        is("прокрути вниз", ScreenControlCommands.Kind.SCROLL_DOWN, null);
        is("прокрути вгору", ScreenControlCommands.Kind.SCROLL_UP, null);
        is("повернись назад", ScreenControlCommands.Kind.BACK, null);
        is("натисни кнопку пошук", ScreenControlCommands.Kind.CLICK, "пошук");
        is("натисни на кнопку налаштування", ScreenControlCommands.Kind.CLICK, "налаштування");
        is("натисни пошук", ScreenControlCommands.Kind.CLICK, "пошук");
        is("натисни кнопку оплатити", ScreenControlCommands.Kind.CLICK, "");
        is("натисни підтвердити", ScreenControlCommands.Kind.CLICK, "");
        is("натисни так", ScreenControlCommands.Kind.CLICK, "");
        is("натисни кнопку пошук.", ScreenControlCommands.Kind.CLICK, "пошук");
        eq(ScreenControlCommands.parse("відкрий телеграм"), null);
        eq(ScreenControlCommands.parse(null), null);
        eq(ScreenControlCommands.isSensitiveLabel("Pay now"), true);
        eq(ScreenControlCommands.isSensitiveLabel("Пошук"), false);
        System.out.println("PASS " + assertions + " screen control parser assertions");
    }
}
