package com.andriy.jarvis;

public final class WakeConversationTest {
    private static int count;
    private static void check(boolean v, String label) {
        count++;
        if (!v) throw new AssertionError(label);
    }
    private static void result(WakeConversation machine, String phrase, long now,
                               boolean manual, WakeConversation.Kind kind, String command) {
        WakeConversation.Decision d = machine.accept(phrase, now, manual);
        check(d.kind == kind, phrase + " expected " + kind + ", got " + d.kind);
        check(d.command.equals(command), phrase + " expected text " + command + ", got " + d.command);
    }
    public static void main(String[] args) {
        WakeConversation w = new WakeConversation();
        result(w, "розкажи щось цікаве", 100L, false, WakeConversation.Kind.IGNORE, "");
        result(w, "джарвіска розкажи", 110L, false, WakeConversation.Kind.IGNORE, "");
        result(w, "Джарвіс", 200L, false, WakeConversation.Kind.ARMED, "");
        check(w.isActive(201L), "wake should activate without speaking");
        result(w, "Розкажи про космос", 300L, false, WakeConversation.Kind.COMMAND, "розкажи про космос");
        result(w, "І ще щось", 500L, false, WakeConversation.Kind.COMMAND, "і ще щось");
        result(w, "Джарвіс, досить", 600L, false, WakeConversation.Kind.FINISHED, "");
        check(!w.isActive(601L), "finish exits");
        result(w, "поради", 700L, false, WakeConversation.Kind.IGNORE, "");
        result(w, "Jarvis, відкрий ARM LOG", 800L, false, WakeConversation.Kind.COMMAND, "відкрий arm log");
        result(w, "Продовжуй", 800L + WakeConversation.CONVERSATION_WINDOW_MS, false,
            WakeConversation.Kind.IGNORE, "");
        result(w, "ДЖАРВИС: привіт!", 90000L, false, WakeConversation.Kind.COMMAND, "привіт");
        w.assistantFinished(93000L);
        result(w, "а чому?", 93000L + WakeConversation.CONVERSATION_WINDOW_MS - 1,
            false, WakeConversation.Kind.COMMAND, "а чому");
        w.end();
        result(w, "Нотатка купити каву", 120000L, true, WakeConversation.Kind.COMMAND, "нотатка купити каву");
        w.end();
        result(w, "джарвіс?", 121000L, false, WakeConversation.Kind.ARMED, "");
        check(!w.isActive(121000L + WakeConversation.WAKE_ONLY_WINDOW_MS), "wake-only expiration");
        result(w, "запиши купити молоко", 135000L, false, WakeConversation.Kind.IGNORE, "");
        result(w, "ей джарвіс — наступна пісня", 140000L, false,
            WakeConversation.Kind.COMMAND, "наступна пісня");
        result(w, "закінчи розмову", 140100L, false, WakeConversation.Kind.FINISHED, "");
        result(w, "Привіт", 142000L, true, WakeConversation.Kind.COMMAND, "привіт");
        result(w, "кінець розмови", 142200L, false, WakeConversation.Kind.FINISHED, "");
        result(w, null, 142300L, false, WakeConversation.Kind.IGNORE, "");
        check(!w.isActive(142300L), "null ignored");
        System.out.println("PASS " + count + " WakeConversation tests");
    }
}
