package com.andriy.jarvis;

public final class JarvisSearchRoutingTest {
    private static void yes(String command, String query, boolean addressed) {
        if (JarvisSearchRouting.addressedToJarvis(command) != addressed)
            throw new AssertionError("Wrong wake routing: " + command);
        String actual = JarvisSearchRouting.query(command);
        if (!query.equals(actual))
            throw new AssertionError("Wrong query for [" + command + "]: [" + actual + "]");
    }

    private static void unrelated(String command) {
        if (JarvisSearchRouting.query(command) != null)
            throw new AssertionError("Not information search: " + command);
    }

    public static void main(String[] args) {
        yes("Джарвіс, знайди останні новини України", "останні новини україни", true);
        yes("Знайди останні новини України", "останні новини україни", false);
        yes("ДЖАРВІС: знайди інформацію про Samsung Watch7 і озвуч",
            "samsung watch7", true);
        yes("знайди інформацію про Samsung Watch7 і озвуч",
            "samsung watch7", false);
        yes("Jarvis, хто президент США?", "хто президент сша", true);
        yes("Хто президент США?", "хто президент сша", false);
        yes("Джарвіс, що таке електромагнітна індукція?",
            "що таке електромагнітна індукція", true);
        yes("Де найближча аптека", "де найближча аптека", false);
        yes("пошукай у гуглі прогноз погоди", "прогноз погоди", false);
        yes("Джарвіс знайди новини світу і прочитай їх",
            "новини світу", true);
        if (!JarvisSearchRouting.browserUrl("котики & собаки").contains("q=") ||
            !JarvisSearchRouting.browserUrl("котики & собаки").contains("%26"))
            throw new AssertionError("Search query was not URL encoded");
        unrelated(null);
        unrelated("");
        unrelated("увімкни режим польоту");
        unrelated("джарвіс вимкни світло");
        unrelated("відкрий ютуб");
        unrelated("таймер на 5 хвилин");
        unrelated("постав гучність на 70");
        unrelated("джарвіс скажи привіт");
        unrelated("привіт");
        System.out.println("PASS JarvisSearchRoutingTest");
    }
}
