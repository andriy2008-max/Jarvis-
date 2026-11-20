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
    private boolean playlist;
    private String resultTitle;

    public synchronized void begin(String newTitle,long now) {
        if(newTitle==null || newTitle.trim().isEmpty() || newTitle.length()>100) {
            clear();return;
        }
        title=newTitle.trim();
        selected=-1;
        playlist=false;
        resultTitle=null;
        updatedAt=now;
    }

    public synchronized void selected(int index,long now) {
        selected(index,now,false,null);
    }

    /** Used by Next Result to return from a playlist before selecting the
     * next visible mixed search result. */
    public synchronized void selected(int index,long now,boolean isPlaylist,String name) {
        if(title==null || index<0 || index>=MAX_RESULTS)return;
        selected=index;
        playlist=isPlaylist;
        resultTitle=name;
        updatedAt=now;
    }
    public synchronized boolean selectedPlaylist(long now) {
        return valid(now) && selected>=0 && playlist;
    }
    public synchronized String selectedTitle(long now) {
        return valid(now) && selected>=0 ? resultTitle : null;
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
        playlist=false;resultTitle=null;
    }

    private boolean valid(long now) {
        return title!=null && now>=updatedAt && now-updatedAt<=TTL_MS;
    }
}
