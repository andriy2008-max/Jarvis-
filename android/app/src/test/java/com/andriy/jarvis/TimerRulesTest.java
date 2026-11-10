package com.andriy.jarvis;

public final class TimerRulesTest {
    private static int count;
    private static void check(boolean success) {
        count++;
        if (!success) throw new AssertionError("TimerRules test #" + count);
    }
    public static void main(String[] args) {
        check(!TimerRules.valid(0));
        check(!TimerRules.valid(-1));
        check(TimerRules.valid(1));
        check(TimerRules.valid(60));
        check(TimerRules.valid(10_800));
        check(!TimerRules.valid(10_801));
        check(TimerRules.deadline(1_000L, 5) == 6_000L);
        check(TimerRules.remainingSeconds(6_000L, 1_000L) == 5);
        check(TimerRules.remainingSeconds(6_000L, 5_999L) == 1);
        check(TimerRules.remainingSeconds(6_000L, 6_000L) == 0);
        check(TimerRules.remainingSeconds(6_000L, 9_000L) == 0);
        check(TimerRules.remainingSeconds(6_000L, 4_500L) == 2);
        check(TimerRules.remainingSeconds(1_000L, -1_000L) == 2);
        boolean threw = false;
        try { TimerRules.deadline(0, 0); } catch (IllegalArgumentException expected) { threw = true; }
        check(threw);
        System.out.println("TimerRulesTest: " + count + " checks passed.");
    }
}
