package com.andriy.jarvis;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/** Ordered Bolt passenger UI flow with visible-control retries; never books a ride. */
public final class BoltTaxiController {
    private static final String BOLT="ee.mtakso.client";
    private static final Handler HANDLER=new Handler(Looper.getMainLooper());
    // Re-read the visible requested control; never repeat a blind coordinate tap.
    private static final long POLL_MS=2000L;
    private static BoltTaxiController active;
    public interface Listener {
        void onProgress(String status);
        void onFinished(boolean ok,String status);
    }
    private enum Stage { COORD_RIDES, COORD_DROPOFF, COORD_TYPE, COORD_ENTER }
    private final Context context;
    private final Listener listener;
    private final String address;
    private final JarvisPhoneModeManager.Lease lease;
    private final Runnable tick=this::pollSafely;
    private Stage stage=Stage.COORD_RIDES;
    private long started=SystemClock.elapsedRealtime();
    private boolean finished;
    /** A non-sensitive marker for the exact operation that failed.
     * Keep street addresses and recognition transcripts out of logs. */
    private String operation="WAIT_BOLT";

    private BoltTaxiController(Context context,Listener listener,String address,JarvisPhoneModeManager.Lease lease){
        this.context=context.getApplicationContext();
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
        if(!lease.granted){listener.onFinished(false,lease.message);return;}
        try{context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
        catch(RuntimeException error){
            JarvisPhoneModeManager.releaseForCommand(lease);
            listener.onFinished(false,"Android не дозволив відкрити Bolt.");return;
        }
        BoltTaxiController job=new BoltTaxiController(context,listener,address,lease);
        synchronized(BoltTaxiController.class){active=job;}
        job.progress("Відкриваю Bolt. Через 4 секунди перевірю кнопку «Поїздки / Рушаймо»...");
        HANDLER.postDelayed(job.tick,4000L);
    }
    private void move(Stage next,String info){
        stage=next;started=SystemClock.elapsedRealtime();
        progress(info);
    }
    private void pollSafely() {
        if(finished)return;
        try {
            pollCoordinates();
        } catch(RuntimeException error) {
            android.util.Log.w("JARVIS_TAXI",
                "Bolt operation "+operation+" failed in "+stage.name(),error);
            // Do not report only COORD_RIDES: the operation code distinguishes
            // foreground detection, overlay preparation and gesture dispatch.
            // The exception class is safe to show (never its message/stack).
            finish(false,"Bolt: збій на кроці "+operation+
                " ("+error.getClass().getSimpleName()+"). "+
                "Перевір службу спеціальних можливостей Джарвіса. "+
                "Поїздку не замовляв.");
        }
    }

    private void progress(String text) {
        JarvisActionNotice.show(context,text);
        listener.onProgress(text);
    }

    /** Advance only when the requested control is present. Never order a ride. */
    private void pollCoordinates() {
        if(finished)return;
        if(!JarvisPhoneScreenGuard.available(context) ||
           !ScreenControlAccessibilityService.isSessionActive()) {
            finish(false,"Керування Bolt зупинено. Розблокуй телефон і перевір спеціальні можливості.");return;
        }
        if(!ScreenControlAccessibilityService.boltVisible()) {
            progress("Очікую екран Bolt. Перевірю через 2 с. Скасування: «Скасуй таксі».");
            HANDLER.postDelayed(tick,POLL_MS);return;
        }
        Stage before=stage;
        ScreenControlAccessibilityService.BoltStep result;
        switch(stage) {
            case COORD_RIDES:
                operation="FIND_RIDES";
                result=ScreenControlAccessibilityService.boltRides();
                if(result==ScreenControlAccessibilityService.BoltStep.WAIT)
                    result=ScreenControlAccessibilityService.boltLetsGo();
                if(result==ScreenControlAccessibilityService.BoltStep.READY ||
                   result==ScreenControlAccessibilityService.BoltStep.CLICKED)
                    move(Stage.COORD_DROPOFF,"Відкриваю маршрут. Очікую «Місце висадки»...");
                break;
            case COORD_DROPOFF:
                operation="FOCUS_DROPOFF";
                result=ScreenControlAccessibilityService.boltFocusDestination();
                if(result==ScreenControlAccessibilityService.BoltStep.READY) {
                    if(address.isEmpty()) {finish(true,"Поле призначення відкрито. Назви адресу.");return;}
                    move(Stage.COORD_TYPE,"Поле призначення готове. Вводжу адресу...");
                } else if(result==ScreenControlAccessibilityService.BoltStep.CLICKED)
                    progress("Натиснув «Місце висадки». Очікую активне поле...");
                else if(SystemClock.elapsedRealtime()-started>=4000L)
                    ScreenControlAccessibilityService.boltRides();
                break;
            case COORD_TYPE:
                operation="TYPE_ADDRESS";
                result=ScreenControlAccessibilityService.boltTypeDestination(address);
                if(result!=ScreenControlAccessibilityService.BoltStep.ENTERED)
                    result=ScreenControlAccessibilityService.boltTypeViaInputMethod(address);
                if(result==ScreenControlAccessibilityService.BoltStep.ENTERED)
                    move(Stage.COORD_ENTER,"Адресу введено. Натискаю Пошук/Enter...");
                else if(result==ScreenControlAccessibilityService.BoltStep.BLOCKED) {
                    finish(false,"Android відхилив введення адреси Bolt.");return;
                }
                break;
            case COORD_ENTER:
                operation="SUBMIT_SEARCH";
                result=ScreenControlAccessibilityService.boltPressDestinationEnter(address);
                if(result!=ScreenControlAccessibilityService.BoltStep.SUBMITTED)
                    result=ScreenControlAccessibilityService.boltSubmitDestinationSearch();
                if(result==ScreenControlAccessibilityService.BoltStep.SUBMITTED) {
                    finish(true,"Ввів адресу й натиснув Enter у Bolt. Перевір маршрут. Поїздку не замовляв.");return;
                } else if(result==ScreenControlAccessibilityService.BoltStep.BLOCKED) {
                    finish(false,"Android відхилив Enter. Адресу введено; поїздку не замовляв.");return;
                }
                break;
        }
        if(before==stage)progress("Bolt: очікую потрібну кнопку або поле. Перевірю через 2 с. Скасування: «Скасуй таксі».");
        HANDLER.postDelayed(tick,POLL_MS);
    }

    private void finish(boolean ok,String info){
        if(finished)return;
        finished=true;HANDLER.removeCallbacks(tick);
        synchronized(BoltTaxiController.class){if(active==this)active=null;}
        JarvisPhoneModeManager.releaseForCommand(lease,ok);
        JarvisActionNotice.show(context,info);
        listener.onFinished(ok,info);
    }
}
