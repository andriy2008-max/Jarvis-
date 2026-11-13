package com.andriy.jarvis;

public final class AssistantIntentsTest {
    private static int checks;
    private static void eq(Object actual, Object expected) {
        if (actual == null ? expected != null : !actual.equals(expected)) {
            throw new AssertionError("expected " + expected + ", got " + actual);
        }
        checks++;
    }
    private static void assertTime(String input, Integer hour, Integer minute) {
        AssistantIntents.ClockTime time = AssistantIntents.alarmTime(input);
        if (hour == null) eq(time, null);
        else { if (time == null) throw new AssertionError("No time for " + input); eq(time.hour, hour); eq(time.minute, minute); }
    }
    private static void assertReminder(String input, int seconds, Integer hour, Integer minute, String message) {
        AssistantIntents.Reminder rem = AssistantIntents.reminder(input);
        if (rem == null) throw new AssertionError("No reminder for " + input);
        eq(rem.seconds, seconds);
        eq(rem.message, message);
        if (hour == null) eq(rem.time, null);
        else {eq(rem.time.hour, hour); eq(rem.time.minute, minute);}
    }
    public static void main(String[] args) {
        assertTime("постав будильник на 07:30", 7, 30);
        assertTime(CommandPatterns.normalizeControlVerb(
            "Джарвіс, постав будильник на 07:30"),7,30);
        assertTime("постав будильник на 00:00",0,0);
        assertTime("постав будильник на 23:59",23,59);
        assertTime("будильник о 6:05", 6, 5);
        assertTime("заведи будильник на 23.59", 23, 59);
        assertTime("розбуди мене о 00:00", 0, 0);
        assertTime("встанови будильник на 24:00", null, null);
        assertTime("будильник на 12:60", null, null);
        assertTime("будильник о 7", null, null);
        assertReminder("нагадай через 5 хвилин купити молоко", 300, null, null, "купити молоко");
        assertReminder("нагадай мені через 10 секунд про чай", 10, null, null, "чай");
        assertReminder("нагадай через 1 годину випити води", 3600, null, null, "випити води");
        assertReminder("нагадай о 18:30 тренування", 0, 18, 30, "тренування");
        assertReminder("нагадай мені про тренування о 19:15", 0, 19, 15, "тренування");
        eq(AssistantIntents.reminder("нагадай через 25 годин спати"), null);
        eq(AssistantIntents.reminder("нагадай через 0 секунд спати"), null);
        eq(AssistantIntents.reminder("нагадай о 25:30 обід"), null);
        eq(AssistantIntents.reminder("нагадай завтра о 8:00 тренування"), null);
        eq(AssistantIntents.isReminderCommand("нагадай завтра о 8:00 тренування"), true);
        eq(AssistantIntents.isAlarmCommand("постав будильник на 07:30"), true);
        eq(AssistantIntents.requestedApp("відкрий телеграм"), "телеграм");
        eq(AssistantIntents.requestedApp("запусти програму Spotify"), "Spotify");
        eq(AssistantIntents.requestedApp("відкрий застосунок TikTok"), "TikTok");
        eq(AssistantIntents.requestedApp("покажи нотатки"), null);
        eq(AssistantIntents.requestedApp("відкрий Krita Studio"), "Krita Studio");
        eq(AssistantIntents.requestedApp("відкрий програму Мій щоденник"), "Мій щоденник");
        eq(AssistantIntents.appLabel("телеграм"), "telegram");
        eq(AssistantIntents.appLabel("спотіфай"), "spotify");
        eq(AssistantIntents.appLabel("інстаграм"), "instagram");
        eq(AssistantIntents.appLabel("Календар"), "calendar");
        eq(AssistantIntents.appLabel("Невідомий Додаток"), "невідомий додаток");
        System.out.println("PASS " + checks + " AssistantIntents checks");
    }
}
