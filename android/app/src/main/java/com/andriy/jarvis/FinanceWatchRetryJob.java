package com.andriy.jarvis;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

/**
 * Best-effort Wear OS Data Layer retransmission of already persisted finance commands.
 * Jobs can be deferred by Doze/battery saver, so the app never claims immediate delivery.
 */
public final class FinanceWatchRetryJob extends JobService {
    private static final int JOB=81743;
    private JarvisWearPhoneLink link;
    private boolean finishing;
    public static void schedule(Context context,long delayMs){
        if(!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH))return;
        if(FinanceWatchQueue.firstId(context).isEmpty())return;
        JobScheduler jobs=(JobScheduler)context.getSystemService(JOB_SCHEDULER_SERVICE);
        if(jobs==null)return;
        long min=Math.max(15_000L,delayMs);
        JobInfo info=new JobInfo.Builder(JOB,new ComponentName(context,FinanceWatchRetryJob.class))
            .setMinimumLatency(min).setOverrideDeadline(Math.max(5*60_000L,min+60_000L))
            .setPersisted(true).build();
        jobs.schedule(info);
    }
    public static void cancel(Context context){
        JobScheduler jobs=(JobScheduler)context.getSystemService(JOB_SCHEDULER_SERVICE);
        if(jobs!=null)jobs.cancel(JOB);
    }
    @Override public boolean onStartJob(JobParameters params){
        String pending=FinanceWatchQueue.first(this);
        if(pending.isEmpty()||FinanceWatchQueue.firstId(this).isEmpty())return false;
        finishing=false;
        link=new JarvisWearPhoneLink(getApplicationContext());
        link.assistant(pending,(ok,detail)->{
            if(finishing)return;
            finishing=true;
            if(!FinanceWatchQueue.firstId(this).isEmpty())
                schedule(this,ok?15_000L:15*60_000L);
            if(link!=null){link.close();link=null;}
            jobFinished(params,false);
        });
        return true;
    }
    @Override public boolean onStopJob(JobParameters params){
        finishing=true;
        if(link!=null){link.close();link=null;}
        return !FinanceWatchQueue.firstId(this).isEmpty();
    }
}
