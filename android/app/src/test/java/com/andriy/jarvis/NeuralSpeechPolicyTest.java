package com.andriy.jarvis;

public final class NeuralSpeechPolicyTest {
    private static void check(boolean actual, String reason) {
        if (!actual) throw new AssertionError(reason);
    }

    public static void main(String[] args) {
        check(NeuralSpeechPolicy.validEndpoint("https://example.org/tts"), "valid HTTPS endpoint");
        check(NeuralSpeechPolicy.validEndpoint("https://example.org:8443/tts"), "HTTPS port");
        check(!NeuralSpeechPolicy.validEndpoint("http://example.org/tts"), "no cleartext");
        check(!NeuralSpeechPolicy.validEndpoint("https://example.org/chat"), "wrong path");
        check(!NeuralSpeechPolicy.validEndpoint("https://user:pass@example.org/tts"), "no basic auth");
        check(!NeuralSpeechPolicy.validEndpoint("https://example.org/tts?token=secret"), "no query");
        check(!NeuralSpeechPolicy.validEndpoint("https://example.org/tts#fragment"), "no fragments");
        check(!NeuralSpeechPolicy.validEndpoint(null), "null URL");
        check(NeuralSpeechPolicy.validToken("abcdefghijklmnopqrstuvwxyz"), "valid token");
        check(!NeuralSpeechPolicy.validToken("short"), "short token");
        check(!NeuralSpeechPolicy.validToken("abcdefghijklmnopqrst\n"), "newline");
        check(NeuralSpeechPolicy.validText("Привіт!"), "Ukrainian text");
        check(!NeuralSpeechPolicy.validText(" "), "empty");
        check(!NeuralSpeechPolicy.validText("x".repeat(1201)), "overlong");
        System.out.println("NeuralSpeechPolicyTest: OK");
    }
}
