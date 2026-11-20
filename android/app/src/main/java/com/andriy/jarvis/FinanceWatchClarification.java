package com.andriy.jarvis;

import android.content.Context;

/** Watch-side short follow-up window for a question explicitly sent by Pixel. */
public final class FinanceWatchClarification {
    private static final long TTL=120000L;
    private FinanceWatchClarification(){}
    public static boolean pending(Context c){
        long deadline=c.getSharedPreferences("finance_watch_followup",Context.MODE_PRIVATE)
            .getLong("deadline",0L);
        return deadline>System.currentTimeMillis();
    }
    public static void observe(Context c,String phoneReply){
        if(phoneReply==null)return;
        boolean asks=phoneReply.startsWith("Яка точна сума")||
            phoneReply.startsWith("На що записати")||
            phoneReply.startsWith("Назви точну суму")||
            phoneReply.startsWith("Категорію не впізнано")||
            phoneReply.startsWith("Категорію поки не впізнав");
        c.getSharedPreferences("finance_watch_followup",Context.MODE_PRIVATE).edit()
            .putLong("deadline",asks?System.currentTimeMillis()+TTL:0L).commit();
    }
}
