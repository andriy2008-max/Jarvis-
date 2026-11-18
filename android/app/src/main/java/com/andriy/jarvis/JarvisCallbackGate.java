package com.andriy.jarvis;

/** A terminal callback belongs to one operation; late/duplicate callbacks are inert. */
final class JarvisCallbackGate {
    private long generation;
    private boolean terminal = true;
    synchronized long begin() { terminal = false; return ++generation; }
    synchronized void cancel() { terminal = true; ++generation; }
    synchronized boolean active(long token) { return token == generation && !terminal; }
    synchronized boolean finish(long token) {
        if (!active(token)) return false;
        terminal = true;
        return true;
    }
}
