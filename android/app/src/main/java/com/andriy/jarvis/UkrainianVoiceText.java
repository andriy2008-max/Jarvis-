package com.andriy.jarvis;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lightweight Ukrainian preparation for the offline VITS char tokenizer. */
public final class UkrainianVoiceText {
    private static final String VOWELS = "аеєиіїоуюя";
    private static final Pattern NUMBERS = Pattern.compile("(?<![\\p{L}\\d])\\d{1,6}(?!\\d)");
    private static final Pattern WORDS = Pattern.compile("[а-щьюяєіїґ+'-]+");
    private static final Pattern COMBINING_STRESS =
        Pattern.compile("([" + VOWELS + "])\\u0301");
    private static final Pattern TRAILING_PLUS =
        Pattern.compile("([" + VOWELS + "])\\+");
    private static final Map<String, String> COMMON_STRESS = new HashMap<>();

    private static final String[] ONES = {
        "нуль", "один", "два", "три", "чотири", "п'ять", "шість", "сім", "вісім", "дев'ять"
    };
    private static final String[] TEENS = {
        "десять", "одинадцять", "дванадцять", "тринадцять", "чотирнадцять",
        "п'ятнадцять", "шістнадцять", "сімнадцять", "вісімнадцять", "дев'ятнадцять"
    };
    private static final String[] TENS = {
        "", "", "двадцять", "тридцять", "сорок", "п'ятдесят", "шістдесят",
        "сімдесят", "вісімдесят", "дев'яносто"
    };
    private static final String[] HUNDREDS = {
        "", "сто", "двісті", "триста", "чотириста", "п'ятсот", "шістсот",
        "сімсот", "вісімсот", "дев'ятсот"
    };
    static {
        COMMON_STRESS.put("привіт", "прив+іт");
        COMMON_STRESS.put("джарвіс", "дж+арвіс");
        COMMON_STRESS.put("дмитро", "дмитр+о");
        COMMON_STRESS.put("лада", "л+ада");
        COMMON_STRESS.put("дякую", "д+якую");
        COMMON_STRESS.put("слухаю", "сл+ухаю");
        COMMON_STRESS.put("сьогодні", "сьог+одні");
        COMMON_STRESS.put("секунда", "сек+унда");
        COMMON_STRESS.put("секунди", "сек+унди");
        COMMON_STRESS.put("секунд", "сек+унд");
        COMMON_STRESS.put("хвилина", "хвил+ина");
        COMMON_STRESS.put("хвилини", "хвил+ини");
        COMMON_STRESS.put("хвилин", "хвил+ин");
    }
    private UkrainianVoiceText() {}

    private static String underThousand(int value) {
        if (value == 0) return ONES[0];
        StringBuilder out = new StringBuilder();
        int hundreds = value / 100;
        int remain = value % 100;
        if (hundreds > 0) out.append(HUNDREDS[hundreds]);
        if (remain >= 10 && remain < 20) {
            join(out, TEENS[remain - 10]);
        } else {
            if (remain >= 20) join(out, TENS[remain / 10]);
            if (remain % 10 > 0) join(out, ONES[remain % 10]);
        }
        return out.toString();
    }

    private static void join(StringBuilder b, String token) {
        if (b.length() > 0) b.append(' ');
        b.append(token);
    }

    public static String numberToWords(int value) {
        if (value < 0 || value > 999999) return Integer.toString(value);
        if (value < 1000) return underThousand(value);
        int thousands = value / 1000;
        int tail = value % 1000;
        String base;
        if (thousands == 1) base = "одна";
        else if (thousands == 2) base = "дві";
        else base = underThousand(thousands);
        int d = thousands % 10, dd = thousands % 100;
        String unit = (dd >= 11 && dd <= 14) ? "тисяч" :
            d == 1 ? "тисяча" : d >= 2 && d <= 4 ? "тисячі" : "тисяч";
        String result = base + " " + unit;
        return tail == 0 ? result : result + " " + underThousand(tail);
    }

    public static String normalize(String raw) {
        if (raw == null) return "";
        String text = raw.toLowerCase(Locale.forLanguageTag("uk-UA")).trim()
            .replace('’', '\'').replace('ʼ', '\'').replace('‘', '\'')
            .replace('`', '\'').replace('“', '"').replace('”', '"')
            .replace('«', '"').replace('»', '"')
            .replace('–', '-').replace('—', '-').replace('―', '-')
            .replace('…', '.');
        text = text.replaceAll("\\s+", " ");

        Matcher m = NUMBERS.matcher(text);
        StringBuffer expanded = new StringBuffer();
        while (m.find()) {
            String digits = m.group();
            String words;
            try {
                words = numberToWords(Integer.parseInt(digits));
            } catch (NumberFormatException invalid) {
                words = digits;
            }
            m.appendReplacement(expanded, Matcher.quoteReplacement(words));
        }
        m.appendTail(expanded);
        text = expanded.toString();

        // Upstream Ukrainian TTS uses '+' immediately BEFORE a stressed vowel.
        text = COMBINING_STRESS.matcher(text).replaceAll("+$1");
        text = TRAILING_PLUS.matcher(text).replaceAll("+$1");

        m = WORDS.matcher(text);
        StringBuffer stressed = new StringBuffer();
        while (m.find()) {
            String word = m.group();
            if (!word.contains("+")) {
                if (COMMON_STRESS.containsKey(word)) {
                    word = COMMON_STRESS.get(word);
                } else {
                    int at = -1, count = 0;
                    for (int i = 0; i < word.length(); i++) {
                        if (VOWELS.indexOf(word.charAt(i)) >= 0) {
                            count++;
                            at = i;
                        }
                    }
                    // Exact deterministic stress on single-vowel words.
                    if (count == 1) word = word.substring(0, at) + "+" + word.substring(at);
                }
            }
            m.appendReplacement(stressed, Matcher.quoteReplacement(word));
        }
        m.appendTail(stressed);
        return stressed.toString().replaceAll(" +", " ").trim();
    }
}
