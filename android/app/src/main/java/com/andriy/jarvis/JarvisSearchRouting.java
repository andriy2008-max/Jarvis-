package com.andriy.jarvis;

import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Джарвіс" at the very start selects spoken AI-assisted information search.
 * Without that prefix, information requests open the browser silently.
 * Device and app commands are routed separately by their original handlers.
 */
public final class JarvisSearchRouting {
    private static final Pattern WAKE = Pattern.compile(
        "^(?:джарвіс|джарвис|jarvis)(?=$|[\\s,:—-])",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern SEARCH = Pattern.compile(
        "^(?:знайди|пошукай|загугли|погугли|відшукай|знайти|пошук(?:ай)?)" +
        "(?:\\s+мені)?(?:\\s+(?:в|у)\\s+(?:інтернеті|гуглі|мережі))?\\s+(.+)$",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern QUESTION = Pattern.compile(
        "^(?:(?:хто|що|де|коли|чому|як|який|яка|яке|які|скільки|чи|навіщо|котрий)" +
        "\\s+|(?:розкажи\\s+про|поясни|дізнайся\\s+про|розкажи\\s+мені\\s+про)\\s+).+",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private JarvisSearchRouting() {}

    public static boolean addressedToJarvis(String speech) {
        return speech != null && WAKE.matcher(speech.trim()).find();
    }

    /** The actual web query, or null if this is not an information request. */
    public static String query(String speech) {
        if (speech == null) return null;
        String normalized = CommandPatterns.normalize(speech)
            .toLowerCase(Locale.forLanguageTag("uk-UA"))
            .replaceAll("\\s+", " ").trim();
        Matcher search = SEARCH.matcher(normalized);
        String content = search.matches() ? search.group(1).trim() :
            QUESTION.matcher(normalized).matches() ? normalized : null;
        if (content == null) return null;
        content = content.replaceFirst(
            "(?iu)\\s+(?:і\\s+)?(?:озвуч|зачитай|прочитай)(?:\\s+(?:це|їх|мені|результати))?\\s*$", "")
            .replaceFirst("(?iu)^(?:інформацію|відомості|дані)\\s+(?:про|щодо)\\s+", "")
            .replaceAll("[.,!?]+$", "").trim();
        return content.length() > 0 && content.length() <= 220 ? content : null;
    }

    public static String browserUrl(String query) {
        if (query == null || query.trim().isEmpty()) throw new IllegalArgumentException();
        try {
            return "https://www.google.com/search?q=" +
                URLEncoder.encode(query.trim(), "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
