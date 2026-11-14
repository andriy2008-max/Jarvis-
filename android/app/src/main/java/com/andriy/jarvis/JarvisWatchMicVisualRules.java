package com.andriy.jarvis;

/** Pure UI policy: an ear/hand means the Wear microphone is really ready.
 * Android's transcript can remain active between recognition segments;
 * that state alone must never show the listening animation on a watch. */
public final class JarvisWatchMicVisualRules {
    private JarvisWatchMicVisualRules() {}

    public static boolean hearing(boolean watch,boolean foreground,
                                  boolean ready,boolean sessionActive,
                                  boolean receiving,boolean manualCapture,
                                  boolean speaking,boolean transcriptActive,
                                  boolean listeningExpression) {
        if(watch)
            return foreground && ready && sessionActive && receiving &&
                manualCapture && !speaking;
        // Keep the previous phone UI behavior unchanged.
        return !speaking && (transcriptActive || listeningExpression);
    }
}
