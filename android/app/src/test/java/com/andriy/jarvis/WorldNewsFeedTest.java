package com.andriy.jarvis;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class WorldNewsFeedTest {
    private static String date(long t) {
        SimpleDateFormat f = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("GMT"));
        return f.format(new Date(t));
    }
    private static String item(String title, String source, String date) {
        return "<item><title>" + title + "</title><source>" + source +
            "</source><pubDate>" + date + "</pubDate></item>";
    }
    public static void main(String[] args) throws Exception {
        long now = 1800000000000L;
        String recent = date(now - 3600000L);
        String stale = date(now - 9L * 86400000L);
        String xml = "<?xml version='1.0'?><rss><channel>" +
            item("Перша свіжа новина - Агенція А", "Агенція А", recent) +
            item("Перша свіжа новина - Агенція А", "Агенція А", recent) +
            item("Дуже стара новина", "Агенція Б", stale) +
            item("Друга свіжа новина", "Агенція Б", recent) +
            "</channel></rss>";
        String text = WorldNewsFeed.spokenDigest(xml, now);
        if (!text.contains("Перша свіжа новина. Джерело: Агенція А") ||
            !text.contains("Друга свіжа новина. Джерело: Агенція Б") ||
            text.contains("Дуже стара новина") || text.contains("3."))
            throw new AssertionError("News freshness/source/deduplication: " + text);
        try {
            WorldNewsFeed.spokenDigest("<rss><channel>" +
                item("Стара", "Тест", stale) + "</channel></rss>", now);
            throw new AssertionError("Stale items must not be presented as latest");
        } catch (IllegalStateException expected) {}
        boolean blocked = false;
        try {
            WorldNewsFeed.spokenDigest(
                "<!DOCTYPE rss [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]>" +
                "<rss><channel><item><title>&xxe;</title></item></channel></rss>", now);
        } catch (Exception expected) { blocked = true; }
        if (!blocked) throw new AssertionError("External entities must be blocked");
        System.out.println("PASS WorldNewsFeedTest");
    }
}
