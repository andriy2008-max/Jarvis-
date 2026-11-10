package com.andriy.jarvis;

public final class VoiceRankingTest {
    private static int checks;
    private static void ok(boolean value) {
        checks++;
        if (!value) throw new AssertionError("VoiceRanking #" + checks);
    }

    public static void main(String[] args) {
        ok(VoiceRanking.ukrainian("uk-UA"));
        ok(VoiceRanking.ukrainian("UK_ua"));
        ok(VoiceRanking.ukrainian("uk"));
        ok(!VoiceRanking.ukrainian("en-US"));
        ok(!VoiceRanking.ukrainian("ru-RU"));
        ok(VoiceRanking.labeledMale("uk-UA-Male-local"));
        ok(VoiceRanking.labeledMale("uk_ua_masculine"));
        ok(!VoiceRanking.labeledMale("uk-UA-Female"));
        ok(!VoiceRanking.labeledMale("uk-UA-x-ukd-local"));
        ok(VoiceRanking.profile(null).equals("dmytro"));
        ok(VoiceRanking.profile("DMyTrO").equals("dmytro"));
        ok(VoiceRanking.profile("LADA").equals("lada"));
        ok(VoiceRanking.profile("unknown").equals("dmytro"));
        ok(VoiceRanking.matchesProfile("uk-UA-Dmytro", VoiceRanking.DMYTRO));
        ok(VoiceRanking.matchesProfile("uk-UA-Дмитро", VoiceRanking.DMYTRO));
        ok(VoiceRanking.matchesProfile("uk-UA-Dmitro", VoiceRanking.DMYTRO));
        ok(VoiceRanking.matchesProfile("uk-UA-Lada", VoiceRanking.LADA));
        ok(VoiceRanking.matchesProfile("uk-UA-Лада", VoiceRanking.LADA));
        ok(!VoiceRanking.matchesProfile("uk-UA-Lada", VoiceRanking.DMYTRO));
        ok(!VoiceRanking.matchesProfile("uk-UA-Dmytro", VoiceRanking.LADA));
        ok(!VoiceRanking.matchesProfile(null, VoiceRanking.LADA));
        ok(VoiceRanking.score("uk-UA", "Dmytro", false, 200)
            > VoiceRanking.score("uk-UA", "male", false, 400));
        ok(VoiceRanking.score("uk-UA", "Lada", false, 200)
            > VoiceRanking.score("uk-UA", "male", false, 400));
        ok(VoiceRanking.score("en-US", "Dmytro", false, 400) == Integer.MIN_VALUE);
        ok(VoiceRanking.score("uk-UA", "neural", false, 300)
            > VoiceRanking.score("uk-UA", "neural", true, 300));
        ok(VoiceRanking.score("uk-UA", "normal", false, 400)
            > VoiceRanking.score("uk-UA", "normal", false, 200));
        System.out.println("VoiceRankingTest: " + checks + " checks passed");
    }
}
