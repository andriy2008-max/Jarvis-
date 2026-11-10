package com.andriy.jarvis;

public final class ScreenTargetMatcherTest {
    private static int assertions;
    private static void eq(Object actual, Object expected) {
        if (actual == null ? expected != null : !actual.equals(expected))
            throw new AssertionError("Expected " + expected + " but got " + actual);
        assertions++;
    }
    private static ScreenTargetMatcher.Target target(String label, String description, String id) {
        return new ScreenTargetMatcher.Target(label, description, id);
    }
    private static void unique(String request, int expected, ScreenTargetMatcher.Target... options) {
        ScreenTargetMatcher.Selection selected = ScreenTargetMatcher.select(
            request, ScreenTargetMatcher.examples(options));
        eq(selected.index, expected);
        eq(selected.ambiguous, false);
    }
    public static void main(String[] args) {
        eq(ScreenTargetMatcher.intent("лупа"), "search");
        eq(ScreenTargetMatcher.intent("знайди чати"), "chats");
        eq(ScreenTargetMatcher.intent("іконка шестерні"), "settings");
        eq(ScreenTargetMatcher.intent("три крапки"), "menu");
        eq(ScreenTargetMatcher.intent("відкрити повідомлення"), "messages");

        unique("лупу", 1,
            target("Головна", "", "nav_home"),
            target("", "Search", "com.example:id/action_search"),
            target("Профіль", "", "profile"));
        unique("знайди чати", 1,
            target("Пошук", "", "btn_search"),
            target("", "Chats", "tab_chats"),
            target("Мій профіль", "", "tab_profile"));
        unique("знайди пошук", 0,
            target("", "", "com.example:id/ic_search"),
            target("Головна", "", "main_tab"));
        unique("перейди до профілю", 0,
            target("Мій акаунт", "", "profile_tab"),
            target("Налаштування", "", "settings"));
        unique("відкрий меню", 0,
            target("", "More options", "toolbar_overflow"),
            target("Чати", "", "chats"));
        unique("пошук", 0,
            target("Пошук", "", "search"),
            target("Чати", "", "chat"));
        unique("повідомлення", 0,
            target("Messages", "", "inbox_icon"),
            target("Мій профіль", "", "profile"));
        unique("лупу", -1,
            target("", "", "button_12"),
            target("", "", "button_13"));
        unique("натисни лупу", -1,
            target("Оплатити", "Search", "btn_search"),
            target("Профіль", "", "profile"));
        unique("пошук", -1,
            target("Пошук", "", "nav_search_top"),
            target("Пошук", "", "nav_search_bottom"));
        eq(ScreenTargetMatcher.select("пошук", ScreenTargetMatcher.examples(
            target("Пошук", "", "search_top"),
            target("Пошук", "", "search_bottom"))).ambiguous, true);
        unique("вкладення", -1,
            target("Надіслати", "", "btn_attachment"),
            target("Чати", "", "chat"));
        unique("лупа", 0,
            target("", "", "toolbar/searchIcon"),
            target("Головна", "", "nav_home"));
        unique("чати", 0,
            target("Пошук у чатах", "", "chat_search"),
            target("Камера", "", "btn_camera"));
        System.out.println("PASS " + assertions + " semantic screen matcher assertions");
    }
}
