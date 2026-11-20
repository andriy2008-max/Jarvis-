package com.andriy.jarvis;

/**
 * In-memory cursor for the last explicitly requested Spotify song search.
 * Phone and Watch keep different instances. No storage, server, or AI calls.
 * Each successful result tap advances the cursor; failed taps never do.
 */
public final class SpotifyResultCursor {
    public static final int MAX_RESULTS=8;
    private static final long TTL_MS=10*60*1000L;
    private String title;
    private int selected=-1;
    private long updatedAt;

    public synchronized void begin(String newTitle,long now) {
        if(newTitle==null || newTitle.trim().isEmpty() || newTitle.length()>100) {
            clear();return;
        }
        title=newTitle.trim();
        selected=-1;
        updatedAt=now;
    }

    public synchronized void selected(int index,long now) {
        if(title==null || index<0 || index>=MAX_RESULTS)return;
        selected=index;
        updatedAt=now;
    }

    public synchronized int next(long now) {
        if(!valid(now) || selected<0 || selected+1>=MAX_RESULTS)return -1;
        return selected+1;
    }

    public synchronized String query(long now) {
        return valid(now) ? title : null;
    }

    public synchronized void clear() {
        title=null;selected=-1;updatedAt=0;
    }

    private boolean valid(long now) {
        return title!=null && now>=updatedAt && now-updatedAt<=TTL_MS;
    }
}
