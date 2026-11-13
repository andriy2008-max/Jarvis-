package com.andriy.jarvis;

public final class JarvisInstantCommandsTest {
    private static int checks;
    private static void yes(boolean valid) {
        checks++;
        if(!valid)throw new AssertionError("Instant command test "+checks);
    }
    public static void main(String[] args) {
        String[] immediate={
            "назад","гучніше","тихіше","досить",
            "наступна пісня","попередня пісня",
            "наступний трек","попередній трек",
            "пауза музики","продовж музику",
            "увімкни музику","увімкни реп","увімкни українське",
            "увімкни ліхтарик","вимкни ліхтарик",
            "покажи нотатки","покажи будильники",
            "скасуй таймер","вимкни звук"
        };
        for(String phrase:immediate) {
            yes(JarvisInstantCommands.isComplete(phrase));
            yes(JarvisInstantCommands.isComplete("Джарвіс, "+phrase+"!"));
        }
        String[] mustWait={
            null,"","джарвіс","стоп","постав","встанови",
            "відкрий","напиши","напиши Яна Привіт",
            "напиши в Telegram Яна Привіт",
            "увімкни пісню","увімкни пісню Океан",
            "увімкни плейлист","увімкни плейлист Рок",
            "постав будильник","постав будильник на 07:30",
            "постав гучність","постав гучність на 70",
            "увімкни ліхтарик і відкрий Telegram",
            "відкрий YouTube натисни пошук",
            "увімкни реп а потім постав гучність на 70",
            "наступна пісня в Spotify",
            "Привіт як справи",
            "скажи Привіт",
            "покажи нотатки і видали останню"
        };
        for(String phrase:mustWait)yes(!JarvisInstantCommands.isComplete(phrase));
        yes(JarvisInstantCommands.stableDelayMs()>=250);
        yes(JarvisInstantCommands.stableDelayMs()<=500);
        System.out.println("PASS "+checks+" instant command checks");
    }
}
