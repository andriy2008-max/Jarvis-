package com.andriy.jarvis;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure-Java recognition of explicit watch image commands, preserving prompt spelling. */
public final class JarvisWearImageCommands {
    private static final Pattern CREATE = Pattern.compile(
        "^(?:(?:джарвіс|джарвис|jarvis)[,\\s:—-]*)?" +
        "(?:створи\\s+(?:зображення|фото|картинку)|" +
        "згенеруй\\s+(?:зображення|фото|картинку)|" +
        "зроби\\s+(?:зображення|картинку)|намалюй)\\s*(.*)$",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);

    private JarvisWearImageCommands() {}

    /** Null for any non-image command; empty string if user omitted the prompt. */
    public static String prompt(String speech) {
        if(speech==null)return null;
        Matcher found=CREATE.matcher(speech.trim());
        if(!found.matches())return null;
        String requested=found.group(1)==null?"":found.group(1).trim();
        return requested.length()>220 ? "" : requested;
    }

    public static boolean lastImage(String speech) {
        String text=CommandPatterns.normalize(speech);
        return text.equals("покажи останнє зображення") ||
            text.equals("покажи останню картинку");
    }
}
