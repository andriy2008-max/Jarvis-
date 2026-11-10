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
        is("напиши Привіт, це тест!", ScreenControlCommands.Kind.TYPE_TEXT, "Привіт, це тест!");
        is("Джарвіс, напиши: Я люблю Україну. 🇺🇦", ScreenControlCommands.Kind.TYPE_TEXT, "Я люблю Україну. 🇺🇦");
        is("Jarvis — НАПИШИ Hello, World!", ScreenControlCommands.Kind.TYPE_TEXT, "Hello, World!");
        is("напиши", ScreenControlCommands.Kind.TYPE_TEXT, "");
        is("напиши: ", ScreenControlCommands.Kind.TYPE_TEXT, "");
        is("напиши замовкни", ScreenControlCommands.Kind.TYPE_TEXT, "замовкни");
        eq(ScreenControlCommands.parse("написане слово"), null);
        eq(ScreenControlCommands.parse("скажи Привіт"), null);
        is("натисни кнопку пошук", ScreenControlCommands.Kind.CLICK, "пошук");
        is("натисни на значок лупи", ScreenControlCommands.Kind.CLICK, "лупи");
        is("відкрий пошук", ScreenControlCommands.Kind.CLICK, "пошук");
        is("знайди чати", ScreenControlCommands.Kind.CLICK, "чати");
        is("зайди в переписки", ScreenControlCommands.Kind.CLICK, "переписки");
        is("покажи мої переписки", ScreenControlCommands.Kind.CLICK, "мої переписки");
        is("покажи профіль", ScreenControlCommands.Kind.CLICK, "профіль");
        is("перейди до налаштувань", ScreenControlCommands.Kind.CLICK, "налаштувань");
        is("де тут повідомлення", ScreenControlCommands.Kind.CLICK, "повідомлення");
        is("мені потрібна галерея", ScreenControlCommands.Kind.CLICK, "галерея");
        is("натисни іконку три крапки", ScreenControlCommands.Kind.CLICK, "три крапки");
        eq(ScreenControlCommands.parse("відкрий телеграм"), null);
        eq(ScreenControlCommands.parse("покажи нотатки"), null);
        eq(ScreenControlCommands.parse("перейди в рандомне місце"), null);
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
