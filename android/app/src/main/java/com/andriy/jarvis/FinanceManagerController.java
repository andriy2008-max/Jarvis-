package com.andriy.jarvis;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * Money Manager expense/income entry by a user-requested voice command.
 * Uses only verified Accessibility screens of com.freeman.moneymanager.
 * Never performs a transfer or edits existing entries. No blind retries
 * after the final save gesture, avoiding duplicated financial records.
 */
public final class FinanceManagerController {
    private static final String PACKAGE="com.freeman.moneymanager";
    private static final Handler HANDLER=new Handler(Looper.getMainLooper());
    private static final long POLL_MS=480L;
    private static final long LIMIT_MS=68000L;
    private static FinanceManagerController active;

    public interface Listener {
        void onProgress(String message);
        void onFinished(boolean success,String message);
    }
    private enum Stage {
        OPEN, WAIT_ADD, TYPE, WAIT_TYPE, CATEGORY, WAIT_EDITOR,
        OPEN_ACCOUNT, WAIT_ACCOUNT, SELECT_ACCOUNT, WAIT_AMOUNT,
        KEY, VERIFY, SAVE, VERIFY_SAVED
    }

    private final Context context;
    private final FinanceVoiceCommands.Entry entry;
    private final Listener listener;
    private final JarvisPhoneModeManager.Lease lease;
    private final Runnable tick=this::poll;
    private final long openedAt=SystemClock.elapsedRealtime();
    private Stage stage=Stage.OPEN;
    private long since=openedAt;
    private int digitIndex;
    private boolean finished;
    private boolean saveAttempted;
    private int attempts;

    private FinanceManagerController(Context context,FinanceVoiceCommands.Entry entry,
                                     Listener listener,JarvisPhoneModeManager.Lease lease){
        this.context=context;
        this.entry=entry;
        this.listener=listener;
        this.lease=lease;
    }
    public static synchronized boolean isRunning(){return active!=null;}
    public static void cancel(){
        FinanceManagerController flow;
        synchronized(FinanceManagerController.class){flow=active;}
        if(flow!=null)flow.finish(false,
            flow.saveAttempted?"Натискання збереження вже виконано. Перевір запис у застосунку.":
                "Запис витрати/доходу скасовано до збереження.");
    }

