package com.andriy.jarvis;

public final class WorldNewsCommandsTest {
    private static void yes(String s) {
        if (!WorldNewsCommands.requestsWorldNews(s))
            throw new AssertionError("Expected world news: " + s);
    }
    private static void no(String s) {
        if (WorldNewsCommands.requestsWorldNews(s))
            throw new AssertionError("Unexpected world news: " + s);
    }
    public static void main(String[] args) {
        yes("Знайди останні новини світу");
        yes("Джарвіс, озвуч мені останні новини світу!");
        yes("Розкажи новини світу");
        yes("Прочитай свіжі світові новини");
        yes("Останні новини");
        yes("Які новини у світі");
        yes("Що нового в світі");
        yes("Знайди останні новини світу і озвуч їх");
        no(null);
        no("");
        no("Останні новини України");
        no("Відкрий ютуб");
        no("Увімкни звук");
        no("Увімкни новини на телевізорі");
        no("Розкажи про футбол");
        no("Озвуч мені новину про погоду");
        System.out.println("PASS WorldNewsCommandsTest");
    }
}
