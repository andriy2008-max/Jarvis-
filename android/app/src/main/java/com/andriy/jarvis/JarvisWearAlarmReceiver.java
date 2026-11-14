package com.andriy.jarvis;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.json.JSONObject;

/** Fires a persisted, user-requested one-shot Pixel alarm/reminder. */
public final class JarvisWearAlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        if(intent==null ||
            !JarvisWearAlarmScheduler.fireAction().equals(intent.getAction()))return;
        int id=intent.getIntExtra("alarm_id",-1);
        JSONObject item=JarvisWearAlarmScheduler.consume(context,id);
        if(item==null)return;
        JarvisWearAlarmScheduler.notifyDue(context,item);
    }
}
