package com.andriy.jarvis;

import java.util.ArrayList;

public final class JarvisLiveTranscriptTest {
    public static void main(String[] args) {
        ArrayList<String> events = new ArrayList<>();
        JarvisLiveTranscript.Listener listener = (words, active, until) ->
            events.add(words + "|" + active + "|" + until);
        JarvisLiveTranscript.subscribe(listener);
        JarvisLiveTranscript.begin("main");
        JarvisLiveTranscript.update("main", "Відкрий Ютуб", false, 100L);
        if (!events.get(events.size() - 1).equals("Відкрий Ютуб|true|0"))
            throw new AssertionError("Main partial not visible");
        JarvisLiveTranscript.update("main", "Відкрий Ютуб пошук", true, 1000L);
        if (!events.get(events.size() - 1).equals("Відкрий Ютуб пошук|false|4500"))
            throw new AssertionError("Final timeout mismatch");
        JarvisLiveTranscript.begin("floating");
        JarvisLiveTranscript.update("main", "Стара сесія", true, 1500L);
        if (events.get(events.size() - 1).contains("Стара сесія"))
            throw new AssertionError("Previous owner overwrote active session");
        JarvisLiveTranscript.update("floating", "Постав гучність на 70", false, 1600L);
        if (!events.get(events.size() - 1).equals("Постав гучність на 70|true|0"))
            throw new AssertionError("Floating partial not shared");
        JarvisLiveTranscript.clear("main");
        if (!events.get(events.size() - 1).contains("Постав гучність"))
            throw new AssertionError("Old owner cleared new owner");
        JarvisLiveTranscript.clear("floating");
        if (!events.get(events.size() - 1).equals("|false|0"))
            throw new AssertionError("Transcript was not cleared");
        JarvisLiveTranscript.unsubscribe(listener);
        System.out.println("PASS Jarvis shared live transcript");
    }
}
