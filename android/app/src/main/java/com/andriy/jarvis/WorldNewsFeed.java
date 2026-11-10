package com.andriy.jarvis;

import java.io.StringReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Reads attributed, recent RSS headlines. Never invents article summaries.
 * XML external entities/DOCTYPE disabled; stale/undated stories are omitted.
 */
public final class WorldNewsFeed {
    private static final long MAX_AGE_MS = 72L * 60L * 60L * 1000L;
    private static final int MAX_ITEMS = 5;
    private WorldNewsFeed() {}

    public static String spokenDigest(String xml, long nowMs) throws Exception {
        if (xml == null || xml.length() > 230000)
            throw new IllegalArgumentException("Стрічка новин завелика або порожня.");
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        f.setExpandEntityReferences(false);
        f.setXIncludeAware(false);
        Document doc = f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        NodeList items = doc.getElementsByTagName("item");
        ArrayList<String> headlines = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < items.getLength() && headlines.size() < MAX_ITEMS; i++) {
            Node node = items.item(i);
            if (!(node instanceof Element)) continue;
            Element item = (Element) node;
            String title = childText(item, "title");
            String source = childText(item, "source");
            long date = publishedAt(childText(item, "pubDate"));
            if (date <= 0 || date > nowMs + 3600000L || nowMs - date > MAX_AGE_MS ||
                title.isEmpty()) continue;
            if (!source.isEmpty() && title.endsWith(" - " + source))
                title = title.substring(0, title.length() - source.length() - 3).trim();
            title = limit(title, 170);
            source = limit(source, 55);
            if (title.isEmpty() || !seen.add(title.toLowerCase(Locale.ROOT))) continue;
            headlines.add((headlines.size() + 1) + ". " + title +
                (source.isEmpty() ? "." : ". Джерело: " + source + "."));
        }
        if (headlines.isEmpty()) throw new IllegalStateException(
            "Не знайшов свіжих заголовків світових новин за останні 72 години. Спробуй пізніше.");
        StringBuilder result = new StringBuilder(
            "Ось останні доступні заголовки світових новин за матеріалами Google Новин. ");
        for (String headline : headlines) result.append(headline).append(' ');
        result.append("Це заголовки публікацій, а не повний зміст статей.");
        return result.toString().trim();
    }

    private static String childText(Element item, String tag) {
        NodeList matches = item.getElementsByTagName(tag);
        if (matches.getLength() == 0) return "";
        Node node = matches.item(0);
        String value = node == null ? null : node.getTextContent();
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static String limit(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max).trim() + "…";
    }

    private static long publishedAt(String date) {
        if (date.isEmpty()) return -1;
        SimpleDateFormat fmt = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        fmt.setLenient(false);
        fmt.setTimeZone(TimeZone.getTimeZone("GMT"));
        try {
            Date parsed = fmt.parse(date);
            return parsed == null ? -1 : parsed.getTime();
        } catch (ParseException ignored) {
            return -1;
        }
    }
}
