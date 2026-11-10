package com.andriy.jarvis;

public final class JarvisShakePatternTest {
    private static void check(boolean condition, String text) {
        if (!condition) throw new AssertionError(text);
    }
    public static void main(String[] args) {
        JarvisShakePattern shake = new JarvisShakePattern();
        check(!shake.onAcceleration(0,0,9.8f,0),"gravity alone");
        check(!shake.onAcceleration(0,0,27f,100),"first peak");
        check(!shake.onAcceleration(0,0,32f,120),"same continuous peak");
        check(!shake.onAcceleration(0,0,13f,155),"release");
        check(shake.onAcceleration(0,0,29f,335),"two quick independent impulses");
        check(!shake.onAcceleration(0,0,13f,500),"cooldown release");
        check(!shake.onAcceleration(0,0,28f,650),"cooldown suppress");
        shake.reset();
        check(!shake.onAcceleration(0,0,26f,100),"first");
        check(!shake.onAcceleration(0,0,12f,300),"release");
        check(!shake.onAcceleration(0,0,27f,1200),"too late isn't second peak");
        check(!shake.onAcceleration(0,0,12f,1230),"release 2");
        check(shake.onAcceleration(0,0,27f,1550),"new second peak");
        shake.reset();
        check(!shake.onAcceleration(Float.NaN,0,10,10),"NaN safe");
        check(!shake.onAcceleration(1,1,9.8f,3000),"normal movement");
        System.out.println("PASS JarvisShakePatternTest");
    }
}
