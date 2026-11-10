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
        eq(JarvisPhoneMode.Transition.EXIT,JarvisPhoneMode.transition("Джарвіс"));
        eq(JarvisPhoneMode.Transition.EXIT,JarvisPhoneMode.transition("джарвіс!"));
        eq(JarvisPhoneMode.Transition.EXIT,JarvisPhoneMode.transition("вийди з режиму телефон"));
        eq(JarvisPhoneMode.Transition.NONE,JarvisPhoneMode.transition("джарвіс натисни пошук"));
        eq(JarvisPhoneMode.Transition.NONE,JarvisPhoneMode.transition("телефонний дзвінок"));
        eq(JarvisPhoneMode.Transition.NONE,JarvisPhoneMode.transition(null));
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
