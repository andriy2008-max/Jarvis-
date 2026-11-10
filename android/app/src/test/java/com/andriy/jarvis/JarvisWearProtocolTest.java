package com.andriy.jarvis;

/** Transport safety tests: never accept unbounded or malformed control payloads. */
public final class JarvisWearProtocolTest {
    private static void yes(boolean result,String message) {
        if (!result) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        yes(JarvisWearProtocol.validAction("enter"),"enter");
        yes(JarvisWearProtocol.validAction("exit"),"exit");
        yes(JarvisWearProtocol.validAction("command"),"command");
        yes(!JarvisWearProtocol.validAction("execute_shell"),"no arbitrary action");
        yes(!JarvisWearProtocol.validAction(null),"null action");
        yes(JarvisWearProtocol.validCommand("Натисни пошук"),"ordinary command");
        yes(JarvisWearProtocol.validCommand("Свайпни вправо"),"swipe");
        yes(!JarvisWearProtocol.validCommand(""),"no empty commands");
        yes(!JarvisWearProtocol.validCommand("x".repeat(251)),"max command length");
        yes(!JarvisWearProtocol.validCommand("натисни\nоплатити"),"no multiline injection");
        yes(!JarvisWearProtocol.validCommand(null),"null command");
        yes(JarvisWearProtocol.validId("123e4567-e89b-12d3-a456-426614174000"),"uuid");
        yes(!JarvisWearProtocol.validId("wrong-id"),"valid request id");
        yes(JarvisWearProtocol.SESSION_IDLE_MS > JarvisWearProtocol.ACK_TIMEOUT_MS,
            "session must outlast a round trip");
        System.out.println("PASS JarvisWearProtocolTest");
    }
}
