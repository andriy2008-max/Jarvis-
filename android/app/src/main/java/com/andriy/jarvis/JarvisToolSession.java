package com.andriy.jarvis;

import java.util.UUID;

/** Один запит/підтвердження для головного екрана й плаваючого вікна. */
public final class JarvisToolSession {
    public static final long REQUEST_MS = 45000, CONFIRM_MS = 30000;
    private String requestId, pending;
    private long started, expires;

    public synchronized String begin(long now) {
        invalidate();
        requestId = UUID.randomUUID().toString();
        started = now;
        return requestId;
    }

    /** Захопити запит можна лише раз, до завершення строку дії. */
    public synchronized boolean claim(String id, long now) {
        if (id == null || !id.equals(requestId)) return false;
        requestId = null;
        return now >= started && now - started <= REQUEST_MS;
    }

    public synchronized void awaitConfirmation(String immutableJson, long now) {
        pending = immutableJson;
        expires = now + CONFIRM_MS;
    }

    public synchronized String confirm(long now) {
        String value = pending;
        pending = null;
        return now <= expires && now >= expires - CONFIRM_MS ? value : null;
    }

    public synchronized void invalidate() { requestId = null; pending = null; }
}
