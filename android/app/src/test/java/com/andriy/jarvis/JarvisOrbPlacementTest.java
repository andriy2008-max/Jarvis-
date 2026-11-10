package com.andriy.jarvis;

public final class JarvisOrbPlacementTest {
    private static int checks;

    private static void eq(int actual, int expected) {
        if (actual != expected) throw new AssertionError(actual + " != " + expected);
        checks++;
    }

    private static void near(float actual, float expected) {
        if (Math.abs(actual - expected) > 0.001f)
            throw new AssertionError(actual + " != " + expected);
        checks++;
    }

    public static void main(String[] args) {
        eq(JarvisOrbPlacement.clamp(-15, 0, 400), 0);
        eq(JarvisOrbPlacement.clamp(420, 0, 400), 400);
        eq(JarvisOrbPlacement.clamp(218, 0, 400), 218);
        eq(JarvisOrbPlacement.fromFraction(-2f, 200), 0);
        eq(JarvisOrbPlacement.fromFraction(2f, 200), 200);
        eq(JarvisOrbPlacement.fromFraction(0.5f, 200), 100);
        eq(JarvisOrbPlacement.fromFraction(Float.NaN, 200), 100);
        eq(JarvisOrbPlacement.fromFraction(1f, 0), 0);
        near(JarvisOrbPlacement.fraction(75, 300), .25f);
        near(JarvisOrbPlacement.fraction(200, 0), 0f);
        eq(JarvisOrbPlacement.supportedSize(96), 96);
        eq(JarvisOrbPlacement.supportedSize(112), 112);
        eq(JarvisOrbPlacement.supportedSize(144), 144);
        eq(JarvisOrbPlacement.supportedSize(600), 112);
        System.out.println("PASS " + checks + " Jarvis floating orb placement checks");
    }
}
