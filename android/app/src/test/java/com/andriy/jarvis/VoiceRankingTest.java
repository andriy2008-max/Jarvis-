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
        ok(VoiceRanking.score("uk-UA", "uk-UA-Male", true, 300)
            > VoiceRanking.score("uk-UA", "uk-UA-female", false, 400));
        ok(VoiceRanking.preferredVladimir("Vladimir"));
        ok(VoiceRanking.preferredVladimir("uk-UA-Volodymyr"));
        ok(VoiceRanking.preferredVladimir("Володимир"));
        ok(!VoiceRanking.preferredVladimir("Anatol"));
        ok(VoiceRanking.score("uk-UA", "Vladimir", false, 200)
            > VoiceRanking.score("uk-UA", "male", false, 400));
        ok(VoiceRanking.score("en-US", "male", false, 400) == Integer.MIN_VALUE);
        ok(VoiceRanking.score("uk-UA", "neural", false, 300)
            > VoiceRanking.score("uk-UA", "neural", true, 300));
        ok(VoiceRanking.score("uk-UA", "normal", false, 400)
            > VoiceRanking.score("uk-UA", "normal", false, 200));
        System.out.println("VoiceRankingTest: " + checks + " checks passed");
    }
}
