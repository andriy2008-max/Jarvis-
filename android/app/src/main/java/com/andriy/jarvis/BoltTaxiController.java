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
    // Poll the app foreground conservatively; only user-calibrated taps run.
    private static final long POLL_MS=650L;
    private static final long TOTAL_TIMEOUT_MS=45000L;
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
    private final long flowStarted=started;
    private boolean finished;
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
        listener.onProgress("Відкриваю Bolt. Використовую збережені координати, без читання екрана...");
        HANDLER.postDelayed(job.tick,POLL_MS);
    }
    private void move(Stage next,String info){
        stage=next;started=SystemClock.elapsedRealtime();
        listener.onProgress(info);
    }
    /**
     * The only supported flow is the owner's fixed-coordinate Bolt sequence.
     * Never fall back to stale OPEN_APP/Accessibility-tree navigation:
     * those stages depend on brittle Bolt labels and are not recoverable
     * when Android hides the window hierarchy.
     *
     * An unexpected error stops the command without repeating a tap.
     */
    private void pollSafely() {
        if(finished)return;
        try {
            pollCoordinates();
        } catch(RuntimeException error) {
            android.util.Log.w("JARVIS_TAXI",
                "Coordinate Bolt stage failed: "+stage.name(),error);
            finish(false,"Bolt: помилка координатного керування на етапі "+
                stage.name()+". Перевір, чи Bolt відкритий і чи ввімкнено "+
                "спеціальні можливості Джарвіса. Поїздку не замовляв.");
        }
    }

    /**
     * Coordinate-only Bolt flow configured by the device owner:
     * home «Рушаймо» (333,645), 2s later «Місце висадки» (450,520).
     * Each coordinate is tapped once. If the destination editor belongs to
     * Bolt, Android's Accessibility InputMethod enters the spoken address.
     * After the address is entered, Enter is sent once to the same Bolt editor.
     * No UI text reads, auto-retries on unknown pages, or ride confirmation.
     */
    private void pollCoordinates() {
        if(finished)return;
        long elapsed=SystemClock.elapsedRealtime()-started;
        if(SystemClock.elapsedRealtime()-flowStarted>=TOTAL_TIMEOUT_MS) {
            finish(false,"Bolt не завершив послідовність за 45 секунд. Поїздку не замовлено.");
            return;
        }
        if(!JarvisPhoneScreenGuard.available(context) ||
           !ScreenControlAccessibilityService.isSessionActive()) {
            finish(false,"Розблокуй телефон і ввімкни керування екраном Джарвіса.");
            return;
        }
        switch(stage) {
            case COORD_RIDES:
                // Preserve the existing 4s app-load grace period. Never tap
                // merely because a timer elapsed: require Bolt foreground too.
                if(elapsed<BoltUiLabels.RIDES_TAP_DELAY_MS) {
                    HANDLER.postDelayed(tick,POLL_MS);return;
                }
                if(!ScreenControlAccessibilityService.boltCoordinateReady()) {
                    if(elapsed>12000L) {
                        finish(false,"Не вдалося підтвердити, що відкритий Bolt. Натискання зупинено.");
                    } else HANDLER.postDelayed(tick,POLL_MS);
                    return;
                }
                ScreenControlAccessibilityService.boltTapCalibrated(333,645,
                    (ok,detail)->{
                        if(finished)return;
                        if(!ok) {
                            finish(false,"Кнопка «Рушаймо»: "+detail);return;
                        }
                        move(Stage.COORD_DROPOFF,
                            "«Рушаймо» натиснуто. Через 2 секунди відкрию «Місце висадки».");
                        HANDLER.postDelayed(tick,2000L);
                    });
                return;
            case COORD_DROPOFF:
                if(elapsed<2000L) {
                    HANDLER.postDelayed(tick,2000L-elapsed);return;
                }
                if(!ScreenControlAccessibilityService.boltCoordinateReady()) {
                    finish(false,"Bolt не підтверджено перед натисканням «Місце висадки».");
                    return;
                }
                ScreenControlAccessibilityService.boltTapCalibrated(450,520,
                    (ok,detail)->{
                        if(finished)return;
                        if(!ok) {
                            finish(false,"«Місце висадки»: "+detail);return;
                        }
                        if(address.isEmpty()) {
                            finish(true,"Відкрив «Місце висадки». Назви адресу призначення.");
                            return;
                        }
                        move(Stage.COORD_TYPE,"Шукаю активне поле Bolt для введення адреси...");
                        HANDLER.postDelayed(tick,800L);
                    });
                return;
            case COORD_TYPE:
                if(elapsed>8000L) {
                    finish(false,"Поле введення Bolt не активувалося. Адресу не вводив.");
                    return;
                }
                ScreenControlAccessibilityService.BoltStep typed=
                    ScreenControlAccessibilityService.boltTypeViaInputMethod(address);
                if(typed==ScreenControlAccessibilityService.BoltStep.ENTERED) {
                    // The user explicitly asked for Enter after typing.
                    // Do not assume commitText alone triggers Bolt's search.
                    move(Stage.COORD_ENTER,
                        "Адресу введено. Через 500 мс натисну Enter для пошуку...");
                    HANDLER.postDelayed(tick,500L);
                    return;
                } else if(typed==ScreenControlAccessibilityService.BoltStep.BLOCKED) {
                    finish(false,"Android не дозволив введення в поле Bolt. "+
                        "Перевір, чи відкрилося поле адреси.");
                } else HANDLER.postDelayed(tick,POLL_MS);
                return;
            case COORD_ENTER:
                if(elapsed<500L) {
                    HANDLER.postDelayed(tick,500L-elapsed);return;
                }
                if(elapsed>8000L) {
                    finish(false,"Bolt не підтвердив активне поле адреси для Enter. "+
                        "Адресу введено, але Enter не натискав. Поїздку не замовляв.");
                    return;
                }
                ScreenControlAccessibilityService.BoltStep enter=
                    ScreenControlAccessibilityService.boltSubmitDestinationSearch();
                if(enter==ScreenControlAccessibilityService.BoltStep.SUBMITTED) {
                    finish(true,"Ввів адресу «"+address+
                        "» і один раз натиснув Enter у пошуку Bolt. "+
                        "Перевір правильність результату. Поїздку не замовляв.");
                } else if(enter==ScreenControlAccessibilityService.BoltStep.BLOCKED) {
                    finish(false,"Android не дозволив натиснути Enter у Bolt. "+
                        "Адресу вже введено. Поїздку не замовляв.");
                } else HANDLER.postDelayed(tick,POLL_MS);
                return;
            default:
                finish(false,"Невідома стадія введення адреси Bolt.");
        }
    }

    private void finish(boolean ok,String info){
        if(finished)return;
        finished=true;HANDLER.removeCallbacks(tick);
        synchronized(BoltTaxiController.class){if(active==this)active=null;}
        JarvisPhoneModeManager.releaseForCommand(lease,ok);
        listener.onFinished(ok,info);
    }
}
