package com.andriy.jarvis;
import android.app.*;
import android.app.job.*;
import android.content.*;
public final class FinanceReminderJob extends JobService {
    public static void schedule(Context c){JobScheduler scheduler=(JobScheduler)c.getSystemService(JOB_SCHEDULER_SERVICE);if(scheduler!=null)scheduler.schedule(new JobInfo.Builder(7834,new ComponentName(c,FinanceReminderJob.class)).setPeriodic(24L*60*60*1000).setPersisted(true).build());}
    public boolean onStartJob(JobParameters params){
        new Thread(()->{try(FinanceLedger l=new FinanceLedger(this)){
            String day=java.time.LocalDate.now().toString();
            if(!l.setting("reminders","false").equals("true")||l.setting("last_reminder","").equals(day))return;
            String text=FinanceInsights.reminders(l);if(text.isEmpty())return;
            NotificationManager n=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(n==null||!n.areNotificationsEnabled())return;
            n.createNotificationChannel(new NotificationChannel("finance","Фінансові нагадування",NotificationManager.IMPORTANCE_DEFAULT));
            PendingIntent open=PendingIntent.getActivity(this,7834,new Intent(this,FinanceActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            n.notify(7834,new Notification.Builder(this,"finance").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Джарвіс · фінанси").setContentText("Є найближчі фінансові події. Відкрий облік.").setContentIntent(open).setAutoCancel(true).setVisibility(Notification.VISIBILITY_SECRET).build());
            l.setSetting("last_reminder",day);
        }catch(RuntimeException ignored){}finally{jobFinished(params,false);}},"finance-reminders").start();return true;
    }
    public boolean onStopJob(JobParameters params){return true;}
}
