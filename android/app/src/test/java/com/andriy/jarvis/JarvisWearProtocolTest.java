package com.andriy.jarvis;

/** Transport safety tests: never accept unbounded or malformed control payloads. */
public final class JarvisWearProtocolTest {
    private static void yes(boolean result,String message) {
        if (!result) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        if (!JarvisWearProtocol.validAction(JarvisWearProtocol.SYNC))
            throw new AssertionError("Phone mode sync/heartbeat must be supported");
        yes(JarvisWearProtocol.validAction("enter"),"enter");
        yes(JarvisWearProtocol.validAction("exit"),"exit");
        yes(JarvisWearProtocol.validAction("command"),"command");
        yes(JarvisWearProtocol.validAction(JarvisWearProtocol.TAXI),
            "watch taxi must be a local phone action, not conversational AI");
        yes(JarvisWearProtocol.validAction(JarvisWearProtocol.ASSISTANT),
            "watch freeform voice must be delegated to phone AI/API");
        yes(JarvisWearProtocol.ASSISTANT_ACK_TIMEOUT_MS > 90000,
            "watch waits for slow phone AI and app callbacks, not send-only ack");
        yes(JarvisWearProtocol.validCommand("Джарвіс, запиши витрату 350 гривень на харчування"),
            "phone handles finances without storing keys on watch");
        yes(JarvisWearProtocol.validCommand("Джарвіс, що ти знаєш про Україну?"),
            "phone handles natural-language AI question");
        yes(JarvisWearProtocol.TAXI_ACK_TIMEOUT_MS > 45000,
            "watch waits for Bolt UI outcome rather than only message delivery");
        yes(JarvisWearProtocol.TAXI_ACK_TIMEOUT_MS < JarvisWearProtocol.SPOTIFY_ACK_TIMEOUT_MS,
            "taxi round trip is bounded");
        yes(JarvisWearProtocol.validAction("spotify"),"temporary spotify control");
        yes(JarvisWearProtocol.validAction("cancel_spotify"),"watch can cancel Spotify");
        yes(JarvisWearProtocol.SPOTIFY_ACK_TIMEOUT_MS > 6*20000,
            "Spotify command may include six sequential 20-second UI stages");
        yes(JarvisWearProtocol.MAX_COMMAND_LENGTH < JarvisWearProtocol.MAX_BYTES,
            "Bound commands transmitted over Wear Data Layer");
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
