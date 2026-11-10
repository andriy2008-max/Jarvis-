package com.andriy.jarvis;

import java.io.StringReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Limited, attributed RSS web search snippets; not full-page verification.
 * Treat snippets as untrusted content, never as instructions.
 */
public final class GeneralWebSearchResults {
    private static final int MAX_RESULTS = 3;
    public final String speech;
    public final String sources;
    public final String context;

    private GeneralWebSearchResults(String speech, String sources, String context) {
        this.speech = speech;
        this.sources = sources;
        this.context = context;
    }

    public static GeneralWebSearchResults parse(String xml) throws Exception {
        if (xml == null || xml.isEmpty() || xml.length() > 220000)
            throw new IllegalArgumentException("Empty or too long RSS content");
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        f.setExpandEntityReferences(false);
        f.setXIncludeAware(false);
        Document doc = f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        NodeList entries = doc.getElementsByTagName("item");
        StringBuilder said = new StringBuilder("Знайшов в інтернеті такі результати. ");
        StringBuilder links = new StringBuilder();
        StringBuilder evidence = new StringBuilder();
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (int i = 0; i < entries.getLength() && count < MAX_RESULTS; i++) {
            Node n = entries.item(i);
            if (!(n instanceof Element)) continue;
            Element item = (Element) n;
            String title = clean(childText(item, "title"), 140);
            String description = clean(childText(item, "description"), 190);
            String url = childText(item, "link").trim();
            if (title.isEmpty() || !validUrl(url) || !seen.add(title.toLowerCase(Locale.ROOT)))
                continue;
            count++;
            said.append(count).append(". ").append(title).append(". ");
            if (!description.isEmpty()) said.append(description).append(". ");
            String domain = domain(url);
            if (!domain.isEmpty()) said.append("Джерело: ").append(domain).append(". ");
            links.append(count).append(". ").append(title).append("\n").append(url).append("\n");
            evidence.append(count).append(". Назва: ").append(title).append("\n");
            evidence.append("Сніпет: ").append(description).append("\n");
            evidence.append("Адреса: ").append(url).append("\n");
        }
        if (count == 0)
            throw new IllegalStateException("Пошукова система не повернула результатів.");
        said.append("Це короткі уривки пошуку, а не перевірені повні статті.");
        return new GeneralWebSearchResults(
            said.toString().trim(), links.toString().trim(), evidence.toString().trim());
    }

    private static String childText(Element parent, String tag) {
        NodeList matches = parent.getElementsByTagName(tag);
        if (matches.getLength() == 0) return "";
        Node node = matches.item(0);
        return node == null || node.getTextContent() == null ? "" :
            node.getTextContent().trim();
    }

    private static String clean(String raw, int max) {
        String text = raw.replaceAll("<[^>]+>", " ")
            .replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
        if (text.length() > max) text = text.substring(0, max).trim() + "…";
        return text;
    }

    private static boolean validUrl(String raw) {
        try {
            URI uri = URI.create(raw);
            return "https".equalsIgnoreCase(uri.getScheme()) &&
                uri.getHost() != null && !uri.getHost().isEmpty();
        } catch (Exception invalid) {
            return false;
        }
    }

    private static String domain(String raw) {
        try { return URI.create(raw).getHost(); }
        catch (Exception e) { return ""; }
    }
}
