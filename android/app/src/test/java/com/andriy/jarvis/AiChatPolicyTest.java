package com.andriy.jarvis;

import java.util.Arrays;
import java.util.List;

public final class AiChatPolicyTest {
    private static int checks;
    private static void yes(boolean test, String label) {
        if (!test) throw new AssertionError(label);
        checks++;
    }
    public static void main(String[] args) {
        yes(AiChatPolicy.isValidEndpoint("https://jarvis.example.workers.dev/chat"), "valid worker URL");
        yes(AiChatPolicy.isValidEndpoint("https://example.org/"), "valid HTTPS base");
        yes(AiChatPolicy.isValidEndpoint("https://example.org:8443/chat"), "valid port");
        yes(!AiChatPolicy.isValidEndpoint("http://example.org/chat"), "HTTP rejected");
        yes(!AiChatPolicy.isValidEndpoint("javascript:alert(1)"), "scheme rejected");
        yes(!AiChatPolicy.isValidEndpoint("https://user:password@example.org/chat"), "userinfo rejected");
        yes(!AiChatPolicy.isValidEndpoint("https://example.org/chat?token=secret"), "query rejected");
        yes(!AiChatPolicy.isValidEndpoint("https://example.org/chat#fragment"), "fragment rejected");
        yes(!AiChatPolicy.isValidEndpoint(""), "blank rejected");
        yes(!AiChatPolicy.isValidEndpoint(null), "null rejected");
        yes(AiChatPolicy.isValidAccessCode("a_very_long_secret_token_123"), "token length");
        yes(!AiChatPolicy.isValidAccessCode("1234"), "short token rejected");
        yes(!AiChatPolicy.isValidAccessCode("a_very_long_secret_token_123\n"), "newline token rejected");
        List<String> last = AiChatPolicy.keepLast(Arrays.asList("a", "b", "c", "d"), 2);
        yes(last.equals(Arrays.asList("c", "d")), "bounded history");
        yes(AiChatPolicy.keepLast(Arrays.asList("a"), 0).isEmpty(), "empty history");
        yes(AiChatPolicy.MAX_MESSAGE_CHARS == 2000, "message cap");
        yes(AiChatPolicy.MAX_CONTEXT_TURNS == 14, "context cap");
        System.out.println("PASS " + checks + " AiChatPolicy assertions");
    }
}