    public static void start(Context context,String utterance,Listener listener) {
        if(listener==null)return;
        FinanceVoiceCommands.Entry request=FinanceVoiceCommands.parse(utterance);
        if(request==null){
            listener.onFinished(false,"Не розпізнав суму або категорію. " +
                "Спробуй «Запиши витрату 350 гривень на харчування».");return;
        }
        if(context.getPackageManager().hasSystemFeature(
            android.content.pm.PackageManager.FEATURE_WATCH)){
            listener.onFinished(false,"Фінансовий запис виконується на телефоні.");return;
        }
        synchronized(FinanceManagerController.class){
            if(active!=null){listener.onFinished(false,
                "Попередня операція ще виконується. Не створюватиму дублікат.");return;}
        }
        Intent launch=context.getPackageManager().getLaunchIntentForPackage(PACKAGE);
        if(launch==null){
            listener.onFinished(false,
                "Не встановлено «Менеджер фінансів» (com.freeman.moneymanager).");return;
        }
        JarvisPhoneModeManager.Lease lease=JarvisPhoneModeManager.acquireForCommand(context);
        if(!lease.granted){listener.onFinished(false,lease.message);return;}
        try {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch(RuntimeException error) {
            JarvisPhoneModeManager.releaseForCommand(lease,false);
            listener.onFinished(false,"Android не зміг відкрити «Менеджер фінансів».");return;
        }
        FinanceManagerController task=new FinanceManagerController(context,request,listener,lease);
        synchronized(FinanceManagerController.class){active=task;}
        listener.onProgress("Відкриваю «Менеджер фінансів» для "+
            (request.kind==FinanceVoiceCommands.Kind.EXPENSE?"витрати":"доходу")+
            " "+request.amount+" грн...");
        HANDLER.postDelayed(task.tick,700L);
    }
    private void move(Stage next,String status){
        stage=next;
        since=SystemClock.elapsedRealtime();
        listener.onProgress(status);
    }
    private void poll(){
        if(finished)return;
        if (!JarvisPhoneScreenGuard.available(context)) {
            finish(false,JarvisPhoneScreenGuard.MESSAGE + (saveAttempted
                ? " Збереження вже натиснуто: спочатку перевір історію, не повторюй запис." : ""));
            return;
        }
        if(!ScreenControlAccessibilityService.isSessionActive()){
            finish(false,"Керування екраном вимкнулося. Фінансову операцію зупинено.");return;
        }
        long elapsed=SystemClock.elapsedRealtime()-since;
        if(SystemClock.elapsedRealtime()-openedAt>LIMIT_MS){
            finish(false,"Час очікування «Менеджера фінансів» вичерпано. "+
                (saveAttempted?"Перевір, чи запис збережено.":"Запис не підтверджував."));return;
        }
        if(!ScreenControlAccessibilityService.financeVisible()){
            HANDLER.postDelayed(tick,POLL_MS);return;
        }
        ScreenControlAccessibilityService.FinanceScreen screen=
            ScreenControlAccessibilityService.financeScreen();
        switch(stage){
            case OPEN:
                if(screen==ScreenControlAccessibilityService.FinanceScreen.HOME){
                    ScreenControlAccessibilityService.FinanceStep step=
                        ScreenControlAccessibilityService.financePlus();
                    if(step==ScreenControlAccessibilityService.FinanceStep.TAPPED)
                        move(Stage.WAIT_ADD,"Відкриваю форму операції...");
                    else if(step==ScreenControlAccessibilityService.FinanceStep.BLOCKED){
                        finish(false,"Неможливо безпечно натиснути «+».");return;
                    }
                } else if(screen==ScreenControlAccessibilityService.FinanceScreen.CATEGORY) {
                    move(Stage.TYPE,"Форма «Додати» вже відкрита...");
                } else if(elapsed>11000L) {
                    finish(false,"Не впізнав головний екран «Менеджера фінансів».");return;
                }
                break;
            case WAIT_ADD:
                if(screen==ScreenControlAccessibilityService.FinanceScreen.CATEGORY)
                    move(Stage.TYPE,"Вибираю тип операції...");
                else if(elapsed>7000L){
                    finish(false,"Після «+» форма додавання не відкрилася.");return;
                }
                break;
            case TYPE:
                if(screen!=ScreenControlAccessibilityService.FinanceScreen.CATEGORY)break;
                ScreenControlAccessibilityService.FinanceStep typed=
                    ScreenControlAccessibilityService.financeType(entry.kind);
                if(typed==ScreenControlAccessibilityService.FinanceStep.TAPPED)
                    move(Stage.WAIT_TYPE,"Обрав тип. Перевіряю категорії...");
                else if(typed==ScreenControlAccessibilityService.FinanceStep.BLOCKED){
                    finish(false,"Не зміг вибрати тип операції.");return;
                }
                break;
            case WAIT_TYPE:
                if(elapsed>=650L && screen==ScreenControlAccessibilityService.FinanceScreen.CATEGORY)
                    move(Stage.CATEGORY,"Шукаю категорію «"+entry.category+"»...");
                else if(elapsed>=7000L){
                    finish(false,"Категорії не з'явилися.");return;
                }
                break;
            case CATEGORY:
                if(screen!=ScreenControlAccessibilityService.FinanceScreen.CATEGORY)break;
                ScreenControlAccessibilityService.FinanceStep category=
                    ScreenControlAccessibilityService.financeCategory(entry);
                if(category==ScreenControlAccessibilityService.FinanceStep.TAPPED)
                    move(Stage.WAIT_EDITOR,"Вибрав категорію. Очікую суму/рахунок...");
                else if(category==ScreenControlAccessibilityService.FinanceStep.BLOCKED){
                    finish(false,"Не зміг безпечно вибрати категорію.");return;
                }
                break;
            case WAIT_EDITOR:
                if(screen==ScreenControlAccessibilityService.FinanceScreen.ACCOUNTS)
                    move(Stage.SELECT_ACCOUNT,"Перевіряю рахунок...");
                else if(screen==ScreenControlAccessibilityService.FinanceScreen.AMOUNT)
                    move(entry.account==null?Stage.KEY:Stage.OPEN_ACCOUNT,
                        entry.account==null?"Вводжу суму...":"Вибираю вказаний рахунок...");
                else if(elapsed>8500L){
                    finish(false,"Не знайшов поле суми після вибору категорії.");return;
                }
                break;
            case OPEN_ACCOUNT:
                if(screen==ScreenControlAccessibilityService.FinanceScreen.ACCOUNTS) {
                    move(Stage.SELECT_ACCOUNT,"Вибираю рахунок...");
                } else if(screen==ScreenControlAccessibilityService.FinanceScreen.AMOUNT) {
                    ScreenControlAccessibilityService.FinanceStep pick=
                        ScreenControlAccessibilityService.financeOpenAccounts();
                    if(pick==ScreenControlAccessibilityService.FinanceStep.TAPPED)
                        move(Stage.WAIT_ACCOUNT,"Відкриваю рахунки...");
                    else if(pick==ScreenControlAccessibilityService.FinanceStep.BLOCKED){
                        finish(false,"Не можу відкрити вибір рахунку.");return;
                    }
                }
                break;
            case WAIT_ACCOUNT:
                if(screen==ScreenControlAccessibilityService.FinanceScreen.ACCOUNTS)
                    move(Stage.SELECT_ACCOUNT,"Знаходжу рахунок...");
                else if(elapsed>7000L){
                    finish(false,"Вибір рахунків не відкрився.");return;
                }
                break;
            case SELECT_ACCOUNT:
                if(screen==ScreenControlAccessibilityService.FinanceScreen.AMOUNT) {
                    move(Stage.KEY,"Вводжу суму...");
                } else if(screen==ScreenControlAccessibilityService.FinanceScreen.ACCOUNTS) {
                    ScreenControlAccessibilityService.FinanceStep choice=
                        ScreenControlAccessibilityService.financeAccount(entry.account);
                    if(choice==ScreenControlAccessibilityService.FinanceStep.TAPPED)
                        move(Stage.WAIT_AMOUNT,entry.account==null?
                            "Залишаю поточний рахунок...":
                            "Вибрав рахунок «"+entry.account+"»...");
                    else if(choice==ScreenControlAccessibilityService.FinanceStep.BLOCKED){
                        finish(false,"Рахунок недоступний або не впізнаний.");return;
                    }
                }
                break;
            case WAIT_AMOUNT:
                if(screen==ScreenControlAccessibilityService.FinanceScreen.AMOUNT)
                    move(Stage.KEY,"Поле суми готове...");
                else if(elapsed>8000L){
                    finish(false,"Після вибору рахунку сума не відкрилася.");return;
                }
                break;
            case KEY:
                if(screen!=ScreenControlAccessibilityService.FinanceScreen.AMOUNT)break;
                String digits=entry.keypad();
                if(digitIndex==0) {
                    String visible=ScreenControlAccessibilityService.financeVisibleAmount();
                    if(visible!=null && !FinanceManagerUiLabels.amountEquals("0",visible)) {
                        finish(false,"У полі вже є сума «"+visible+
                            "». Не додаватиму цифри до попереднього запису.");return;
                    }
                }
                if(digitIndex<digits.length()) {
                    ScreenControlAccessibilityService.FinanceStep key=
                        ScreenControlAccessibilityService.financeDigit(
                            digits.charAt(digitIndex));
                    if(key==ScreenControlAccessibilityService.FinanceStep.TAPPED) {
                        digitIndex++;
                        if(digitIndex==digits.length())
                            move(Stage.VERIFY,"Перевіряю введену суму перед збереженням...");
                    } else if(key==ScreenControlAccessibilityService.FinanceStep.BLOCKED) {
                        finish(false,"Не вдалося ввести цифру на клавіатурі.");return;
                    }
                }
                break;
            case VERIFY:
                if(screen!=ScreenControlAccessibilityService.FinanceScreen.AMOUNT){
                    finish(false,"Форма змінилася до перевірки суми. Не зберігав.");return;
                }
                String actual=ScreenControlAccessibilityService.financeVisibleAmount();
                if(FinanceManagerUiLabels.amountEquals(entry.amount,actual))
                    move(Stage.SAVE,"Сума збігається. Зберігаю операцію...");
                else if(elapsed>4500L){
                    finish(false,"Не зміг підтвердити суму "+entry.amount+
                        " грн на екрані. Не натискав кнопку збереження.");return;
                }
                break;
            case SAVE:
                if(screen!=ScreenControlAccessibilityService.FinanceScreen.AMOUNT){
                    finish(false,"Форма змінилася; збереження не натискав.");return;
                }
                // At most ONE submission gesture, even if app does not respond.
                // This avoids accidentally duplicating an expense.
                saveAttempted=true;
                ScreenControlAccessibilityService.FinanceStep saved=
                    ScreenControlAccessibilityService.financeConfirm(entry.amount);
                if(saved==ScreenControlAccessibilityService.FinanceStep.TAPPED)
                    move(Stage.VERIFY_SAVED,"Натиснув ✓. Перевіряю збереження...");
                else{
                    finish(false,"Не зміг підтвердити суму перед збереженням. "+
                        "Перевір форму вручну.");return;
                }
                break;
            case VERIFY_SAVED:
                if(screen==ScreenControlAccessibilityService.FinanceScreen.HOME) {
                    finish(false,"Збереження натиснуто, форма закрилася. Не можу підтвердити запис " +
                        entry.amount+" грн, «"+entry.category+"» в історії. " +
                        "Перевір операцію вручну; не повторюй команду до перевірки.");
                    return;
                }
                if(elapsed>7000L){
                    finish(false,"Натиснув ✓, але не підтвердив повернення до списку "+
                        "операцій. Перевір запис вручну, не повторюй команду без перевірки.");
                    return;
                }
                break;
        }
        if(!finished)HANDLER.postDelayed(tick,POLL_MS);
    }
    private void finish(boolean success,String result){
        if(finished)return;
        finished=true;
        HANDLER.removeCallbacks(tick);
        synchronized(FinanceManagerController.class){if(active==this)active=null;}
        JarvisPhoneModeManager.releaseForCommand(lease,success);
        listener.onFinished(success,result);
    }
}
