package com.andriy.jarvis;

import java.util.Locale;

/** Strict alarm time payload, independent of spoken wording and AI routing. */
public final class JarvisWatchAlarmRequest {
    private JarvisWatchAlarmRequest(){}

    public static String encode(AssistantIntents.ClockTime time) {
        if(time==null || time.hour<0 || time.hour>23 ||
           time.minute<0 || time.minute>59)return null;
        return String.format(Locale.ROOT,"%02d:%02d",time.hour,time.minute);
    }
    public static AssistantIntents.ClockTime decode(String text) {
        if(text==null || !text.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]"))
            return null;
        return new AssistantIntents.ClockTime(
            Integer.parseInt(text.substring(0,2)),
            Integer.parseInt(text.substring(3,5)));
    }
}
