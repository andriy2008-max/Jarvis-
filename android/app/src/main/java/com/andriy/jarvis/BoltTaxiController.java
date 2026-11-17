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
    private static final long POLL_MS=2000L;
    // A cold start of Bolt may be much slower than its previous 4s grace
    // period; never interpret timeout as permission to tap blindly.
    private static final long TOTAL_TIMEOUT_MS=90000L;
    private static BoltTaxiController active;
    public interface Listener {
        void onProgress(String status);
        void onFinished(boolean ok,String status);
    }
    private enum Stage { PREPARE, COORD_RIDES, COORD_DROPOFF, COORD_TYPE,
                         COORD_ENTER, SELECT_FIRST }
    private final Context context;
    private final Listener listener;
    private final String address;
    private final JarvisPhoneModeManager.Lease lease;
    private final Runnable tick=this::pollSafely;
    private Stage stage=Stage.PREPARE;
    private long started=SystemClock.elapsedRealtime();
    private final long flowStarted=started;
    private boolean finished;
    private boolean portalPending;
    private boolean portalSkipped;
    private boolean portalStartedRides;
    private ScreenControlAccessibilityService.BoltScreen lastObservedScreen;
    private long sameScreenSince;
    private int backPressedCount;
    private boolean selectionPending;
    private long firstSuggestionSeenAt;
    private long lastWaitNotice;
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
        listener.onProgress("Відкриваю Bolt. Чекаю, поки повністю "+
            "завантажиться головний екран; на інших сторінках повернуся «Назад» "+
            "до двох разів.");
        HANDLER.postDelayed(job.tick,500L);
    }
    private void move(Stage next,String info){
        stage=next;started=SystemClock.elapsedRealtime();
        portalPending=false;
        portalSkipped=false;
        listener.onProgress(info);
    }

    /** Stable screen across two snapshots, not just a 4-second timer. */
    private boolean observedStable(ScreenControlAccessibilityService.BoltScreen current){
        if(current!=lastObservedScreen) {
            lastObservedScreen=current;
            sameScreenSince=SystemClock.elapsedRealtime();
            return false;
        }
        return BoltUiLabels.stablePage(
            SystemClock.elapsedRealtime()-sameScreenSince);
    }

    private void waitMessage(String message) {
        long now=SystemClock.elapsedRealtime();
        if(now-lastWaitNotice>=5500L || lastWaitNotice==0L) {
            lastWaitNotice=now;
            listener.onProgress(message);
        }
    }

    /** Portal reads the current Bolt tree and taps ONE verified target.
     * WAIT is polled every 2 s; uncertain taps NEVER trigger coordinate retries. */
    private boolean maybeTapThroughPortal(Stage expected,PortalUiBridge.Target target) {
        if(!PortalUiBridge.available(context)||portalSkipped)return false;
        long stageAge=SystemClock.elapsedRealtime()-started;
        if(stageAge>11000L) {
            if(portalStartedRides && expected==Stage.COORD_DROPOFF) {
                finish(false,"Portal не знайшов «Місце висадки» після натискання "+
                    "«Рушаймо». Не натискав координати на непідтвердженій сторінці.");
                return true;
            }
            portalSkipped=true;
            listener.onProgress("Portal не побачив потрібну кнопку. "+
                "Переходжу до перевіреного резервного способу.");
            return false;
        }
        if(portalPending)return true;
        portalPending=true;
        operation=expected==Stage.COORD_RIDES?"PORTAL_RIDES":"PORTAL_DROPOFF";
        // Portal is a second Accessibility service. Ensure Jarvis's own
        // floating face never intercepts a Portal gesture.
        final Runnable restore;
        try { restore=FloatingJarvisService.hideForGesture(); }
        catch(RuntimeException blocked) {
            portalPending=false;
            finish(false,"Не вдалося приховати плаваюче обличчя перед "+
                "натисканням Portal. Ніяких координат не натискав.");
            return true;
        }
        PortalUiBridge.tap(context,target,(state,note)->{
            portalPending=false;
            try {restore.run();}
            catch(RuntimeException overlayFailure) {
                finish(false,"Не вдалося відновити плаваючий інтерфейс Джарвіса.");
                return;
            }
            if(finished||stage!=expected)return;
            switch(state) {
                case CLICKED:
                    if(expected==Stage.COORD_RIDES) {
                        portalStartedRides=true;
                        move(Stage.COORD_DROPOFF,
                            "Portal знайшов і натиснув «Рушаймо». "+
                            "Чекаю «Місце висадки»...");
                        HANDLER.postDelayed(tick,2000L);
                    } else {
                        if(address.isEmpty()) {
                            finish(true,"Відкрив «Місце висадки» через Portal. "+
                                "Назви адресу призначення.");
                        } else {
                            move(Stage.COORD_TYPE,
                                "Portal натиснув «Місце висадки». "+
                                "Перевіряю активне поле адреси...");
                            HANDLER.postDelayed(tick,800L);
                        }
                    }
                    break;
                case UNSURE:
                    finish(false,"Portal: не підтверджено дію «"+
                        (expected==Stage.COORD_RIDES?"Рушаймо":"Місце висадки")+
                        "». Жодного повторного натискання не робив.");
                    break;
                case UNAVAILABLE:
                    if(portalStartedRides && expected==Stage.COORD_DROPOFF){
                        finish(false,"Після натискання «Рушаймо» Portal став "+
                            "недоступним. Не натискаю координати навмання.");
                        return;
                    }
                    portalSkipped=true;
                    HANDLER.post(tick);
                    break;
                case WAIT:
                default:
                    listener.onProgress("Portal очікує кнопку Bolt. "+
                        "Знову перевірю елементи через 2 секунди...");
                    HANDLER.postDelayed(tick,2000L);
                    break;
            }
        });
        return true;
    }
    /**
     * Prefer the owner's opt-in, localhost Portal semantic UI tree.
     * If absent, preserve calibrated native gestures. Never use guessed
     * coordinates after a Portal gesture with uncertain confirmation.
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

    /**
     * Guarded Bolt flow configured by the device owner:
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
            finish(false,"Bolt не завершив послідовність за 90 секунд. "+
                "Не натискав на невідомі кнопки; поїздку не замовлено.");
            return;
        }
        operation="CHECK_ACCESSIBILITY";
        if(!JarvisPhoneScreenGuard.available(context) ||
           !ScreenControlAccessibilityService.isSessionActive()) {
            finish(false,"Розблокуй телефон і ввімкни керування екраном Джарвіса.");
            return;
        }
        switch(stage) {
            case PREPARE: {
                // Never tap during Bolt splash/loading or resume into an old
                // route/search screen. Back at most twice until HOME is visible.
                if(SystemClock.elapsedRealtime()-flowStarted<
                    BoltUiLabels.RIDES_TAP_DELAY_MS) {
                    HANDLER.postDelayed(tick,POLL_MS);return;
                }
                operation="WAIT_BOLT_LOADED";
                ScreenControlAccessibilityService.BoltScreen page=
                    ScreenControlAccessibilityService.boltScreen();
                boolean stable=observedStable(page);
                if(page==ScreenControlAccessibilityService.BoltScreen.WAIT) {
                    waitMessage("Bolt ще завантажується. "+
                        "Перевірю екран знову через 2 секунди...");
                    HANDLER.postDelayed(tick,POLL_MS);return;
                }
                if(page==ScreenControlAccessibilityService.BoltScreen.UNSAFE) {
                    finish(false,"Bolt відкрив екран підтвердження або оплати. "+
                        "Не натискаю «Назад» і не продовжую поїздку автоматично.");
                    return;
                }
                if(!stable) {
                    HANDLER.postDelayed(tick,POLL_MS);return;
                }
                if(page==ScreenControlAccessibilityService.BoltScreen.HOME) {
                    move(Stage.COORD_RIDES,
                        "Головний екран Bolt завантажений. Шукаю «Рушаймо»...");
                    HANDLER.post(tick);return;
                }
                if(backPressedCount>=2) {
                    finish(false,"Два рази повернувся «Назад», але головний "+
                        "екран Bolt не з'явився. Натискання зупинено.");
                    return;
                }
                operation="BOLT_BACK_TO_HOME";
                ScreenControlAccessibilityService.BoltStep back=
                    ScreenControlAccessibilityService.boltBackTowardHome();
                if(back==ScreenControlAccessibilityService.BoltStep.READY) {
                    HANDLER.postDelayed(tick,POLL_MS);return;
                }
                if(back==ScreenControlAccessibilityService.BoltStep.WAIT) {
                    HANDLER.postDelayed(tick,POLL_MS);return;
                }
                if(back!=ScreenControlAccessibilityService.BoltStep.CLICKED) {
                    finish(false,"Android не дозволив повернутися до "+
                        "головного екрана Bolt. Дії зупинено.");
                    return;
                }
                backPressedCount++;
                lastObservedScreen=null;
                listener.onProgress("Bolt відкрив попередній екран. "+
                    "Емулював «Назад» "+backPressedCount+"/2. "+
                    "Перевіряю наступний екран...");
                HANDLER.postDelayed(tick,1800L);
                return;
            }
            case COORD_RIDES:
                operation="CHECK_BOLT_HOME";
                if(ScreenControlAccessibilityService.boltScreen()!=
                    ScreenControlAccessibilityService.BoltScreen.HOME) {
                    waitMessage("Чекаю підтвердження головного екрана Bolt "+
                        "перед «Рушаймо»...");
                    HANDLER.postDelayed(tick,POLL_MS);
                    return;
                }
                if(maybeTapThroughPortal(Stage.COORD_RIDES,
                    PortalUiBridge.Target.BOLT_RIDES))return;
                operation="TAP_RUSHAYMO_PREP";
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
                ScreenControlAccessibilityService.BoltScreen dropoffScreen=
                    ScreenControlAccessibilityService.boltScreen();
                if(dropoffScreen==ScreenControlAccessibilityService.BoltScreen.SEARCH) {
                    if(address.isEmpty()) {
                        finish(true,"Bolt уже відкрив поле адреси.");return;
                    }
                    move(Stage.COORD_TYPE,"Поле адреси вже активне. Вводжу адресу...");
                    HANDLER.post(tick);return;
                }
                if(dropoffScreen==ScreenControlAccessibilityService.BoltScreen.UNSAFE){
                    finish(false,"Bolt відкрив екран підтвердження/оплати. "+
                        "Нічого не натискаю.");return;
                }
                if(dropoffScreen!=ScreenControlAccessibilityService.BoltScreen.ROUTE){
                    waitMessage("Чекаю завантаження сторінки «Маршрут» "+
                        "перед натисканням «Місце висадки»...");
                    HANDLER.postDelayed(tick,POLL_MS);return;
                }
                if(maybeTapThroughPortal(Stage.COORD_DROPOFF,
                    PortalUiBridge.Target.BOLT_DROPOFF))return;
                operation="TAP_DROPOFF_PREP";
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
                operation="TYPE_BOLT_ADDRESS";
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
                operation="SUBMIT_BOLT_ENTER";
                ScreenControlAccessibilityService.BoltStep enter=
                    ScreenControlAccessibilityService.boltSubmitDestinationSearch();
                if(enter==ScreenControlAccessibilityService.BoltStep.SUBMITTED) {
                    move(Stage.SELECT_FIRST,
                        "Enter натиснуто. Очікую перший варіант адреси зі списку Bolt...");
                    HANDLER.postDelayed(tick,900L);
                } else if(enter==ScreenControlAccessibilityService.BoltStep.BLOCKED) {
                    finish(false,"Android не дозволив натиснути Enter у Bolt. "+
                        "Адресу вже введено. Поїздку не замовляв.");
                } else HANDLER.postDelayed(tick,POLL_MS);
                return;
            case SELECT_FIRST: {
                if(selectionPending)return;
                if(elapsed>18000L) {
                    finish(false,"Bolt не показав доступний перший варіант адреси "+
                        "після Enter. Адресу введено, але результат не вибрано.");
                    return;
                }
                if(!ScreenControlAccessibilityService.boltFirstSuggestionVisible()) {
                    firstSuggestionSeenAt=0L;
                    waitMessage("Шукаю першу підказку адреси Bolt. "+
                        "Перевірю через 2 секунди...");
                    HANDLER.postDelayed(tick,POLL_MS);return;
                }
                long now=SystemClock.elapsedRealtime();
                if(firstSuggestionSeenAt==0L) {
                    firstSuggestionSeenAt=now;
                    HANDLER.postDelayed(tick,950L);return;
                }
                if(!BoltUiLabels.stablePage(now-firstSuggestionSeenAt)) {
                    HANDLER.postDelayed(tick,500L);return;
                }
                operation="BOLT_FIRST_ADDRESS_SUGGESTION";
                selectionPending=true;
                ScreenControlAccessibilityService.BoltStep selected=
                    ScreenControlAccessibilityService.boltSelectFirstSuggestion(
                        (ok,detail)->{
                            selectionPending=false;
                            if(finished || stage!=Stage.SELECT_FIRST)return;
                            finish(ok,ok
                                ? "Натиснув перший видимий варіант адреси в "+
                                  "Bolt після Enter. Перевір назву й маршрут; "+
                                  "поїздку не замовляв."
                                : "Bolt не підтвердив вибір першої адреси: "+
                                  detail+". Повторно не натискав.");
                        });
                if(selected!=ScreenControlAccessibilityService.BoltStep.CLICKED) {
                    selectionPending=false;
                    if(selected==ScreenControlAccessibilityService.BoltStep.BLOCKED) {
                        finish(false,"Перший варіант адреси в Bolt не можна "+
                            "безпечно натиснути. Поїздку не замовляв.");
                    } else {
                        HANDLER.postDelayed(tick,POLL_MS);
                    }
                } else listener.onProgress(
                    "Натискаю перший варіант адреси, як на скриншоті...");
                return;
            }
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
