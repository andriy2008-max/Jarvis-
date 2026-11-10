package com.andriy.jarvis;

public final class JarvisSpeechErrorPolicyTest {
    private static void assertTrue(boolean test,String label) {
        if (!test) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        assertTrue(JarvisSpeechErrorPolicy.SERVER_DISCONNECTED == 11,"Android error 11");
        assertTrue(JarvisSpeechErrorPolicy.rebuildRecognizer(11),
            "Disconnected provider must be recreated");
        assertTrue(JarvisSpeechErrorPolicy.rebuildRecognizer(5),
            "Client failure triggers rebind");
        assertTrue(JarvisSpeechErrorPolicy.lostConnection(8),
            "Recognizer busy needs recovery delay");
        assertTrue(!JarvisSpeechErrorPolicy.rebuildRecognizer(7),
            "No match must not recycle healthy service");
        assertTrue(JarvisSpeechErrorPolicy.isSilent(6),
            "Silence must not count as transport failure");
        assertTrue(JarvisSpeechErrorPolicy.isSilent(7),"No match");
        assertTrue(JarvisSpeechErrorPolicy.needsConfiguration(9),"Permissions");
        assertTrue(JarvisSpeechErrorPolicy.needsConfiguration(12),"Language support");
        assertTrue(JarvisSpeechErrorPolicy.needsConfiguration(13),"Language pack");
        assertTrue(JarvisSpeechErrorPolicy.backoffMs(11,2) >
            JarvisSpeechErrorPolicy.backoffMs(11,1),
            "Disconnected service must backoff");
        assertTrue(JarvisSpeechErrorPolicy.backoffMs(11,8) <= 6500L,
            "Backoff is bounded");
        assertTrue(JarvisSpeechErrorPolicy.backoffMs(10,2) >= 5000L,
            "Rate limiting needs longer cooldown");
        assertTrue(JarvisSpeechErrorPolicy.explanation(11).contains("11"),
            "Error 11 must be clear to user");
        System.out.println("PASS JarvisSpeechErrorPolicyTest");
    }
}
