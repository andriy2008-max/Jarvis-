package com.andriy.jarvis;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/** Ordered consent-gated Bolt passenger UI flow. Never books or pays for a ride. */
public final class BoltTaxiController {
    private static final String BOLT="ee.mtakso.client";
    private static final Handler HANDLER=new Handler(Looper.getMainLooper());
    private static final long POLL_MS=350L;
    // Bolt can start with a splash/home skeleton before the actual rides tile
    // is rendered. Never treat package visibility as button readiness.
    private static final long RIDES_STABLE_MS=900L;
    private static final long RETAP_AFTER_MS=2300L;
    private static final long TIMEOUT_MS=30000L;
    private static final long TOTAL_TIMEOUT_MS=45000L;
    private static BoltTaxiController active;
    public interface Listener {
        void onProgress(String status);
        void onFinished(boolean ok,String status);
    }
    private enum Stage { OPEN_APP, RIDES, VERIFY_RIDES, DELAY, TYPE }
    private final Listener listener;
    private final String address;
    private final JarvisPhoneModeManager.Lease lease;
    private final Runnable tick=this::poll;
    private Stage stage=Stage.OPEN_APP;
    private long started=SystemClock.elapsedRealtime();
    private final long flowStarted=started;
    private long ridesVisibleSince=-1L;
    private boolean finished;
    private int ridesRetries;

