package com.andriy.jarvis;

public final class GeneralWebSearchResultsTest {
    public static void main(String[] args) throws Exception {
        String rss = "<?xml version='1.0'?><rss><channel>" +
            "<item><title>Результат один</title><description>Короткий опис</description>" +
            "<link>https://example.org/one</link></item>" +
            "<item><title>Результат один</title><description>Дубль</description>" +
            "<link>https://example.org/copy</link></item>" +
            "<item><title>Результат два</title><description><![CDATA[<b>Факти</b>]]></description>" +
            "<link>https://news.example.org/two</link></item>" +
            "<item><title>Підозрілий URL</title><description>Небезпека</description>" +
            "<link>file:///secret.txt</link></item>" +
            "</channel></rss>";
        GeneralWebSearchResults results = GeneralWebSearchResults.parse(rss);
        if (!results.speech.contains("Результат один") ||
            !results.speech.contains("Результат два") ||
            !results.speech.contains("example.org") ||
            !results.sources.contains("https://example.org/one") ||
            results.sources.contains("copy") ||
            results.sources.contains("file:") ||
            results.speech.contains("<b>"))
            throw new AssertionError("Bad attributed snippets: " + results.speech);
        boolean rejected = false;
        try {
            GeneralWebSearchResults.parse(
                "<!DOCTYPE rss [<!ENTITY test SYSTEM 'file:///etc/passwd'>]>" +
                "<rss><channel><item><title>&test;</title></item></channel></rss>");
        } catch (Exception expected) { rejected = true; }
        if (!rejected) throw new AssertionError("XXE must not be allowed");
        try {
            GeneralWebSearchResults.parse("<rss><channel></channel></rss>");
            throw new AssertionError("Empty feed should fail");
        } catch (IllegalStateException expected) { }
        System.out.println("PASS GeneralWebSearchResultsTest");
    }
}
