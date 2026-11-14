package com.andriy.jarvis;

public final class JarvisPhoneModeTest {
    private static void eq(Object expected,Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual))
            throw new AssertionError("Expected " + expected + " but got " + actual);
    }
    public static void main(String[] args) {
        eq(JarvisPhoneMode.Transition.ENTER,JarvisPhoneMode.transition("Телефон"));
        eq(JarvisPhoneMode.Transition.ENTER,JarvisPhoneMode.transition("Джарвіс, телефон"));
        eq(JarvisPhoneMode.Transition.ENTER,JarvisPhoneMode.transition("режим телефона"));
        eq(JarvisPhoneMode.Transition.EXIT,JarvisPhoneMode.transition("Годинник"));
        eq(JarvisPhoneMode.Transition.EXIT,JarvisPhoneMode.transition("Джарвіс"));
        eq(JarvisPhoneMode.Transition.EXIT,JarvisPhoneMode.transition("Jarvis"));
        eq(JarvisPhoneMode.Transition.EXIT,JarvisPhoneMode.transition("джарвис"));
        eq(JarvisPhoneMode.Transition.EXIT,JarvisPhoneMode.transition("джарвіс!"));
        eq(JarvisPhoneMode.Transition.EXIT,JarvisPhoneMode.transition("вийди з режиму телефон"));
        eq(JarvisPhoneMode.Transition.NONE,JarvisPhoneMode.transition("джарвіс натисни пошук"));
        eq(JarvisPhoneMode.Transition.NONE,JarvisPhoneMode.transition("телефонний дзвінок"));
        eq(JarvisPhoneMode.Transition.NONE,JarvisPhoneMode.transition(null));
        eq(JarvisPhoneMode.Transition.ENTER,JarvisPhoneMode.transition("увімкни керування екраном"));
        eq(JarvisPhoneMode.Transition.EXIT,JarvisPhoneMode.transition("вимкни керування екраном"));
        eq(JarvisPhoneMode.Transition.ENTER,JarvisPhoneMode.transition("включи керування екраном"));
        eq(JarvisPhoneMode.Transition.EXIT,JarvisPhoneMode.transition("виключи керування екраном"));
        eq(true,JarvisPhoneMode.requiresPhone("натисни пошук"));
        eq(true,JarvisPhoneMode.requiresPhone("зажми повідомлення"));
        eq(true,JarvisPhoneMode.requiresPhone("відкрий ютуб"));
        eq(true,JarvisPhoneMode.requiresPhone("відкрий ютуб натисни пошук напиши музика"));
        eq(false,JarvisPhoneMode.requiresPhone("котра година"));
        eq(false,JarvisPhoneMode.requiresPhone("телефонний дзвінок"));
        final int[] notifications = {0};
        Runnable changed = () -> notifications[0]++;
        JarvisPhoneMode.subscribe(changed);
        JarvisPhoneMode.setActive(false);
        JarvisPhoneMode.setActive(true);
        JarvisPhoneMode.setActive(true);
        eq(1, notifications[0]);
        eq(true, JarvisPhoneMode.isActive());
        JarvisPhoneMode.setActive(false);
        eq(2, notifications[0]);
        JarvisPhoneMode.unsubscribe(changed);
        JarvisPhoneMode.setActive(true);
        eq(2, notifications[0]);
        JarvisPhoneMode.setActive(false);
        System.out.println("PASS JarvisPhoneModeTest");
    }
}
