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
    // Polling accesses an external Accessibility tree. Leave room for Bolt's
    // own UI thread so repeated traversals cannot monopolize Jarvis main.
    private static final long POLL_MS=650L;
    // Retry only if the explicit «Рушаймо» control appears again.
    private static final long RETAP_AFTER_MS=2300L;
    private static final long TIMEOUT_MS=30000L;
    private static final long TOTAL_TIMEOUT_MS=45000L;
    private static BoltTaxiController active;
    public interface Listener {
        void onProgress(String status);
        void onFinished(boolean ok,String status);
    }
    private enum Stage { OPEN_APP, VERIFY_RIDES, DELAY, FOCUS_DROPOFF, WAIT_DROPOFF, TYPE, ENTER }
    private final Context context;
    private final Listener listener;
    private final String address;
    private final JarvisPhoneModeManager.Lease lease;
    private final Runnable tick=this::pollSafely;
    private Stage stage=Stage.OPEN_APP;
    private long started=SystemClock.elapsedRealtime();
    private final long flowStarted=started;
    private boolean finished;
    private int ridesRetries;
    private int dropoffRetries;
    // Accessibility nodes from Bolt can expire during a UI transition.
    // Retry a few times *only* during safe home/route verification,
    // without additional taps when the snapshot itself is invalid.
    private int transientScreenFailures;

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
        listener.onProgress("Відкриваю Bolt. Чекаю появи «Рушаймо»...");
        HANDLER.postDelayed(job.tick,POLL_MS);
    }
    private void move(Stage next,String info){
        stage=next;started=SystemClock.elapsedRealtime();
        transientScreenFailures=0;
        listener.onProgress(info);
    }
    /** Recover from unexpected Bolt/Accessibility runtime failures without
     * letting a stale node exception crash all of Jarvis. Log only the stage,
     * never the dictated destination or a screenshot. */
    private void pollSafely() {
        if(finished)return;
        try {
            poll();
        } catch (RuntimeException error) {
            android.util.Log.w("JARVIS_TAXI",
                "Bolt Accessibility snapshot failed at stage="+stage.name()+
                ", retry="+transientScreenFailures,error);
            // Loading Bolt and switching to its rides screen can invalidate
            // an AccessibilityNodeInfo while traversing the tree. A single
            // exception is NOT proof the app failed to open. Re-acquire a fresh
            // root on the next tick. Do not retry data entry or booking stages.
            if ((stage==Stage.OPEN_APP || stage==Stage.VERIFY_RIDES ||
                 stage==Stage.FOCUS_DROPOFF || stage==Stage.WAIT_DROPOFF) &&
                ++transientScreenFailures<=4 &&
                SystemClock.elapsedRealtime()-flowStarted<TOTAL_TIMEOUT_MS) {
                HANDLER.postDelayed(tick,Math.max(850L,POLL_MS));
                return;
            }
            try {
                finish(false,"Bolt не дозволив прочитати екран під час «"+stage.name()+
                    "» після повторних спроб. Перевір дозвіл керування екраном "+
                    "і чи Bolt відкритий. Поїздку не замовляв.");
            } catch (RuntimeException cleanupError) {
                android.util.Log.e("JARVIS_TAXI","Taxi cleanup failed",cleanupError);
                finished=true;
                HANDLER.removeCallbacks(tick);
                synchronized(BoltTaxiController.class) {
                    if(active==this)active=null;
                }
                try { JarvisPhoneModeManager.releaseForCommand(lease,false); }
                catch(RuntimeException ignored) { }
            }
        }
    }

    private void poll(){
        if(finished)return;
        if(!JarvisPhoneScreenGuard.available(context)){
            finish(false,JarvisPhoneScreenGuard.MESSAGE);return;
        }
        if(!ScreenControlAccessibilityService.isSessionActive()){
            finish(false,"Керування екраном вимкнулося.");return;
        }
        long age=SystemClock.elapsedRealtime()-started;
        if(SystemClock.elapsedRealtime()-flowStarted>=TOTAL_TIMEOUT_MS){
            finish(false,"Bolt не показав потрібний екран за 45 секунд. Нічого навмання не натискав.");
            return;
        }
        if(!ScreenControlAccessibilityService.boltVisible()){
            if(age>=TIMEOUT_MS)finish(false,"Bolt не відкрився або екран заблоковано.");
            else HANDLER.postDelayed(tick,POLL_MS);
            return;
        }
        if(age>=TIMEOUT_MS){
            finish(false,stage==Stage.OPEN_APP
                ? "Не з'явився напис «Рушаймо» на головному екрані Bolt за 30 секунд."
                : "Не знайшов «Місце висадки» або поле адреси Bolt за 30 секунд. Нічого навмання не натискав.");
            return;
        }
        switch(stage){
            case OPEN_APP: {
                // Bolt may load in 1s or 12s; NEVER click from a fixed timer.
                // Observe exact «Рушаймо» on the verified passenger HOME tile.
                ScreenControlAccessibilityService.BoltStep result=
                    ScreenControlAccessibilityService.boltLetsGo();
                if(result==ScreenControlAccessibilityService.BoltStep.READY) {
                    move(Stage.DELAY,"Маршрут Bolt уже відкритий. Шукаю «Місце висадки»...");
                } else if(result==ScreenControlAccessibilityService.BoltStep.CLICKED) {
                    move(Stage.VERIFY_RIDES,
                        "Натиснув «Рушаймо». Чекаю появи «Місце висадки»...");
                } else if(result==ScreenControlAccessibilityService.BoltStep.BLOCKED) {
                    finish(false,"Bolt показав інший або небезпечний екран. «Рушаймо» не натискав.");
                    return;
                }
                break;
            }
            case VERIFY_RIDES:
                if(ScreenControlAccessibilityService.boltRidesReady()) {
                    move(Stage.DELAY,"«Поїздки» відкрито. Шукаю «Місце висадки»...");
                } else if(age>=RETAP_AFTER_MS && ridesRetries==0) {
                    // At most ONE retry, only when «Рушаймо» is still
                    // explicitly visible. Never tap another unknown screen.
                    ScreenControlAccessibilityService.BoltStep again=
                        ScreenControlAccessibilityService.boltLetsGo();
                    if(again==ScreenControlAccessibilityService.BoltStep.READY) {
                        move(Stage.DELAY,"Маршрут Bolt відкрито. Шукаю «Місце висадки»...");
                    } else if(again==ScreenControlAccessibilityService.BoltStep.CLICKED) {
                        ridesRetries=1;
                        move(Stage.VERIFY_RIDES,"Повторив «Рушаймо». Перевіряю маршрут...");
                    } else if(again==ScreenControlAccessibilityService.BoltStep.BLOCKED) {
                        finish(false,"Bolt перейшов на інший екран; не натискатиму навмання.");
                        return;
                    }
                } else if(age>=7000L) {
                    finish(false,"Після «Рушаймо» не з'явився маршрут із «Місце висадки». Поїздку не замовляв.");
                    return;
                }
                break;
            case DELAY:
                if(age>=1400L){
                    if(address.isEmpty()){
                        finish(true,"Відкрив Bolt → «Рушаймо». Назви місце висадки.");return;
                    }
                    // After «Поїздки», Bolt displays a two-row route form.
                    // The destination row isn't editable until tapped.
                    move(Stage.FOCUS_DROPOFF,"Натискаю «Місце висадки»...");
                }
                break;
            case FOCUS_DROPOFF:{
                ScreenControlAccessibilityService.BoltStep action=
                    ScreenControlAccessibilityService.boltFocusDestination();
                if(action==ScreenControlAccessibilityService.BoltStep.READY)
                    move(Stage.TYPE,"Поле висадки активне. Вводжу адресу...");
                else if(action==ScreenControlAccessibilityService.BoltStep.CLICKED)
                    move(Stage.WAIT_DROPOFF,"Натиснув «Місце висадки». Очікую поле вводу...");
                else if(action==ScreenControlAccessibilityService.BoltStep.BLOCKED){
                    finish(false,"Не вдалося безпечно натиснути поле «Місце висадки».");return;
                }
                break;
            }
            case WAIT_DROPOFF:
                if(ScreenControlAccessibilityService.boltDestinationFocused()){
                    move(Stage.TYPE,"Поле висадки активне. Вводжу адресу...");
                }else if(age>=2600L){
                    if(dropoffRetries>=2){
                        finish(false,"Bolt не відкрив поле введення після натискання «Місце висадки».");return;
                    }
                    dropoffRetries++;
                    move(Stage.FOCUS_DROPOFF,"Повторно шукаю поле «Місце висадки»...");
                }
                break;
            case TYPE:{
                ScreenControlAccessibilityService.BoltStep result=
                    ScreenControlAccessibilityService.boltTypeDestination(address);
                if(result==ScreenControlAccessibilityService.BoltStep.ENTERED){
                    // ACTION_SET_TEXT can finish before Bolt refreshes its editor.
                    // Wait for the exact address to appear before pressing
                    // the keyboard Enter/Search key, not a Bolt confirmation.
                    move(Stage.ENTER,"Адресу введено. Натискаю Enter / Пошук...");
                    break;
                }
                if(result==ScreenControlAccessibilityService.BoltStep.BLOCKED){
                    finish(false,"Bolt не дозволив безпечно ввести адресу.");return;
                }
                break;
            }
            case ENTER:{
                ScreenControlAccessibilityService.BoltStep result=
                    ScreenControlAccessibilityService.boltPressDestinationEnter(address);
                if(result==ScreenControlAccessibilityService.BoltStep.SUBMITTED){
                    finish(true,"У Bolt введено: "+address+
                        ". Натиснув Enter / Пошук. Обери потрібну адресу з результатів " +
                        "і підтвердь поїздку вручну.");
                    return;
                }
                if(result==ScreenControlAccessibilityService.BoltStep.BLOCKED){
                    finish(false,"Адресу введено, але Android або Bolt не дозволив натиснути " +
                        "Enter через Спеціальні можливості. Натисни Enter / Пошук " +
                        "на клавіатурі вручну. Поїздку не підтверджував.");
                    return;
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
