package com.andriy.jarvis;

public final class SpotifyResultCursorTest {
    private static void ok(boolean cond,String msg) {
        if(!cond)throw new AssertionError(msg);
    }
    public static void main(String[] args) {
        SpotifyResultCursor phone=new SpotifyResultCursor();
        SpotifyResultCursor watch=new SpotifyResultCursor();
        ok(phone.next(1000L)==-1,"no next song before a search");
        phone.begin("я вільний",1000L);
        ok(phone.next(1100L)==-1,"no next song until a first result was tapped");
        phone.selected(0,1200L);
        ok(phone.next(1300L)==1,"second result after first click");
        phone.selected(1,1400L);
        ok(phone.next(1500L)==2,"third result after repeated command");
        ok(watch.next(1500L)==-1,"phone list does not leak onto Watch7");
        watch.begin("мама",2000L);
        watch.selected(0,2200L);
        ok(watch.next(2300L)==1,"Watch7 keeps its own result cursor");
        phone.begin("інша назва",2500L);
        ok(phone.next(2600L)==-1,"new search resets old results");
        phone.selected(SpotifyResultCursor.MAX_RESULTS-1,2700L);
        ok(phone.next(2800L)==-1,"stop after last supported result");
        phone.selected(0,3000L);
        ok(phone.next(3000L+600001L)==-1,"expire stale result list");
        ok(phone.query(3000L+600001L)==null,"expired title unavailable");
        phone.clear();
        ok(phone.query(3001L)==null,"clear erases last query");
        System.out.println("PASS SpotifyResultCursorTest");
    }
}
