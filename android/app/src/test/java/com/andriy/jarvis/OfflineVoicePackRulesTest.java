package com.andriy.jarvis;

public final class OfflineVoicePackRulesTest {
    static void check(boolean valid, String reason) {
        if (!valid) throw new AssertionError(reason);
    }

    public static void main(String[] args) {
        check(OfflineVoicePackRules.validUrl("https://example.com/uk-v6.zip"), "valid HTTPS");
        check(!OfflineVoicePackRules.validUrl("http://example.com/model.zip"), "HTTP rejected");
        check(!OfflineVoicePackRules.validUrl("https://example.com/model.pth"), "raw weights rejected");
        check(!OfflineVoicePackRules.validUrl("https://u:p@example.com/model.zip"), "embedded auth");
        check(!OfflineVoicePackRules.validUrl("https://example.com/model.zip#hash"), "fragments");
        check(OfflineVoicePackRules.validSha256("a".repeat(64)), "correct SHA");
        check(!OfflineVoicePackRules.validSha256("a".repeat(63)), "short SHA");
        check(OfflineVoicePackRules.validEntry("manifest.json"), "manifest");
        check(OfflineVoicePackRules.validEntry("onnx/full/tts_model_encoder.onnx"), "onnx model");
        check(!OfflineVoicePackRules.validEntry("../manifest.json"), "traversal");
        check(!OfflineVoicePackRules.validEntry("onnx/../../private.txt"), "nested traversal");
        check(!OfflineVoicePackRules.validEntry("onnx/other.py"), "no arbitrary code");
        check(!OfflineVoicePackRules.validEntry("onnx\\full\\model.onnx"), "no Windows paths");
        check(!OfflineVoicePackRules.validEntry("onnx//model.onnx"), "no double slash");
        System.out.println("OfflineVoicePackRulesTest: OK");
    }
}
