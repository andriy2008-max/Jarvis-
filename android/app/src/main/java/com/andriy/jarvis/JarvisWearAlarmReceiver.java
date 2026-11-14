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
        if("Будильник".equals(item.optString("type"))) {
            Intent alert=new Intent(context,JarvisWearAlertService.class);
            alert.setAction(JarvisWearAlertService.START);
            alert.putExtra("message",item.optString("message","Будильник Джарвіса"));
            try {
                if(android.os.Build.VERSION.SDK_INT>=26)
                    context.startForegroundService(alert);
                else context.startService(alert);
                return;
            } catch(RuntimeException restricted) {
                // Android may prevent background foreground-service launch.
                // Still deliver the high-priority alarm notification.
            }
        }
        JarvisWearAlarmScheduler.notifyDue(context,item);
    }
}
