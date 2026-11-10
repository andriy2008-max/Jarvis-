package com.andriy.jarvis;

import java.util.ArrayList;

/**
 * In-process speech text shared by the main and floating Jarvis faces.
 * Keeps only a short visible tail, never saves or uploads speech data.
 * Recognition engines remain under the control of their existing owners.
 */
public final class JarvisLiveTranscript {
    public interface Listener {
        void onChanged(String words, boolean inProgress, long visibleUntilMs);
    }

    public static final long FINAL_VISIBLE_MS = 3500L;
    private static final ArrayList<Listener> listeners = new ArrayList<>();
    private static Runnable mainCorrection;
    private static Runnable floatingCorrection;
    private static String owner = "";
    private static String words = "";
    private static boolean inProgress;
    private static long visibleUntilMs;

    private JarvisLiveTranscript() {}

    public static synchronized void subscribe(Listener listener) {
        if (listener == null) return;
        if (!listeners.contains(listener)) listeners.add(listener);
        listener.onChanged(words, inProgress, visibleUntilMs);
    }

    public static synchronized void unsubscribe(Listener listener) {
        listeners.remove(listener);
    }

    /** A swipe on either face is dispatched to the recognizer owning the words. */
    public static synchronized void setCorrectionHandler(String source, Runnable handler) {
        if ("main".equals(source)) mainCorrection = handler;
        else if ("floating".equals(source)) floatingCorrection = handler;
    }

    public static synchronized boolean canCorrect() {
        return !words.isEmpty() &&
            (("main".equals(owner) && mainCorrection != null) ||
             ("floating".equals(owner) && floatingCorrection != null));
    }

    public static synchronized boolean requestCorrection() {
        if (!canCorrect()) return false;
        Runnable handler = "main".equals(owner) ? mainCorrection : floatingCorrection;
        handler.run();
        return true;
    }

    public static synchronized void begin(String source) {
        owner = source;
        words = "";
        inProgress = true;
        visibleUntilMs = 0L;
        notifyListeners();
    }

    public static synchronized void update(String source, String recognized,
                                           boolean finished, long uptimeMs) {
        if (!owner.equals(source)) return;
        words = JarvisOrbTranscript.visibleTail(recognized);
        inProgress = !finished;
        visibleUntilMs = finished ? uptimeMs + FINAL_VISIBLE_MS : 0L;
        notifyListeners();
    }

    public static synchronized void clear(String source) {
        if (!owner.equals(source)) return;
        owner = "";
        words = "";
        inProgress = false;
        visibleUntilMs = 0L;
        notifyListeners();
    }

    private static void notifyListeners() {
        for (Listener listener : new ArrayList<>(listeners))
            listener.onChanged(words, inProgress, visibleUntilMs);
    }
}