    private BoltTaxiController(Listener listener,String address,JarvisPhoneModeManager.Lease lease){
        this.listener=listener;this.address=address;this.lease=lease;
    }
    public static synchronized boolean isRunning(){return active!=null;}
    public static void cancel(){
        BoltTaxiController task;
        synchronized(BoltTaxiController.class){task=active;}
        if(task!=null)task.finish(false,"Підготовку Bolt скасовано. Поїздку не замовляв.");
    }
    public static void start(Context context,String phrase,Listener listener){
        if(listener==null)return;
        String request=JarvisTaxiCommands.destination(phrase);
        if(request==null){listener.onFinished(false,"Невідома команда таксі.");return;}
        if(context.getPackageManager().hasSystemFeature(
            android.content.pm.PackageManager.FEATURE_WATCH)){
            listener.onFinished(false,"Bolt запускається на телефоні.");return;
        }
        synchronized(BoltTaxiController.class){
            if(active!=null){listener.onFinished(false,"Bolt уже виконує команду.");return;}
        }
        Intent launch=context.getPackageManager().getLaunchIntentForPackage(BOLT);
        if(launch==null){
            listener.onFinished(false,"Не знайдено пасажирський застосунок Bolt.");return;
        }
        String address=request.isEmpty()?"":JarvisTaxiPlaces.resolve(context,request);
        if(address.length()>250){
            listener.onFinished(false,"Адреса занадто довга.");return;
        }
        JarvisPhoneModeManager.Lease lease=JarvisPhoneModeManager.acquireForCommand(context);
        try{context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
        catch(RuntimeException error){
            JarvisPhoneModeManager.releaseForCommand(lease);
            listener.onFinished(false,"Android не дозволив відкрити Bolt.");return;
        }
        if(!lease.granted){
            listener.onFinished(false,"Bolt відкритий, але потрібна служба «Джарвіс — керування екраном» у Спеціальних можливостях. "+lease.message);
            return;
        }
        BoltTaxiController job=new BoltTaxiController(listener,address,lease);
        synchronized(BoltTaxiController.class){active=job;}
        listener.onProgress("Відкриваю Bolt. Шукаю «Поїздки»...");
        HANDLER.postDelayed(job.tick,POLL_MS);
    }
    private void move(Stage next,String info){
        stage=next;started=SystemClock.elapsedRealtime();
        listener.onProgress(info);
    }
    private void poll(){
        if(finished)return;
        if(!ScreenControlAccessibilityService.isSessionActive()){
            finish(false,"Керування екраном вимкнулося.");return;
        }
        long age=SystemClock.elapsedRealtime()-started;
        if(SystemClock.elapsedRealtime()-flowStarted>=TOTAL_TIMEOUT_MS){
            finish(false,"Bolt не показав потрібний екран за 45 секунд. Нічого навмання не натискав.");
            return;
        }
        if(!ScreenControlAccessibilityService.boltVisible()){
            ridesVisibleSince=-1L;
            if(age>=TIMEOUT_MS)finish(false,"Bolt не відкрився або екран заблоковано.");
            else HANDLER.postDelayed(tick,POLL_MS);
            return;
        }
        if(age>=TIMEOUT_MS){
            finish(false,"Не знайшов потрібну кнопку або поле Bolt за 30 секунд. Нічого навмання не натискав.");
            return;
        }
        switch(stage){
            case OPEN_APP:
                if(ScreenControlAccessibilityService.boltRidesReady()) {
                    move(Stage.DELAY,"Пошук поїздки вже відкрито. Очікую 2 секунди...");
                } else if(ScreenControlAccessibilityService.boltRidesAvailable()) {
                    long now=SystemClock.elapsedRealtime();
                    if(ridesVisibleSince<0L) ridesVisibleSince=now;
                    if(now-ridesVisibleSince>=RIDES_STABLE_MS)
                        move(Stage.RIDES,"Кнопка «Поїздки» з'явилася. Натискаю...");
                } else {
                    ridesVisibleSince=-1L;
                }
                break;
            case RIDES:{
                ScreenControlAccessibilityService.BoltStep result=
                    ScreenControlAccessibilityService.boltRides();
                if(result==ScreenControlAccessibilityService.BoltStep.CLICKED)
                    move(Stage.VERIFY_RIDES,"Натиснув плитку «Поїздки». Перевіряю, чи відкрився пошук...");
                else if(result==ScreenControlAccessibilityService.BoltStep.READY)
                    move(Stage.DELAY,"Поле Bolt готове. Очікую 2 секунди...");
                else if(result==ScreenControlAccessibilityService.BoltStep.BLOCKED){
                    finish(false,"Не вдалося відкрити «Поїздки».");return;
                }
                break;
            }
            case VERIFY_RIDES:
                if(ScreenControlAccessibilityService.boltRidesReady()) {
                    move(Stage.DELAY,"«Поїздки» відкрито. Очікую 2 секунди...");
                } else if(age>=RETAP_AFTER_MS &&
                    ScreenControlAccessibilityService.boltRidesAvailable()) {
                    if(ridesRetries>=2) {
                        finish(false,"Bolt показує «Поїздки», але не відкриває поле адреси. Спробуй натиснути плитку вручну.");
                        return;
                    }
                    // A gesture can be accepted by Android before Bolt handles
                    // it. Retap only while the verified tile is still present.
                    ridesRetries++;
                    ScreenControlAccessibilityService.BoltStep again=
                        ScreenControlAccessibilityService.boltRides();
                    if(again==ScreenControlAccessibilityService.BoltStep.BLOCKED) {
                        finish(false,"Android не дозволив натиснути «Поїздки».");return;
                    }
                    move(Stage.VERIFY_RIDES,"Bolt ще показує «Поїздки». Перевіряю повторне натискання...");
                }
                break;
            case DELAY:
                if(age>=2000L){
                    if(address.isEmpty()){
                        finish(true,"Відкрив Bolt → «Поїздки». Назви пункт призначення.");return;
                    }
                    // Bolt automatically activates the address field after "Поїздки".
                    // Do not tap any search/destination control again.
                    move(Stage.TYPE,"Вводжу адресу в уже активне поле Bolt...");
                }
                break;
            case TYPE:{
                ScreenControlAccessibilityService.BoltStep result=
                    ScreenControlAccessibilityService.boltTypeDestination(address);
                if(result==ScreenControlAccessibilityService.BoltStep.ENTERED){
                    finish(true,"У Bolt введено: "+address+
                        ". Обери правильну підказку й підтвердь поїздку вручну.");
                    return;
                }
                if(result==ScreenControlAccessibilityService.BoltStep.BLOCKED){
                    finish(false,"Bolt не дозволив безпечно ввести адресу.");return;
                }
                break;
            }
        }
        if(!finished)HANDLER.postDelayed(tick,POLL_MS);
    }
    private void finish(boolean ok,String info){
        if(finished)return;
        finished=true;HANDLER.removeCallbacks(tick);
        synchronized(BoltTaxiController.class){if(active==this)active=null;}
        JarvisPhoneModeManager.releaseForCommand(lease,ok);
        listener.onFinished(ok,info);
    }
}
