package com.andriy.jarvis;

public final class JarvisShakeGateTest {
    private static void check(boolean value,String text) {
        if (!value) throw new AssertionError(text);
    }
    public static void main(String[] args) {
        check(JarvisShakeGate.containsWakeWord("Джарвіс"),"Ukrainian wake");
        check(JarvisShakeGate.containsWakeWord("гей джарвіс!"),"wake word within utterance");
        check(JarvisShakeGate.containsWakeWord("JARVIS"),"English wake");
        check(!JarvisShakeGate.containsWakeWord("Джарвісовий"),"not part of another word");
        check(!JarvisShakeGate.containsWakeWord("привіт"),"ordinary phrase no wake");
        check(JarvisShakeGate.isWakeOnly("джарвіс!"),"second standalone wake");
        check(JarvisShakeGate.isWakeOnly("ДЖАРВІС"),"case insensitive");
        check(!JarvisShakeGate.isWakeOnly("Джарвіс, що таке ШІ?"),"request is not standalone");
        check(JarvisShakeGate.aiRequested("Джарвіс, що таке гравітація?"),"AI with second prefix");
        check(!JarvisShakeGate.aiRequested("що таке гравітація?"),"no AI without second prefix");
        check(!JarvisShakeGate.aiRequested("увімкни ліхтарик"),"local action");
        System.out.println("PASS JarvisShakeGateTest");
    }
}
