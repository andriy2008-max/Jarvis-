package com.andriy.jarvis;

import android.app.*;
import android.app.job.*;
import android.content.*;
import android.database.Cursor;
import java.time.*;
import java.util.*;

/** User-opted financial reminders: quiet hours, per-period alerts, 3-per-day cap. */
public final class FinanceReminderJob extends JobService {
    private static final int JOB=7834,MAX_DAILY=3;
    public static void schedule(Context c){
        JobScheduler scheduler=(JobScheduler)c.getSystemService(JOB_SCHEDULER_SERVICE);
        if(scheduler==null)return;
        ZonedDateTime now=ZonedDateTime.now();
        ZonedDateTime next=now.toLocalDate().atTime(8,30).atZone(now.getZone());
        if(!next.isAfter(now))next=next.plusDays(1);
        long delay=Math.max(60_000L,java.time.Duration.between(now,next).toMillis());
        scheduler.schedule(new JobInfo.Builder(JOB,new ComponentName(c,FinanceReminderJob.class))
            .setMinimumLatency(delay).setOverrideDeadline(delay+3_600_000L).setPersisted(true).build());
    }
    public static void cancel(Context c){
        JobScheduler scheduler=(JobScheduler)c.getSystemService(JOB_SCHEDULER_SERVICE);
        if(scheduler!=null)scheduler.cancel(JOB);
    }
    public static boolean quiet(FinanceLedger ledger,LocalTime current){
        try{
            LocalTime start=LocalTime.parse(ledger.setting("quiet_start","22:00"));
            LocalTime end=LocalTime.parse(ledger.setting("quiet_end","08:00"));
            if(start.equals(end))return false;
            if(start.isBefore(end))return !current.isBefore(start)&&current.isBefore(end);
            return !current.isBefore(start)||current.isBefore(end);
        }catch(RuntimeException e){return true;}
    }
    @Override public boolean onStartJob(JobParameters params){
        new Thread(()->{
            try(FinanceLedger ledger=new FinanceLedger(this)){
                if(!ledger.setting("reminders","false").equals("true")||quiet(ledger,LocalTime.now()))return;
                NotificationManager n=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
                if(n==null||!n.areNotificationsEnabled())return;
                n.createNotificationChannel(new NotificationChannel("finance","Фінансові нагадування",NotificationManager.IMPORTANCE_DEFAULT));
                String day=LocalDate.now().toString();
                int count=Integer.parseInt(ledger.setting("reminder_count:"+day,"0"));
                if(count>=MAX_DAILY)return;
                List<String> messages=new ArrayList<>();
                if(!FinanceInsights.reminders(ledger).isEmpty()&&!ledger.setting("finance_due_notified","").equals(day))
                    messages.add("Наближається запланований платіж");
                List<String> budgetEvents=new ArrayList<>();
                try(Cursor c=ledger.getReadableDatabase().rawQuery("SELECT id,name,currency,category,amount,budget_period FROM plans WHERE type='budget'",null)){
                    while(c.moveToNext()){
                        String period=c.getString(5),key="budget_threshold:"+c.getString(0)+":"+FinanceAnalytics.begin(period);
                        long spent=FinanceAnalytics.spent(ledger,c.getString(2),c.getString(3),period);
                        int level=spent>=c.getLong(4)?100:spent*5>=c.getLong(4)*4?80:0;
                        if(level>Integer.parseInt(ledger.setting(key,"0"))){
                            budgetEvents.add(key+"|"+level);
                        }
                    }
                }
                if(!budgetEvents.isEmpty())messages.add("Один або кілька бюджетів досягли порогу витрат");
                if(LocalDate.now().getDayOfWeek()==DayOfWeek.SUNDAY&&
                   ledger.setting("digest_weekly","false").equals("true")&&
                   !day.equals(ledger.setting("weekly_digest_sent","")))messages.add("Готовий тижневий фінансовий підсумок");
                if(LocalDate.now().getDayOfMonth()==1&&
                   ledger.setting("digest_monthly","false").equals("true")&&
                   !day.equals(ledger.setting("monthly_digest_sent","")))messages.add("Готовий місячний фінансовий підсумок");
                PendingIntent open=PendingIntent.getActivity(this,7834,new Intent(this,FinanceActivity.class),
                    PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
                for(String message:messages){
                    if(count>=MAX_DAILY)break;
                    n.notify(7834+count,new Notification.Builder(this,"finance")
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle("Джарвіс · Фінанси").setContentText(message)
                        .setContentIntent(open).setAutoCancel(true)
                        .setVisibility(Notification.VISIBILITY_SECRET).build());
                    count++;
                    ledger.setSetting("reminder_count:"+day,""+count);
                    if(message.contains("платіж"))ledger.setSetting("finance_due_notified",day);
                    if(message.contains("тижневий"))ledger.setSetting("weekly_digest_sent",day);
                    if(message.contains("місячний"))ledger.setSetting("monthly_digest_sent",day);
                    if(message.contains("бюджетів"))for(String event:budgetEvents){
                        int sep=event.lastIndexOf('|');
                        ledger.setSetting(event.substring(0,sep),event.substring(sep+1));
                    }
                }
            }catch(RuntimeException ignored){
                // Do not leak personal finance records through app errors.
            }finally{
                try(FinanceLedger l=new FinanceLedger(this)){
                    if(l.setting("reminders","false").equals("true"))schedule(this);
                }catch(RuntimeException ignored){}
                jobFinished(params,false);
            }
        },"finance-reminders").start();return true;
    }
    @Override public boolean onStopJob(JobParameters params){return true;}
}
