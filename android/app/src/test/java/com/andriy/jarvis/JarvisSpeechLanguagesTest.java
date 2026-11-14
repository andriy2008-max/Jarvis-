package com.andriy.jarvis;

/** Does not require Android runtime or a speech language pack. */
public final class JarvisSpeechLanguagesTest {
    private static void check(boolean ok,String detail) {
        if(!ok)throw new AssertionError(detail);
    }
    public static void main(String[] args) {
        check(JarvisSpeechLanguages.automatic(null),"default should be bilingual AUTO");
        check(JarvisSpeechLanguages.automatic("auto"),"auto mode");
        check(!JarvisSpeechLanguages.automatic("ru"),"explicit RU is not auto");
        check(!JarvisSpeechLanguages.automatic("uk"),"explicit UA is not auto");
        check("uk-UA".equals(JarvisSpeechLanguages.initialLanguage("auto")),
            "Auto starts with Ukrainian and may switch to Russian");
        check("uk-UA".equals(JarvisSpeechLanguages.initialLanguage("uk")),
            "Ukrainian recognition");
        check("ru-RU".equals(JarvisSpeechLanguages.initialLanguage("ru")),
            "Russian recognition");
        check(JarvisSpeechLanguages.AUTO.equals(
            JarvisSpeechLanguages.mode("invalid")),"invalid setting falls back");
        check("Авто UA/RU".equals(JarvisSpeechLanguages.label(null)),"menu label");
        System.out.println("PASS JarvisSpeechLanguagesTest");
    }
}
