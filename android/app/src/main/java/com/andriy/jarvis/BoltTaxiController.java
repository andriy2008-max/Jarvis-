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
    private static final long TOTAL_TIMEOUT_MS=90000L;
    private static BoltTaxiController active;
    public interface Listener {
        void onProgress(String status);
        void onFinished(boolean ok,String status);
    }
    private enum Stage { WAIT_OPEN, BACK_ONE, BACK_TWO, AFTER_BACK, COORD_RIDES, COORD_DROPOFF, COORD_TYPE, COORD_ENTER, PICK_SUGGESTION }
    private final Context context;
    private final Listener listener;
    private final String address;
    private final JarvisPhoneModeManager.Lease lease;
    private final Runnable tick=this::pollSafely;
    private Stage stage=Stage.WAIT_OPEN;
    private long started=SystemClock.elapsedRealtime();
    private final long flowStarted=started;
    private boolean finished;
    private boolean relaunched;
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
        listener.onProgress("Відкриваю Bolt. Чекаю завантаження головного екрана...");
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

    /** If Back left Bolt, reopen once instead of pressing Back on the launcher. */
    private boolean reopenAfterBack() {
        if(relaunched)return false;
        relaunched=true;
        Intent launch=context.getPackageManager().getLaunchIntentForPackage(BOLT);
        if(launch==null)return false;
        try {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            move(Stage.WAIT_OPEN,"Bolt вийшов після «Назад». Відкриваю повторно...");
            HANDLER.postDelayed(tick,POLL_MS);
            return true;
        }catch(RuntimeException error) {
            return false;
        }
    }

    /**
     * A foreground-package check alone cannot distinguish splash, previous
     * route and home. Wait for real UI markers, back out of a previous page
     * at most twice, then perform the user's calibrated taps only on the
     * matching screens. Poll missing UI again after 2s (bounded by timeout).
     */
    private void pollCoordinates() {
        if(finished)return;
        final long now=SystemClock.elapsedRealtime();
        final long elapsed=now-started;
        if(now-flowStarted>=TOTAL_TIMEOUT_MS) {
            finish(false,"Bolt не завершив пошук за 90 секунд. Поїздку не замовлено.");
            return;
        }
        operation="CHECK_ACCESSIBILITY";
        if(!JarvisPhoneScreenGuard.available(context) ||
           !ScreenControlAccessibilityService.isSessionActive()) {
            finish(false,"Розблокуй телефон і ввімкни керування екраном Джарвіса.");
            return;
        }
        switch(stage) {
            case WAIT_OPEN:
                operation="WAIT_FOR_BOLT_HOME";
                if(elapsed<BoltUiLabels.RIDES_TAP_DELAY_MS) {
                    HANDLER.postDelayed(tick,POLL_MS);return;
                }
                if(ScreenControlAccessibilityService.boltHomeReady()) {
                    move(Stage.COORD_RIDES,"Bolt завантажився. Натискаю «Рушаймо».");
                    HANDLER.post(tick);return;
                }
                // Route/search left from the user's previous visit: reset it.
                // A loading screen is NOT permission to press Back.
                if(elapsed>=6000L &&
                   ScreenControlAccessibilityService.boltPageReadyForBack()) {
                    move(Stage.BACK_ONE,"Відкрито попередній екран Bolt. Повертаюся назад.");
                    HANDLER.post(tick);return;
                }
                if(elapsed>=25000L) {
                    finish(false,"Не з'явився головний екран Bolt. Натискання зупинено.");
                    return;
                }
                HANDLER.postDelayed(tick,2000L);return;
            case BACK_ONE:
                operation="BOLT_BACK_1";
                if(ScreenControlAccessibilityService.boltHomeReady()) {
                    move(Stage.COORD_RIDES,"Головний екран Bolt знайдено.");
                    HANDLER.post(tick);return;
                }
                if(!ScreenControlAccessibilityService.boltCoordinateReady()) {
                    if(reopenAfterBack())return;
                    finish(false,"Після повернення Bolt більше не на екрані.");
                    return;
                }
                if(!ScreenControlAccessibilityService.boltPageReadyForBack()) {
                    if(elapsed>8000L) {
                        finish(false,"Не вдалося визначити екран Bolt для «Назад».");
                    }else HANDLER.postDelayed(tick,2000L);
                    return;
                }
                if(!ScreenControlAccessibilityService.boltBack()) {
                    finish(false,"Android не дозволив натиснути «Назад» у Bolt.");
                    return;
                }
                move(Stage.BACK_TWO,"Перший крок «Назад» виконано. Перевіряю екран...");
                HANDLER.postDelayed(tick,800L);return;
            case BACK_TWO:
                operation="BOLT_BACK_2";
                // Do not exit the app just to press Back exactly twice.
                if(ScreenControlAccessibilityService.boltHomeReady()) {
                    move(Stage.COORD_RIDES,"Bolt повернувся на головний екран.");
                    HANDLER.post(tick);return;
                }
                if(!ScreenControlAccessibilityService.boltCoordinateReady()) {
                    if(reopenAfterBack())return;
                    finish(false,"Bolt закрився під час повернення.");
                    return;
                }
                if(!ScreenControlAccessibilityService.boltPageReadyForBack()) {
                    if(elapsed>8000L) {
                        finish(false,"Другий «Назад» не виконано: екран не готовий.");
                    }else HANDLER.postDelayed(tick,2000L);
                    return;
                }
                if(!ScreenControlAccessibilityService.boltBack()) {
                    finish(false,"Android не дозволив другий «Назад» у Bolt.");
                    return;
                }
                move(Stage.AFTER_BACK,"Другий крок «Назад» виконано. Чекаю головного екрана...");
                HANDLER.postDelayed(tick,1000L);return;
            case AFTER_BACK:
                operation="WAIT_HOME_AFTER_BACK";
                if(ScreenControlAccessibilityService.boltHomeReady()) {
                    move(Stage.COORD_RIDES,"Знайдено «Рушаймо». Продовжую пошук адреси.");
                    HANDLER.post(tick);return;
                }
                if(!ScreenControlAccessibilityService.boltCoordinateReady() &&
                   elapsed>=1500L && reopenAfterBack())return;
                if(elapsed>=12000L) {
                    finish(false,"Bolt не повернувся на головний екран після «Назад».");
                    return;
                }
                HANDLER.postDelayed(tick,2000L);return;
            case COORD_RIDES:
                operation="TAP_RUSHAYMO";
                if(!ScreenControlAccessibilityService.boltHomeReady()) {
                    if(elapsed>=15000L) {
                        finish(false,"Кнопка «Рушаймо» зникла або головний екран не готовий.");
                    }else HANDLER.postDelayed(tick,2000L);
                    return;
                }
                ScreenControlAccessibilityService.boltTapCalibrated(333,645,
                    (ok,detail)->{
                        if(finished)return;
                        if(!ok) {
                            finish(false,"Кнопка «Рушаймо»: "+detail);return;
                        }
                        move(Stage.COORD_DROPOFF,
                            "«Рушаймо» натиснуто. Чекаю на «Маршрут»...");
                        HANDLER.postDelayed(tick,1200L);
                    });
                return;
            case COORD_DROPOFF:
                operation="WAIT_ROUTE_AND_TAP_DROPOFF";
                if(ScreenControlAccessibilityService.boltDestinationFocused()) {
                    if(address.isEmpty()) {
                        finish(true,"Поле адреси Bolt відкрите.");return;
                    }
                    move(Stage.COORD_TYPE,"Поле Bolt активне. Вводжу адресу...");
                    HANDLER.postDelayed(tick,450L);return;
                }
                if(!ScreenControlAccessibilityService.boltRidesReady()) {
                    if(elapsed>=15000L) {
                        finish(false,"Екран «Маршрут» не з'явився після «Рушаймо».");
                    }else HANDLER.postDelayed(tick,2000L);
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
                        move(Stage.COORD_TYPE,"Натиснув «Місце висадки». Чекаю поле вводу...");
                        HANDLER.postDelayed(tick,600L);
                    });
                return;
            case COORD_TYPE:
                operation="TYPE_BOLT_ADDRESS";
                if(elapsed>=12000L) {
                    finish(false,"Поле введення Bolt не активувалося. Адресу не вводив.");
                    return;
                }
                ScreenControlAccessibilityService.BoltStep typed=
                    ScreenControlAccessibilityService.boltTypeViaInputMethod(address);
                if(typed==ScreenControlAccessibilityService.BoltStep.ENTERED) {
                    move(Stage.COORD_ENTER,"Адресу введено. Натискаю Enter...");
                    HANDLER.postDelayed(tick,500L);
                }else if(typed==ScreenControlAccessibilityService.BoltStep.BLOCKED) {
                    finish(false,"Android не дозволив введення у поле Bolt.");
                }else HANDLER.postDelayed(tick,2000L);
                return;
            case COORD_ENTER:
                operation="SUBMIT_BOLT_ENTER";
                if(elapsed<500L) {
                    HANDLER.postDelayed(tick,500L-elapsed);return;
                }
                if(elapsed>=12000L) {
                    finish(false,"Bolt не підтвердив поле адреси для Enter.");
                    return;
                }
                ScreenControlAccessibilityService.BoltStep enter=
                    ScreenControlAccessibilityService.boltSubmitDestinationSearch();
                if(enter==ScreenControlAccessibilityService.BoltStep.SUBMITTED) {
                    move(Stage.PICK_SUGGESTION,
                        "Enter натиснуто. Шукаю перший варіант адреси...");
                    HANDLER.postDelayed(tick,850L);
                }else if(enter==ScreenControlAccessibilityService.BoltStep.BLOCKED) {
                    finish(false,"Android не дозволив Enter у Bolt.");
                }else HANDLER.postDelayed(tick,2000L);
                return;
            case PICK_SUGGESTION:
                operation="SELECT_FIRST_BOLT_SUGGESTION";
                if(elapsed<850L) {
                    HANDLER.postDelayed(tick,850L-elapsed);return;
                }
                if(elapsed>=16000L) {
                    finish(false,"Bolt не показав доступний перший варіант адреси. "+
                        "Нічого навмання не натискав.");
                    return;
                }
                if(!ScreenControlAccessibilityService.boltCoordinateReady()) {
                    finish(false,"Bolt зник з екрана до вибору адреси.");
                    return;
                }
                ScreenControlAccessibilityService.BoltStep selected=
                    ScreenControlAccessibilityService.boltSelectFirstSuggestion(
                        (ok,detail)->{
                            if(finished)return;
                            finish(ok,ok ?
                                "Натиснув перший варіант адреси в Bolt. "+
                                "Перевір маршрут перед замовленням. Поїздку не замовляв." :
                                "Не вдалося вибрати першу адресу: "+detail);
                        });
                if(selected==ScreenControlAccessibilityService.BoltStep.WAIT)
                    HANDLER.postDelayed(tick,2000L);
                else if(selected==ScreenControlAccessibilityService.BoltStep.BLOCKED)
                    finish(false,"Немає дозволу на вибір першої адреси в Bolt.");
                return;
            default:
                finish(false,"Невідомий етап пошуку адреси Bolt.");
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
