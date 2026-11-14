package com.andriy.jarvis;

public final class JarvisWatchMicVisualRulesTest {
    private static int checks;
    private static void check(boolean actual,boolean expected,String label){
        if(actual!=expected)throw new AssertionError(label+": expected "+expected+", got "+actual);
        checks++;
    }
    // watch, foreground, ready, session, receiving, manualCapture,
    // speaking, transcriptActive, listeningAnimationRequested
    public static void main(String[] args) {
        check(JarvisWatchMicVisualRules.hearing(true,true,true,true,true,true,
            false,false,true),true,"watch genuinely listening");
        check(JarvisWatchMicVisualRules.hearing(true,true,false,true,true,true,
            false,true,true),false,"watch recognizer starting: NO ear");
        check(JarvisWatchMicVisualRules.hearing(true,true,false,false,false,true,
            false,true,true),false,"watch between fragments: NO ear");
        check(JarvisWatchMicVisualRules.hearing(true,true,false,false,false,false,
            false,true,true),false,"watch stale transcript: NO ear");
        check(JarvisWatchMicVisualRules.hearing(true,true,true,false,true,true,
            false,true,true),false,"session ended: NO ear");
        check(JarvisWatchMicVisualRules.hearing(true,true,true,true,false,true,
            false,true,true),false,"onEndOfSpeech: NO ear");
        check(JarvisWatchMicVisualRules.hearing(true,true,true,true,true,false,
            false,true,true),false,"manual capture canceled: NO ear");
        check(JarvisWatchMicVisualRules.hearing(true,false,true,true,true,true,
            false,true,true),false,"watch app in background: NO ear");
        check(JarvisWatchMicVisualRules.hearing(true,true,true,true,true,true,
            true,true,true),false,"TTS: NO ear");
        check(JarvisWatchMicVisualRules.hearing(false,true,false,false,false,
            false,false,true,false),true,"phone transcript compatibility");
        check(JarvisWatchMicVisualRules.hearing(false,true,false,false,false,
            false,false,false,true),true,"phone listening expression compatibility");
        check(JarvisWatchMicVisualRules.hearing(false,true,true,true,true,true,
            true,true,true),false,"phone speaking compatibility");
        System.out.println("PASS "+checks+" JarvisWatchMicVisualRules checks");
    }
}
