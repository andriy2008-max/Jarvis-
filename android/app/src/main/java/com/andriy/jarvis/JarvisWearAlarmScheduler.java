package com.andriy.jarvis;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Exact one-shot phone alarms/reminders created from explicit watch commands.
 * Persisted on the phone and restored after reboot. Never controls Pixel UI.
 */
public final class JarvisWearAlarmScheduler {
    private static final String PREFS="jarvis_wear_alarms_v1";
    private static final String KEY="pending";
    private static final String COUNTER="next_id";
    private static final String ACTION="com.andriy.jarvis.WEAR_ALARM_FIRE";
    private static final String CHANNEL="jarvis_wear_alarm_v1";
    private static final int MAX_PENDING=32;
    private static final int NOTIFICATION_BASE=15000;

    private JarvisWearAlarmScheduler(){}

    static String fireAction(){return ACTION;}

    private static SharedPreferences prefs(Context c){
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }

    private static PendingIntent event(Context c,int id) {
        Intent i=new Intent(c,JarvisWearAlarmReceiver.class);
        i.setAction(ACTION);
        i.putExtra("alarm_id",id);
        return PendingIntent.getBroadcast(c,id,i,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private static PendingIntent showApp(Context c) {
        Intent open=new Intent(c,MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c,15009,open,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private static void schedule(Context c,int id,long when) {
        AlarmManager manager=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(manager==null)throw new IllegalStateException("AlarmManager недоступний");
        manager.setAlarmClock(new AlarmManager.AlarmClockInfo(when,showApp(c)),event(c,id));
    }
    private static boolean notificationsAllowed(Context c) {
        return Build.VERSION.SDK_INT<33 ||
            c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                ==PackageManager.PERMISSION_GRANTED;
    }
    private static String fmt(long time) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT,
            new Locale("uk","UA")).format(new Date(time));
    }

    private static long nextOccurrence(AssistantIntents.ClockTime t) {
        Calendar next=Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY,t.hour);
        next.set(Calendar.MINUTE,t.minute);
        next.set(Calendar.SECOND,0);
        next.set(Calendar.MILLISECOND,0);
        if(next.getTimeInMillis()<=System.currentTimeMillis()+2000L)
            next.add(Calendar.DAY_OF_YEAR,1);
        return next.getTimeInMillis();
    }

    public static String setAlarm(Context c,AssistantIntents.ClockTime when) {
        if(when==null)return "Скажи час будильника, наприклад 07:30.";
        return put(c,nextOccurrence(when),"Будильник","Будильник Джарвіса");
    }

    public static String setReminder(Context c,AssistantIntents.Reminder reminder) {
        if(reminder==null)return "Скажи час і текст нагадування.";
        long when=reminder.time!=null ? nextOccurrence(reminder.time) :
            System.currentTimeMillis()+reminder.seconds*1000L;
        return put(c,when,"Нагадування",reminder.message);
    }

    private static synchronized String put(Context context,long when,
                                           String type,String message) {
        Context c=context.getApplicationContext();
        if(!TimerScheduler.canScheduleExact(c))
            return "Не встановлено будильник з Watch7: на Pixel відкрий Налаштування → "+
                "Програми → Спеціальний доступ → Будильники й нагадування → "+
                "Джарвіс → Дозволити. Звичайна команда на Pixel використовує "+
                "Google Годинник і не перевіряє цей дозвіл.";
        if(!notificationsAllowed(c))
            return "Не встановлено будильник з Watch7: на Pixel відкрий "+
                "Налаштування → Сповіщення → Джарвіс і дозволь сповіщення.";
        if(when<=System.currentTimeMillis())
            return "Вказаний час уже минув.";
        SharedPreferences p=prefs(c);
        try {
            JSONArray source=new JSONArray(p.getString(KEY,"[]"));
            JSONArray rows=new JSONArray();
            long now=System.currentTimeMillis();
            for(int i=0;i<source.length();i++){
                JSONObject row=source.optJSONObject(i);
                if(row!=null && row.optLong("when")>now)rows.put(row);
            }
            if(type.equals("Будильник")) {
                for(int i=0;i<rows.length();i++) {
                    JSONObject old=rows.optJSONObject(i);
                    if(old!=null && type.equals(old.optString("type")) &&
                       Math.abs(old.optLong("when")-when)<1000L) {
                        return "Будильник уже встановлено на Pixel: "+fmt(when)+
                            ". Це будильник Джарвіса, а не запис у Google Годиннику.";
                    }
                }
            }
            if(rows.length()>=MAX_PENDING)return "Досягнуто ліміту 32 активних будильників і нагадувань.";
            int id=p.getInt(COUNTER,15030)+1;
            if(id>2000000000 || id<15030) id=15031;
            JSONObject item=new JSONObject();
            item.put("id",id);
            item.put("when",when);
            item.put("type",type);
            item.put("message",message);
            rows.put(item);
            if(!p.edit().putString(KEY,rows.toString()).putInt(COUNTER,id).commit())
                return "Не вдалося зберегти будильник на Pixel.";
            try { schedule(c,id,when); }
            catch(RuntimeException error) {
                remove(c,id);
                return "Android відхилив планування. Перевір спеціальний доступ до будильників на Pixel.";
            }
            return type+" встановлено на Pixel: "+fmt(when)+
                (type.equals("Нагадування")?". "+message:"")+
                (type.equals("Будильник")
                    ? ". Це будильник Джарвіса, він не відображається у Google Годиннику."
                    : "")+
                " Сповіщення на Watch7 залежить від налаштувань Galaxy Wearable.";
        }catch(Exception failure){
            return "Не вдалося створити будильник/нагадування на Pixel. "+
                "Перевір дозволи Джарвіса й повтори команду.";
        }
    }

    static synchronized JSONObject consume(Context c,int id){
        if(id<=0)return null;
        try {
            SharedPreferences p=prefs(c);
            JSONArray rows=new JSONArray(p.getString(KEY,"[]"));
            JSONArray kept=new JSONArray();
            JSONObject found=null;
            for(int i=0;i<rows.length();i++){
                JSONObject item=rows.optJSONObject(i);
                if(item==null)continue;
                if(item.optInt("id")==id)found=item;
                else kept.put(item);
            }
            if(found==null || !p.edit().putString(KEY,kept.toString()).commit())
                return null;
            return found;
        }catch(Exception e){return null;}
    }

    private static synchronized void remove(Context c,int id) {
        consume(c,id);
        AlarmManager manager=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(manager!=null)manager.cancel(event(c,id));
    }

    /** Re-arm future events after reboot. Expired events are dropped safely. */
    public static synchronized void restore(Context context) {
        Context c=context.getApplicationContext();
        if(!TimerScheduler.canScheduleExact(c))return;
        try {
            JSONArray rows=new JSONArray(prefs(c).getString(KEY,"[]"));
            JSONArray future=new JSONArray();
            long now=System.currentTimeMillis();
            for(int i=0;i<rows.length();i++){
                JSONObject item=rows.optJSONObject(i);
                if(item==null || item.optLong("when")<=now)continue;
                try {
                    schedule(c,item.getInt("id"),item.getLong("when"));
                    future.put(item);
                }catch(Exception ignored){future.put(item);}
            }
            prefs(c).edit().putString(KEY,future.toString()).commit();
        }catch(Exception ignored){}
    }

    static void notifyDue(Context c,JSONObject item) {
        if(item==null)return;
        NotificationManager manager=(NotificationManager)c.getSystemService(
            Context.NOTIFICATION_SERVICE);
        if(manager==null || !notificationsAllowed(c))return;
        NotificationChannel channel=new NotificationChannel(CHANNEL,
            "Джарвіс — будильники й нагадування",NotificationManager.IMPORTANCE_HIGH);
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0,800,200,800,200,1200});
        AudioAttributes audio=new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),audio);
        manager.createNotificationChannel(channel);
        String type=item.optString("type","Нагадування");
        String message=item.optString("message",type);
        Notification n=new Notification.Builder(c,CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(type+" — Джарвіс")
            .setContentText(message)
            .setStyle(new Notification.BigTextStyle().bigText(message))
            .setCategory(type.equals("Будильник")
                ? Notification.CATEGORY_ALARM : Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(showApp(c))
            .build();
        try{manager.notify(NOTIFICATION_BASE+item.optInt("id"),n);}
        catch(SecurityException ignored){}
    }
}
