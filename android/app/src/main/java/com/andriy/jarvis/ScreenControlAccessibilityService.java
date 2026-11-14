package com.andriy.jarvis;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Explicit user-operated accessibility helper.
 *
 * Never takes spontaneous actions on accessibility events. Screen labels stay
 * on device; they are never sent to the chat AI or recorded to analytics/logs.
 * Supports locally recognized user-goal synonyms, accessibility labels, and
 * resource IDs; explicitly requested one-step scroll/back and stop only.
 */
public final class ScreenControlAccessibilityService extends AccessibilityService {
    private static volatile WeakReference<ScreenControlAccessibilityService> connected =
        new WeakReference<>(null);
    private static final int MAX_SCAN = 600;
    private static final int MAX_MATCHES = 3;

    private volatile boolean sessionActive;
    // Foreground identity without reading Bolt's often-inaccessible view tree.
    // Only updated from system window transitions; never starts actions itself.
    private volatile String foregroundWindowPackage = "";
    private volatile long foregroundWindowAt;

    private WindowManager windows;
    private View buttonOverlay;
    private SpeechRecognizer recognizer;
    private JarvisHeadsetAudioRouter headsetRoute;
    private JarvisListeningAudioFocus listeningAudioFocus;
    private boolean recording;
    private final Handler main = new Handler(Looper.getMainLooper());
    private JarvisSequenceExecutor sequences;
    private SpotifyPlaybackController spotifyPlayback;
    private JarvisMacroPointPicker macroPointPicker;

    public static boolean isConnected() { return connected.get() != null; }

    /** Uses Android's supported lock-screen global action, not PIN injection. */
    public static boolean lockDevice() {
        ScreenControlAccessibilityService service = connected.get();
        if (service == null) return false;
        try { return service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN); }
        catch (RuntimeException denied) { return false; }
    }
    public static boolean isSessionActive() {
        ScreenControlAccessibilityService s = connected.get();
        return s != null && s.sessionActive;
    }

    public static String start() {
        // Legacy entry also uses the ONE existing pixel face, not a second
        // white Accessibility microphone button.
        return startFromBubble();
    }

    /**
     * Starts a user-requested accessibility session controlled by the separate
     * Quick Settings orb. No second floating microphone is displayed.
     * The user must first grant Accessibility in Android system settings.
     */
    public static String startFromBubble() {
        ScreenControlAccessibilityService service = connected.get();
        if (service == null)
            return "Увімкни Джарвіс — керування екраном у Спеціальних можливостях Android.";
        // Remove a stale microphone overlay left by an older invocation.
        if (service.buttonOverlay != null && service.windows != null) {
            try { service.windows.removeView(service.buttonOverlay); }
            catch (Exception ignored) {}
            service.buttonOverlay = null;
        }
        service.sessionActive = true;
        JarvisPhoneMode.setActive(true);
        return "Керування екраном активне в обличчі Джарвіса.";
    }

    public static String stop() {
        ScreenControlAccessibilityService s = connected.get();
        if (s != null) s.stopSession();
        return "Керування екраном зупинено.";
    }

    /**
     * Read only the currently visible app identity for a user-requested
     * sequential command. No screen text is transmitted or persisted.
     */
    public static String visibleExternalPackage() {
        ScreenControlAccessibilityService service = connected.get();
        if (service == null || !service.sessionActive) return null;
        AccessibilityNodeInfo root = service.currentExternalRoot();
        if (root == null || root.getPackageName() == null) return null;
        return root.getPackageName().toString();
    }

    /** On-demand, read-only foreground check for an explicitly saved macro.
     * No need to activate screen-control mode just to evaluate a condition.
     * Returns null for Jarvis itself, system UI, unavailable accessibility,
     * or when no ordinary foreground app is visible. No screen text is read. */
    public static String foregroundPackageForMacro() {
        ScreenControlAccessibilityService service = connected.get();
        if (service == null) return null;
        AccessibilityNodeInfo root = service.currentExternalRoot();
        if (root == null || !root.isVisibleToUser() || root.getPackageName() == null)
            return null;
        return root.getPackageName().toString();
    }

    public interface MacroActionCallback { void onComplete(boolean success, String detail); }

    /** A deliberate local macro step, never AI-generated or an accessibility-event trigger.
     * All tap gestures require an exact app package and known screen geometry. */
    public static void macroTap(String packageName, String rawPoint, MacroActionCallback cb) {
        ScreenControlAccessibilityService service = connected.get();
        if (service == null) { cb.onComplete(false, "Увімкни службу спеціальних можливостей Джарвіса."); return; }
        service.main.post(() -> service.tapMacroPoint(packageName, rawPoint, cb));
    }

    private void tapMacroPoint(String packageName, String rawPoint, MacroActionCallback cb) {
        if(macroPointPicker!=null) {
            cb.onComplete(false,"Заверши вибір точки перед виконанням макросу.");
            return;
        }
        if (!JarvisForegroundApp.validPackage(packageName)) {
            cb.onComplete(false, "У кроці не вибрано програму."); return;
        }
        JarvisMacroScreenRules.Point point;
        try { point = JarvisMacroScreenRules.parsePoint(rawPoint); }
        catch (Exception error) { cb.onComplete(false, error.getMessage()); return; }
        AccessibilityNodeInfo root = currentExternalRoot();
        if (root == null || root.getPackageName() == null ||
            !packageName.equals(root.getPackageName().toString()) ||
            !root.isVisibleToUser()) {
            cb.onComplete(false, "Активна програма не збігається з програмою макросу.");
            return;
        }
        Rect bounds=new Rect();
        root.getBoundsInScreen(bounds);
        if(bounds.width()<120 || bounds.height()<160) {
            cb.onComplete(false,"Не вдалося визначити область екрана."); return;
        }
        int x=bounds.left+(int)Math.round(bounds.width()*point.x/100);
        int y=bounds.top+(int)Math.round(bounds.height()*point.y/100);
        // Do not tap password fields or visually labelled destructive/payment buttons.
        ArrayDeque<AccessibilityNodeInfo> nodes=new ArrayDeque<>();
        nodes.add(root);
        int visited=0;
        while(!nodes.isEmpty() && ++visited<=MAX_SCAN) {
            AccessibilityNodeInfo node=nodes.removeFirst();
            if(node.isVisibleToUser()) {
                Rect box=new Rect(); node.getBoundsInScreen(box);
                if(box.contains(x,y) &&
                    (node.isPassword() || isSensitiveInputType(node.getInputType()) ||
                    isSensitiveFieldLabel(node) ||
                    JarvisMacroScreenRules.riskyLabel(asString(node.getText())+" "+
                        asString(node.getContentDescription())))) {
                    cb.onComplete(false, "Це захищена або ризикована кнопка. Натисни вручну.");
                    return;
                }
                for(int i=0;i<node.getChildCount();i++){
                    AccessibilityNodeInfo child=node.getChild(i);
                    if(child!=null)nodes.add(child);
                }
            }
        }
        if(visited>MAX_SCAN) {
            cb.onComplete(false,"Екран надто складний для безпечного натискання."); return;
        }
        // Hide the floating Jarvis orb before hit testing. Wait until WindowManager
        // applies NOT_TOUCHABLE, verify app identity once more, then tap exactly once.
        FloatingJarvisService.avoidControl(new Rect(x-24,y-24,x+24,y+24));
        final Runnable restore=FloatingJarvisService.hideForGesture();
        final java.util.concurrent.atomic.AtomicBoolean completed=
            new java.util.concurrent.atomic.AtomicBoolean(false);
        final java.util.function.BiConsumer<Boolean,String> finish=(ok,reason)->{
            if(!completed.compareAndSet(false,true))return;
            restore.run();
            cb.onComplete(ok,reason);
        };
        main.postDelayed(()->{
            AccessibilityNodeInfo current=currentExternalRoot();
            if(connected.get()!=this || current==null ||
                current.getPackageName()==null ||
                !packageName.equals(current.getPackageName().toString())) {
                finish.accept(false,"Програма змінилася до початку натискання.");return;
            }
            Rect frame=new Rect();
            current.getBoundsInScreen(frame);
            if(!frame.contains(x,y)) {
                finish.accept(false,"Координати поза поточним вікном.");return;
            }
            Path path=new Path();
            path.moveTo(x,y);
            GestureDescription gesture=new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path,0,90))
                .build();
            try {
                boolean queued=dispatchGesture(gesture,new GestureResultCallback(){
                    @Override public void onCompleted(GestureDescription description) {
                        finish.accept(true,"Натискання виконано.");
                    }
                    @Override public void onCancelled(GestureDescription description) {
                        finish.accept(false,"Android скасував натискання.");
                    }
                },main);
                if(!queued)finish.accept(false,"Android не дозволив натискання.");
                else main.postDelayed(()->
                    finish.accept(false,"Час очікування жесту вичерпано."),5500);
            } catch(RuntimeException error) {
                finish.accept(false,"Не вдалося виконати натискання.");
            }
        },80);
    }

    /** Read-only label wait: no implicit tap and no UI text leaves the device. */
    public static boolean macroLabelVisible(String packageName,String label) {
        ScreenControlAccessibilityService service=connected.get();
        if(service==null || !JarvisForegroundApp.validPackage(packageName) ||
            label==null || label.trim().isEmpty() || label.length()>100)return false;
        AccessibilityNodeInfo root=service.currentExternalRoot();
        if(root==null || root.getPackageName()==null ||
            !packageName.equals(root.getPackageName().toString()))return false;
        ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>();
        queue.add(root);
        int visited=0;
        while(!queue.isEmpty() && ++visited<MAX_SCAN) {
            AccessibilityNodeInfo node=queue.removeFirst();
            if(!node.isVisibleToUser() || node.isPassword())continue;
            if(JarvisMacroScreenRules.matchesLabel(label,asString(node.getText())) ||
               JarvisMacroScreenRules.matchesLabel(label,asString(node.getContentDescription())))
                return true;
            for(int i=0;i<node.getChildCount();i++){
                AccessibilityNodeInfo child=node.getChild(i);
                if(child!=null)queue.addLast(child);
            }
        }
        return false;
    }

    /** Prefer a uniquely labelled control over replaying a saved screen coordinate. */
    public static void macroTapLabel(String packageName,String label,MacroActionCallback cb) {
        ScreenControlAccessibilityService service=connected.get();
        if(service==null){cb.onComplete(false,"Спеціальні можливості неактивні.");return;}
        service.main.post(()->{
            if(service.macroPointPicker!=null) {
                cb.onComplete(false,"Закрий вибір точки перед виконанням макросу.");return;
            }
            if(!JarvisForegroundApp.validPackage(packageName) ||
                label==null || label.trim().isEmpty() || label.length()>100 ||
                JarvisMacroScreenRules.riskyLabel(label)) {
                cb.onComplete(false,"Небезпечна або порожня назва кнопки.");return;
            }
            AccessibilityNodeInfo root=service.currentExternalRoot();
            if(root==null || root.getPackageName()==null ||
                !packageName.equals(root.getPackageName().toString())){
                cb.onComplete(false,"Відкрита інша програма.");return;
            }
            ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>();
            queue.add(root);
            AccessibilityNodeInfo selected=null;
            Rect selectedBounds=null;
            int visited=0;
            while(!queue.isEmpty() && ++visited<=MAX_SCAN) {
                AccessibilityNodeInfo node=queue.removeFirst();
                if(!node.isVisibleToUser() || !node.isEnabled() || node.isPassword())continue;
                if(JarvisMacroScreenRules.matchesLabel(label,asString(node.getText())) ||
                   JarvisMacroScreenRules.matchesLabel(label,asString(node.getContentDescription()))) {
                    AccessibilityNodeInfo target=clickable(node);
                    if(target!=null && !JarvisMacroScreenRules.riskyLabel(
                        asString(target.getText())+" "+asString(target.getContentDescription()))) {
                        Rect bounds=new Rect();target.getBoundsInScreen(bounds);
                        if(bounds.width()>12 && bounds.height()>12) {
                            if(selectedBounds!=null && !selectedBounds.equals(bounds)) {
                                cb.onComplete(false,"Є кілька кнопок з такою назвою.");return;
                            }
                            selected=target;selectedBounds=bounds;
                        }
                    }
                }
                for(int i=0;i<node.getChildCount();i++){
                    AccessibilityNodeInfo child=node.getChild(i);
                    if(child!=null)queue.addLast(child);
                }
            }
            AccessibilityNodeInfo verified=service.currentExternalRoot();
            if(visited>MAX_SCAN || selected==null || verified==null ||
                verified.getPackageName()==null ||
                !packageName.equals(verified.getPackageName().toString()) ||
                !selected.isEnabled() || !selected.isVisibleToUser()) {
                cb.onComplete(false,"Кнопку не знайдено або екран змінився.");return;
            }
            if(selected.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                cb.onComplete(true,"Натиснув «"+label+"».");
            else cb.onComplete(false,"Android відхилив натискання кнопки.");
        });
    }

    /** Pollable check without changing focus or reading the input contents. */
    public static boolean macroFocusedEditable(String packageName) {
        ScreenControlAccessibilityService service=connected.get();
        if(service==null || !JarvisForegroundApp.validPackage(packageName))return false;
        AccessibilityNodeInfo root=service.currentExternalRoot();
        if(root==null || root.getPackageName()==null ||
            !packageName.equals(root.getPackageName().toString()))return false;
        AccessibilityNodeInfo focus=root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        return focus!=null && focus.getPackageName()!=null &&
            packageName.equals(focus.getPackageName().toString()) &&
            focus.isEditable() && focus.isFocused() && focus.isEnabled() &&
            focus.isVisibleToUser() && !focus.isPassword() &&
            !isSensitiveInputType(focus.getInputType()) &&
            !isSensitiveFieldLabel(focus);
    }

    public static void macroType(String packageName, String text, MacroActionCallback cb) {
        ScreenControlAccessibilityService service=connected.get();
        if(service==null){ cb.onComplete(false, "Увімкни службу спеціальних можливостей.");return;}
        service.main.post(()->{
            if(service.macroPointPicker!=null) {
                cb.onComplete(false,"Закрий вибір точки перед введенням тексту.");return;
            }
            if(!JarvisForegroundApp.validPackage(packageName) ||
                !JarvisMacroScreenRules.validText(text)) {
                cb.onComplete(false,"Невірна програма або текст макросу.");return;
            }
            AccessibilityNodeInfo root=service.currentExternalRoot();
            if(root==null || root.getPackageName()==null ||
                !packageName.equals(root.getPackageName().toString())) {
                cb.onComplete(false,"Для введення тексту відкрий обрану програму.");return;
            }
            String result=service.writeToFocusedField(text, true);
            cb.onComplete(result.startsWith("Текст введено в активне поле."),result);
        });
    }

    /** A visual selector that does not read screenshots or intercept real app
     * touches while the user navigates; accessibility grant is required. */
    public static String startMacroPointPicker(String packageName,JarvisMacroPointPicker.Callback callback) {
        ScreenControlAccessibilityService service=connected.get();
        if(service==null)return "Увімкни «Джарвіс — керування екраном» у Спеціальних можливостях.";
        if(!JarvisForegroundApp.validPackage(packageName))
            return "Спочатку вибери програму для точки.";
        if(service.macroPointPicker!=null)return "Вибір точки вже активний.";
        service.macroPointPicker=new JarvisMacroPointPicker(service,packageName,point->{
            service.macroPointPicker=null;
            if(callback!=null)callback.onPick(point);
        });
        try { service.macroPointPicker.show(); return ""; }
        catch(Exception e) {
            service.macroPointPicker=null;
            return "Не вдалося показати вибір координат.";
        }
    }

    public static void stopMacroPointPicker() {
        ScreenControlAccessibilityService service=connected.get();
        if(service!=null && service.macroPointPicker!=null) {
            service.macroPointPicker.close();
            service.macroPointPicker=null;
        }
    }

    public static boolean focusedEditableIn(String packageName) {
        ScreenControlAccessibilityService service = connected.get();
        if (service == null || !service.sessionActive || packageName == null)
            return false;
        AccessibilityNodeInfo root = service.currentExternalRoot();
        if (root == null || root.getPackageName() == null ||
            !packageName.equals(root.getPackageName().toString())) return false;
        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused == null || !focused.isEditable() || !focused.isFocused() ||
            !focused.isVisibleToUser() || !focused.isEnabled() ||
            focused.isPassword() || isSensitiveInputType(focused.getInputType()) ||
            isSensitiveFieldLabel(focused)) return false;
        return focused.getPackageName() != null &&
            packageName.contentEquals(focused.getPackageName());
    }

    /** Money Manager (Freeman): only the exact installed package can be
     * targeted. Financial amounts, notes and account names never leave the
     * device. A single user-initiated command cannot trigger bank transfers. */
    public enum FinanceScreen { HOME, CATEGORY, ACCOUNTS, AMOUNT, UNKNOWN }
    public enum FinanceStep { WAIT, TAPPED, BLOCKED }

    private AccessibilityNodeInfo financeRoot() {
        AccessibilityNodeInfo root=currentExternalRoot();
        return root!=null && root.isVisibleToUser() &&
            root.getPackageName()!=null &&
            "com.freeman.moneymanager".contentEquals(root.getPackageName())
            ? root : null;
    }

    public static boolean financeVisible() {
        ScreenControlAccessibilityService s=connected.get();
        return s!=null && s.sessionActive && s.financeRoot()!=null;
    }

    private static String financeLabel(AccessibilityNodeInfo node) {
        return asString(node.getText())+" "+asString(node.getContentDescription())+
            " "+asString(node.getHintText());
    }
    private static boolean financeLabelEquals(AccessibilityNodeInfo node,String text) {
        return FinanceManagerUiLabels.exact(asString(node.getText()),text) ||
            FinanceManagerUiLabels.exact(asString(node.getContentDescription()),text);
    }

    private FinanceScreen detectFinanceScreen(AccessibilityNodeInfo root) {
        if(root==null)return FinanceScreen.UNKNOWN;
        ArrayDeque<AccessibilityNodeInfo> q=new ArrayDeque<>();
        q.add(root);
        int visited=0;
        boolean home=false,transactions=false,add=false,expense=false,
            income=false,transfer=false,accounts=false,accountName=false,
            notes=false,seven=false,eight=false,nine=false;
        while(!q.isEmpty()&&++visited<=MAX_SCAN){
            AccessibilityNodeInfo n=q.removeFirst();
            if(!n.isVisibleToUser())continue;
            String label=financeLabel(n);
            String norm=FinanceManagerUiLabels.normalize(label);
            if(norm.contains("менеджер фінансів"))home=true;
            if(financeLabelEquals(n,"Транзакції"))transactions=true;
            if(financeLabelEquals(n,"Додати"))add=true;
            if(financeLabelEquals(n,"Витрата"))expense=true;
            if(financeLabelEquals(n,"Дохід"))income=true;
            if(financeLabelEquals(n,"Переказ"))transfer=true;
            if(financeLabelEquals(n,"Рахунки"))accounts=true;
            if(financeLabelEquals(n,"готівка")||financeLabelEquals(n,"карта моно"))
                accountName=true;
            if(norm.contains("примітка"))notes=true;
            if(financeLabelEquals(n,"7"))seven=true;
            if(financeLabelEquals(n,"8"))eight=true;
            if(financeLabelEquals(n,"9"))nine=true;
            for(int i=0;i<n.getChildCount();i++) {
                AccessibilityNodeInfo child=n.getChild(i);
                if(child!=null)q.addLast(child);
            }
        }
        if(visited>MAX_SCAN)return FinanceScreen.UNKNOWN;
        if(accounts && accountName)return FinanceScreen.ACCOUNTS;
        if(add && expense && income && transfer && notes &&
            seven && eight && nine)return FinanceScreen.AMOUNT;
        if(add && expense && income && transfer)return FinanceScreen.CATEGORY;
        if(home && transactions)return FinanceScreen.HOME;
        return FinanceScreen.UNKNOWN;
    }

    public static FinanceScreen financeScreen() {
        ScreenControlAccessibilityService s=connected.get();
        if(s==null || !s.sessionActive)return FinanceScreen.UNKNOWN;
        AccessibilityNodeInfo root=s.financeRoot();
        return root==null?FinanceScreen.UNKNOWN:s.detectFinanceScreen(root);
    }

    private AccessibilityNodeInfo findFinanceText(AccessibilityNodeInfo root,
                                                   String exact) {
        ArrayDeque<AccessibilityNodeInfo> q=new ArrayDeque<>();
        q.add(root);
        AccessibilityNodeInfo found=null;
        int scanned=0;
        while(!q.isEmpty() && ++scanned<=MAX_SCAN) {
            AccessibilityNodeInfo n=q.removeFirst();
            if(!n.isVisibleToUser() || !n.isEnabled() || n.isPassword())continue;
            if(financeLabelEquals(n,exact)) {
                if(found!=null) {
                    Rect prev=new Rect(),cur=new Rect();
                    found.getBoundsInScreen(prev);
                    n.getBoundsInScreen(cur);
                    if(!prev.equals(cur) && !prev.contains(cur) &&
                        !cur.contains(prev))return null; // Ambiguous
                    if(cur.width()*cur.height()<prev.width()*prev.height())
                        found=n;
                }else found=n;
            }
            for(int i=0;i<n.getChildCount();i++) {
                AccessibilityNodeInfo child=n.getChild(i);
                if(child!=null)q.addLast(child);
            }
        }
        return scanned>MAX_SCAN?null:found;
    }

    /** One user-initiated tap, re-checking the app, page and screen geometry.
     * The fallback coordinates are ONLY those in the supplied screenshots. */
    private FinanceStep financeTap(FinanceScreen expected, String name, int[] fallback) {
        AccessibilityNodeInfo root=financeRoot();
        if(root==null)return FinanceStep.WAIT;
        if(detectFinanceScreen(root)!=expected)return FinanceStep.WAIT;
        android.graphics.Point display=new android.graphics.Point();
        android.view.Display screen=getDisplay();
        if(screen==null){
            WindowManager wm=(WindowManager)getSystemService(Context.WINDOW_SERVICE);
            if(wm!=null)screen=wm.getDefaultDisplay();
        }
        if(screen==null)return FinanceStep.WAIT;
        screen.getRealSize(display);
        if(!FinanceManagerUiLabels.screenshotShape(display.x,display.y))
            return FinanceStep.BLOCKED;
        float x=-1,y=-1;
        // Prefer a visible labelled node to coordinates, but always ensure
        // its bounds fall inside the screenshot-calibrated target region.
        if(name!=null && fallback!=null){
            AccessibilityNodeInfo node=findFinanceText(root,name);
            if(node!=null) {
                Rect bounds=new Rect();
                node.getBoundsInScreen(bounds);
                float distance=Math.abs(bounds.centerX()-fallback[0])+
                    Math.abs(bounds.centerY()-fallback[1]);
                if(bounds.width()>5 && bounds.height()>5 &&
                    distance<display.x*.34f) {
                    x=bounds.exactCenterX();y=bounds.exactCenterY();
                }
            }
        }
        if(x<0||y<0){
            if(fallback==null)return FinanceStep.WAIT;
            x=fallback[0];y=fallback[1];
        }
        if(x<8||x>=display.x-8||y<display.y*.06f||y>=display.y*.95f)
            return FinanceStep.BLOCKED;
        Path path=new Path();
        path.moveTo(x,y);
        GestureDescription gesture=new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(path,0,100))
            .build();
        return dispatchTrackedGesture(gesture,(ok,description)->{
            if(!ok && sessionActive)toast("Менеджер фінансів: "+description);
        })?FinanceStep.TAPPED:FinanceStep.BLOCKED;
    }

    private int[] financeDisplaySize() {
        android.view.Display screen=getDisplay();
        if(screen==null){
            WindowManager wm=(WindowManager)getSystemService(Context.WINDOW_SERVICE);
            if(wm!=null)screen=wm.getDefaultDisplay();
        }
        if(screen==null)return null;
        android.graphics.Point p=new android.graphics.Point();
        screen.getRealSize(p);
        return FinanceManagerUiLabels.screenshotShape(p.x,p.y)
            ?new int[]{p.x,p.y}:null;
    }

    public static FinanceStep financePlus() {
        ScreenControlAccessibilityService s=connected.get();
        if(s==null||!s.sessionActive)return FinanceStep.BLOCKED;
        int[] d=s.financeDisplaySize();
        return d==null?FinanceStep.BLOCKED:
            s.financeTap(FinanceScreen.HOME,null,
                FinanceManagerUiLabels.plusPoint(d[0],d[1]));
    }
    public static FinanceStep financeType(FinanceVoiceCommands.Kind kind) {
        ScreenControlAccessibilityService s=connected.get();
        if(s==null||!s.sessionActive||kind==null)return FinanceStep.BLOCKED;
        int[] d=s.financeDisplaySize();
        return d==null?FinanceStep.BLOCKED:
            s.financeTap(FinanceScreen.CATEGORY,
                kind==FinanceVoiceCommands.Kind.EXPENSE?"Витрата":"Дохід",
                FinanceManagerUiLabels.typePoint(kind,d[0],d[1]));
    }
    public static FinanceStep financeCategory(FinanceVoiceCommands.Entry entry) {
        ScreenControlAccessibilityService s=connected.get();
        if(s==null||!s.sessionActive||entry==null)return FinanceStep.BLOCKED;
        int[] d=s.financeDisplaySize();
        if(d==null)return FinanceStep.BLOCKED;
        int[] point=FinanceManagerUiLabels.categoryPoint(
            entry.kind,entry.category,d[0],d[1]);
        return point==null?FinanceStep.BLOCKED:
            s.financeTap(FinanceScreen.CATEGORY,entry.category,point);
    }
    public static FinanceStep financeAccount(String account) {
        ScreenControlAccessibilityService s=connected.get();
        if(s==null||!s.sessionActive)return FinanceStep.BLOCKED;
        int[] d=s.financeDisplaySize();
        if(d==null)return FinanceStep.BLOCKED;
        // Without an explicit account, dismiss the sheet using its X.
        // This preserves the user's CURRENT app selection (cash/card/etc),
        // rather than guessing from the screenshot's old selection.
        if(account==null)
            return s.financeTap(FinanceScreen.ACCOUNTS,null,
                new int[]{Math.round(d[0]*46f/684f),
                    Math.round(d[1]*805f/1536f)});
        int[] point=FinanceManagerUiLabels.accountPoint(account,d[0],d[1]);
        return point==null?FinanceStep.BLOCKED:
            s.financeTap(FinanceScreen.ACCOUNTS,account,point);
    }
    public static FinanceStep financeOpenAccounts() {
        ScreenControlAccessibilityService s=connected.get();
        if(s==null||!s.sessionActive)return FinanceStep.BLOCKED;
        int[] d=s.financeDisplaySize();
        return d==null?FinanceStep.BLOCKED:
            s.financeTap(FinanceScreen.AMOUNT,null,
                new int[]{Math.round(d[0]*45f/684f),
                    Math.round(d[1]*942f/1536f)});
    }
    public static FinanceStep financeDigit(char digit) {
        ScreenControlAccessibilityService s=connected.get();
        if(s==null||!s.sessionActive)return FinanceStep.BLOCKED;
        int[] d=s.financeDisplaySize();
        if(d==null)return FinanceStep.BLOCKED;
        int[] point=FinanceManagerUiLabels.keypadPoint(digit,d[0],d[1]);
        return point==null?FinanceStep.BLOCKED:
            s.financeTap(FinanceScreen.AMOUNT,String.valueOf(digit),point);
    }

    /** Return only the large current transaction amount on the add screen,
     * not an account balance or a keypad digit. */
    public static String financeVisibleAmount() {
        ScreenControlAccessibilityService s=connected.get();
        if(s==null||!s.sessionActive)return null;
        AccessibilityNodeInfo root=s.financeRoot();
        if(root==null||s.detectFinanceScreen(root)!=FinanceScreen.AMOUNT)
            return null;
        int[] d=s.financeDisplaySize();
        if(d==null)return null;
        ArrayDeque<AccessibilityNodeInfo> q=new ArrayDeque<>();
        q.add(root);
        String value=null;
        int scanned=0;
        while(!q.isEmpty()&&++scanned<=MAX_SCAN){
            AccessibilityNodeInfo n=q.removeFirst();
            if(!n.isVisibleToUser())continue;
            Rect b=new Rect();
            n.getBoundsInScreen(b);
            if(b.centerX()>d[0]*.68f && b.centerY()>d[1]*.55f &&
                b.centerY()<d[1]*.67f) {
                String label=asString(n.getText()).trim();
                if(label.matches("[0-9][0-9 ,.\\u00a0\\u202f]*")){
                    if(value!=null && !value.equals(label))return null;
                    value=label;
                }
            }
            for(int i=0;i<n.getChildCount();i++){
                AccessibilityNodeInfo child=n.getChild(i);
                if(child!=null)q.addLast(child);
            }
        }
        return scanned>MAX_SCAN?null:value;
    }

    public static FinanceStep financeConfirm(String expectedAmount) {
        ScreenControlAccessibilityService s=connected.get();
        if(s==null||!s.sessionActive)return FinanceStep.BLOCKED;
        String shown=financeVisibleAmount();
        if(!FinanceManagerUiLabels.amountEquals(expectedAmount,shown))
            return FinanceStep.WAIT;
        int[] d=s.financeDisplaySize();
        return d==null?FinanceStep.BLOCKED:
            s.financeTap(FinanceScreen.AMOUNT,null,
                FinanceManagerUiLabels.confirmPoint(d[0],d[1]));
    }

    /** Only the user-requested Bolt passenger app may be controlled by these methods. */
    public enum BoltStep { WAIT, CLICKED, READY, ENTERED, SUBMITTED, BLOCKED }

    public static boolean boltVisible() {
        ScreenControlAccessibilityService service = connected.get();
        return service != null && service.sessionActive && service.currentBoltRoot() != null;
    }

    /**
     * Only two owner-calibrated Bolt points are allowed here. Android's
     * accessibility window-state event supplies app identity even when
     * Bolt hides its view hierarchy. This is NOT proof of a specific screen,
     * so the controller performs each tap at most once and never books.
     */
    public static boolean boltCoordinateReady() {
        ScreenControlAccessibilityService s=connected.get();
        return s!=null && s.sessionActive && s.boltWindowTrusted();
    }

    private boolean trustedExternalApp(String packageName) {
        if(!JarvisPhoneScreenGuard.available(this)) return false;
        try {
            AccessibilityNodeInfo root=currentExternalRoot();
            if(root!=null && root.getPackageName()!=null &&
                packageName.contentEquals(root.getPackageName()))return true;
        } catch(RuntimeException ignored) { /* App hides its view tree. */ }
        // Window identity comes from Android AccessibilityEvent, not screen text.
        return packageName.equals(foregroundWindowPackage) &&
            android.os.SystemClock.elapsedRealtime()-foregroundWindowAt<15000L;
    }

    private boolean boltWindowTrusted() {
        return trustedExternalApp("ee.mtakso.client");
    }

    public static boolean spotifyCoordinateReady() {
        ScreenControlAccessibilityService s=connected.get();
        return s!=null && s.sessionActive &&
            s.trustedExternalApp("com.spotify.music");
    }

    public static void spotifyTapCalibrated(int x,int y,Completion cb) {
        ScreenControlAccessibilityService s=connected.get();
        if(s==null || !s.sessionActive || cb==null) {
            if(cb!=null)cb.finished(false,"Служба керування екраном не активна.");
            return;
        }
        s.tapCalibratedForApp("com.spotify.music",x,y,null,cb);
    }

    /** A calibrated Play press is permitted only on the verified named
     * playlist's detail page. It never targets the bottom mini-player. */
    public static void spotifyTapNamedPlaylistPlay(String name,Completion cb) {
        ScreenControlAccessibilityService s=connected.get();
        if(cb==null)return;
        if(s==null || !s.sessionActive || name==null || name.trim().isEmpty()) {
            cb.finished(false,"Керування Spotify або назва плейлиста недоступні.");
            return;
        }
        s.tapCalibratedForApp("com.spotify.music",977,1291,name,cb);
    }

    public static void boltTapCalibrated(int x,int y,Completion cb) {
        ScreenControlAccessibilityService s=connected.get();
        if(s==null || !s.sessionActive || cb==null) {
            if(cb!=null)cb.finished(false,"Служба керування екраном не активна.");
            return;
        }
        s.tapCalibratedForApp("ee.mtakso.client",x,y,null,cb);
    }

    /**
     * Allow only the two owner-calibrated Bolt points and the explicitly
     * requested Spotify Search point. Do not infer coordinates on new devices.
     * Callback fires only after Android has completed or cancelled a gesture.
     */
    private boolean verifiedNamedPlaylistReady(String name) {
        if(name==null || name.trim().isEmpty())return false;
        AccessibilityNodeInfo root=currentSpotifyRoot();
        return root!=null && spotifyPlaylistHeaderVisible(root,null,name) &&
            !spotifyLargePauseVisible(root);
    }

    private void tapCalibratedForApp(String packageName,int x,int y,
                                     String verifiedPlaylistName,Completion cb) {
        boolean bolt="ee.mtakso.client".equals(packageName);
        boolean spotify="com.spotify.music".equals(packageName);
        boolean playlistPlay=spotify && x==977 && y==1291;
        boolean allowed=bolt ? ((x==333 && y==645) || (x==450 && y==520)) :
            (spotify && ((x==403 && y==2211) || (x==490 && y==550) ||
                (playlistPlay && verifiedPlaylistName!=null)));
        if(!allowed || !sessionActive || !trustedExternalApp(packageName) ||
           (playlistPlay && !verifiedNamedPlaylistReady(verifiedPlaylistName))) {
            cb.finished(false,"Програма або сторінка потрібного плейлиста не підтверджена.");
            return;
        }
        android.graphics.Point size=new android.graphics.Point();
        android.view.Display display=getDisplay();
        if(display==null) {
            WindowManager wm=(WindowManager)getSystemService(Context.WINDOW_SERVICE);
            if(wm!=null)display=wm.getDefaultDisplay();
        }
        if(display==null) {
            cb.finished(false,"Не вдалося визначити екран.");return;
        }
        display.getRealSize(size);
        if(size.x<450 || size.y<size.x*1.6f ||
           x<0 || y<0 || x>=size.x || y>=size.y) {
            cb.finished(false,"Невідповідна орієнтація або координати екрана.");
            return;
        }
        FloatingJarvisService.avoidControl(new Rect(x-24,y-24,x+24,y+24));
        final Runnable restore=FloatingJarvisService.hideForGesture();
        final java.util.concurrent.atomic.AtomicBoolean once=
            new java.util.concurrent.atomic.AtomicBoolean(false);
        final Completion finishTap=(ok,detail)->{
            if(!once.compareAndSet(false,true))return;
            try {restore.run();}catch(RuntimeException ignored){}
            cb.finished(ok,detail);
        };
        main.postDelayed(()->{
            if(!sessionActive || !trustedExternalApp(packageName) ||
               (playlistPlay && !verifiedNamedPlaylistReady(verifiedPlaylistName))) {
                finishTap.finished(false,
                    "Програма або сторінка плейлиста змінилася до натискання.");
                return;
            }
            Path path=new Path();
            path.moveTo(x,y);
            GestureDescription gesture=new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path,0,90))
                .build();
            try {
                boolean accepted=dispatchGesture(gesture,new GestureResultCallback(){
                    @Override public void onCompleted(GestureDescription g) {
                        finishTap.finished(true,"Точку натиснуто.");
                    }
                    @Override public void onCancelled(GestureDescription g) {
                        finishTap.finished(false,"Android скасував натискання.");
                    }
                },main);
                if(!accepted)finishTap.finished(false,"Android відхилив натискання.");
                else main.postDelayed(()->finishTap.finished(false,
                    "Час очікування натискання вичерпано."),5000L);
            }catch(RuntimeException error){
                finishTap.finished(false,"Не вдалося натиснути координати.");
            }
        },80L);
    }

    /**
     * Coordinates open Spotify's keyboard, then Android's InputMethod bridge
     * inserts the spoken title in the focused Spotify editor. Never touches
     * passwords or editors belonging to another package.
     */
    public static SpotifyStep spotifyTypeViaInputMethod(String title) {
        ScreenControlAccessibilityService s=connected.get();
        if(s==null || !s.sessionActive || !JarvisPhoneScreenGuard.available(s) ||
            title==null || title.trim().isEmpty() || title.length()>100)
            return SpotifyStep.BLOCKED;
        if(android.os.Build.VERSION.SDK_INT<33)return SpotifyStep.BLOCKED;
        try {
            android.accessibilityservice.InputMethod input=s.getInputMethod();
            if(input==null || !input.getCurrentInputStarted())return SpotifyStep.WAIT;
            android.view.inputmethod.EditorInfo editor=input.getCurrentInputEditorInfo();
            android.accessibilityservice.InputMethod.AccessibilityInputConnection conn=
                input.getCurrentInputConnection();
            if(editor==null || conn==null)return SpotifyStep.WAIT;
            if(!"com.spotify.music".equals(editor.packageName))
                return SpotifyStep.BLOCKED;
            int type=editor.inputType;
            int variation=type & InputType.TYPE_MASK_VARIATION;
            if((type & InputType.TYPE_MASK_CLASS)!=InputType.TYPE_CLASS_TEXT ||
               variation==InputType.TYPE_TEXT_VARIATION_PASSWORD ||
               variation==InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
               variation==InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
                return SpotifyStep.BLOCKED;
            // Replace the previous query rather than append to it.
            // No repeated commit if the UI takes time to update.
            conn.performContextMenuAction(android.R.id.selectAll);
            conn.commitText(title,1,null);
            return SpotifyStep.CLICKED;
        } catch(RuntimeException rejected) {
            return SpotifyStep.BLOCKED;
        }
    }

    /**
     * Direct keyboard connection works on Android 13+ without inspecting
     * Bolt's accessibility nodes. Never types if a different app owns
     * the editor, the field is password-like, or Bolt wasn't tapped first.
     */
    public static BoltStep boltTypeViaInputMethod(String address) {
        ScreenControlAccessibilityService s=connected.get();
        if(s==null || !s.sessionActive ||
            address==null || address.trim().isEmpty() || address.length()>250)
            return BoltStep.BLOCKED;
        if(android.os.Build.VERSION.SDK_INT<33) return BoltStep.BLOCKED;
        try {
            android.accessibilityservice.InputMethod input=s.getInputMethod();
            if(input==null || !input.getCurrentInputStarted())return BoltStep.WAIT;
            android.view.inputmethod.EditorInfo editor=input.getCurrentInputEditorInfo();
            android.accessibilityservice.InputMethod.AccessibilityInputConnection conn=
                input.getCurrentInputConnection();
            if(editor==null || conn==null)return BoltStep.WAIT;
            if(!"ee.mtakso.client".equals(editor.packageName))
                return BoltStep.BLOCKED;
            int type=editor.inputType;
            int variation=type & InputType.TYPE_MASK_VARIATION;
            if((type & InputType.TYPE_MASK_CLASS)!=InputType.TYPE_CLASS_TEXT ||
               variation==InputType.TYPE_TEXT_VARIATION_PASSWORD ||
               variation==InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
               variation==InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
                return BoltStep.BLOCKED;
            conn.commitText(address,1,null);
            // commitText does not acknowledge that Bolt displayed the string.
            // Do not repeat it or press any booking/confirmation button.
            return BoltStep.ENTERED;
        }catch(RuntimeException denied){
            return BoltStep.BLOCKED;
        }
    }

    /** Wait for visible «Рушаймо» rather than tapping a four-second guess. */
    public static BoltStep boltLetsGo() {
        ScreenControlAccessibilityService service=connected.get();
        return service==null || !service.sessionActive ? BoltStep.BLOCKED :
            service.tapBoltLetsGo();
    }

    public static BoltStep boltRides() {
        ScreenControlAccessibilityService service = connected.get();
        return service == null || !service.sessionActive ? BoltStep.BLOCKED :
            service.tapBoltRides();
    }

    /** User-requested fixed 4s click. Does not wait for the «Поїздки»
     * accessibility label: if it is absent, uses a calibrated home-tile point.
     * Still verifies the foreground Bolt package and blocks unsafe screens. */
    public static BoltStep boltRidesAtFourSeconds() {
        ScreenControlAccessibilityService service = connected.get();
        return service == null || !service.sessionActive ? BoltStep.BLOCKED :
            service.tapBoltRidesTimed();
    }

    /** The app appearing in foreground is NOT evidence that its rides card
     * has loaded. Only expose readiness after locating a visible, enabled tile
     * (or verifying that the destination editor is already focused). */
    public static boolean boltRidesAvailable() {
        ScreenControlAccessibilityService service = connected.get();
        if (service == null || !service.sessionActive) return false;
        AccessibilityNodeInfo root = service.currentBoltRoot();
        return root != null && (service.isBoltDestinationFocused() ||
            service.findBoltDropoffTarget(root) != null ||
            service.findBoltRidesTarget(root) != null);
    }

    /** The route screen has a destination placeholder that must be tapped
     * before its search editor receives input. */
    public static BoltStep boltFocusDestination() {
        ScreenControlAccessibilityService service = connected.get();
        return service == null || !service.sessionActive ? BoltStep.BLOCKED :
            service.focusBoltDropoff();
    }

    public static boolean boltDestinationFocused() {
        ScreenControlAccessibilityService service = connected.get();
        return service != null && service.sessionActive &&
            service.isBoltDestinationFocused();
    }

    public static BoltStep boltTypeDestination(String address) {
        ScreenControlAccessibilityService service = connected.get();
        return service == null || !service.sessionActive ? BoltStep.BLOCKED :
            service.fillBoltDestination(address);
    }

    /** Activate keyboard Search/Enter only after confirming the exact text
     * in Bolt's focused destination editor. Never confirms a ride. */
    public static BoltStep boltPressDestinationEnter(String address) {
        ScreenControlAccessibilityService service = connected.get();
        return service == null || !service.sessionActive ? BoltStep.BLOCKED :
            service.submitBoltDestination(address);
    }

    private AccessibilityNodeInfo currentBoltRoot() {
        AccessibilityNodeInfo root = currentExternalRoot();
        return root != null && root.isVisibleToUser() && root.getPackageName() != null &&
            "ee.mtakso.client".contentEquals(root.getPackageName()) ? root : null;
    }

    private static String boltNodeLabel(AccessibilityNodeInfo node) {
        return asString(node.getText()) + " " +
            asString(node.getContentDescription()) + " " +
            asString(node.getHintText()) + " " +
            asString(node.getViewIdResourceName());
    }

    /** A recognized route/drop-off screen means Bolt has opened «Поїздки»,
     * even when the destination editor is NOT focused yet. */
    public static boolean boltRidesReady() {
        ScreenControlAccessibilityService service = connected.get();
        if (service == null || !service.sessionActive) return false;
        AccessibilityNodeInfo root = service.currentBoltRoot();
        return root != null && (service.isBoltDestinationFocused() ||
            service.findBoltDropoffTarget(root) != null);
    }

    private boolean isBoltDestinationFocused() {
        AccessibilityNodeInfo root = currentBoltRoot();
        if (root == null) return false;
        AccessibilityNodeInfo focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focus == null || !focus.isEditable() || !focus.isFocused() ||
            !focus.isVisibleToUser() || !focus.isEnabled() || focus.isPassword() ||
            focus.getPackageName() == null ||
            !"ee.mtakso.client".contentEquals(focus.getPackageName()))
            return false;
        return !BoltUiLabels.pickup(boltNodeLabel(focus)) &&
            !isSensitiveInputType(focus.getInputType()) &&
            !isSensitiveFieldLabel(focus);
    }

    /** Find the actual second row («Місце висадки») of Bolt's «Маршрут».
     * The field can be a non-editable placeholder; clicking it opens an
     * editable destination search. Never click the first/pickup row. */
    private Rect findBoltDropoffTarget(AccessibilityNodeInfo root) {
        if (root == null || !root.isVisibleToUser()) return null;
        android.graphics.Point displaySize = new android.graphics.Point();
        android.view.Display display = getDisplay();
        if (display == null) {
            WindowManager window = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            if (window != null) display = window.getDefaultDisplay();
        }
        if (display == null) return null;
        display.getRealSize(displaySize);
        int width=displaySize.x,height=displaySize.y;
        if (height<width*1.65f) return null;

        ArrayDeque<AccessibilityNodeInfo> scan = new ArrayDeque<>();
        scan.add(root);
        Rect destination=null;
        boolean routeTitle=false;
        boolean pickupLine=false;
        int found=0;
        while (!scan.isEmpty() && ++found<=MAX_SCAN) {
            AccessibilityNodeInfo n=scan.removeFirst();
            if (!n.isVisibleToUser() || !n.isEnabled() || n.isPassword()) continue;
            String label=boltNodeLabel(n);
            String text=asString(n.getText());
            String description=asString(n.getContentDescription());
            Rect bounds=new Rect();
            n.getBoundsInScreen(bounds);
            if (BoltUiLabels.routeTitle(text) || BoltUiLabels.routeTitle(description))
                routeTitle=true;
            if (!text.trim().isEmpty() && text.trim().length()>=5 &&
                !BoltUiLabels.routeTitle(text) &&
                !BoltUiLabels.destination(text) &&
                bounds.centerX()>width*.13f && bounds.centerX()<width*.87f &&
                bounds.centerY()>height*.125f && bounds.centerY()<height*.197f)
                pickupLine=true;

            if (BoltUiLabels.destination(label) && !BoltUiLabels.pickup(label) &&
                bounds.width()>18 && bounds.height()>10 &&
                bounds.height()<height*.12f &&
                BoltUiLabels.dropoffRegion(bounds.centerX(),bounds.centerY(),
                    width,height)) {
                // A text label itself is the most reliable tap target.
                // Multiple different targets are ambiguous and are rejected.
                if (destination!=null && !destination.equals(bounds)) {
                    // Nested accessibility labels can overlap. Prefer their
                    // smaller, more precise visible text bounds.
                    if (bounds.contains(destination)) {
                        // Preserve the smaller prior match.
                    } else if (destination.contains(bounds)) {
                        destination=bounds;
                    } else return null;
                } else destination=bounds;
            }
            for(int i=0;i<n.getChildCount();i++) {
                AccessibilityNodeInfo child=n.getChild(i);
                if(child!=null)scan.addLast(child);
            }
        }
        if(found>MAX_SCAN || !routeTitle)return null;
        if(destination!=null)return destination;

        // Some Bolt versions draw the placeholder without an accessible
        // node. Use the user's screenshot coordinate ONLY with route header,
        // visible pickup line, matching portrait geometry and no other page.
        if(!pickupLine)return null;
        int[] point=BoltUiLabels.screenshotDropoffPoint(width,height);
        return point==null ? null : new Rect(point[0]-12,point[1]-12,
            point[0]+12,point[1]+12);
    }

    /** A full-page «Маршрут» is not an editable field. Tap its second row,
     * then WAIT for Bolt to focus the actual destination input. */
    private BoltStep focusBoltDropoff() {
        AccessibilityNodeInfo root=currentBoltRoot();
        if(root==null)return BoltStep.WAIT;
        if(isBoltDestinationFocused())return BoltStep.READY;
        Rect target=findBoltDropoffTarget(root);
        if(target==null)return BoltStep.WAIT;
        return gestureTapBolt(root,target.exactCenterX(),target.exactCenterY(),
            "Місце висадки");
    }

    /** Select the rides tile only after Android exposes the actual home
     * screen; return null for Bolt splash screens, blank frames or dialogs. */
    private Rect findBoltRidesTarget(AccessibilityNodeInfo root) {
        if (root == null || !root.isVisibleToUser()) return null;
        Rect screen = new Rect();
        root.getBoundsInScreen(screen);
        if (screen.width() < 240 || screen.height() < 450) return null;

        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        Rect best = null;
        int bestScore = -1;
        boolean plan = false, food = false, send = false, homeSearch = false;
        boolean homeHeading = false, routePage = false;
        int scanned = 0;
        while (!queue.isEmpty() && ++scanned <= MAX_SCAN) {
            AccessibilityNodeInfo item = queue.removeFirst();
            if (!item.isVisibleToUser() || !item.isEnabled() || item.isPassword()) continue;
            String text = asString(item.getText());
            String description = asString(item.getContentDescription());
            String joined = (text + " " + description).toLowerCase(Locale.ROOT);
            if (BoltUiLabels.ridesHomeHeading(text) ||
                BoltUiLabels.ridesHomeHeading(description)) homeHeading = true;
            if (BoltUiLabels.routeTitle(text) ||
                BoltUiLabels.routeTitle(description)) routePage = true;
            if (joined.contains("запланувати") || joined.contains("schedule"))
                plan = true;
            if (joined.contains("bolt food")) food = true;
            if (joined.contains("bolt send")) send = true;
            if (BoltUiLabels.destination(text) || BoltUiLabels.destination(description))
                homeSearch = true;

            boolean title = BoltUiLabels.rides(text);
            boolean desc = BoltUiLabels.rides(description);
            if (title || desc) {
                Rect bounds = new Rect();
                item.getBoundsInScreen(bounds);
                float cx = bounds.exactCenterX(), cy = bounds.exactCenterY();
                // Target the first (top-left) passenger-rides card, not history,
                // food, send, or an entire full-screen container.
                boolean topLeft = cx > screen.left + screen.width() * .04f &&
                    cx < screen.left + screen.width() * .61f &&
                    cy > screen.top + screen.height() * .12f &&
                    cy < screen.top + screen.height() * .64f;
                if (topLeft && bounds.width() > 16 && bounds.height() > 16 &&
                    bounds.width() < screen.width() * .72f &&
                    bounds.height() < screen.height() * .43f) {
                    int score = title ? 3 : 2;
                    if (item.isClickable()) score++;
                    if (score > bestScore) {
                        bestScore = score;
                        best = bounds;
                    }
                }
            }
            for (int i=0; i<item.getChildCount(); i++) {
                AccessibilityNodeInfo child = item.getChild(i);
                if (child != null) queue.addLast(child);
            }
        }
        if (scanned > MAX_SCAN) return null;
        if (best != null) return best;

        // Pixel/Bolt can draw a four-tile home grid as inaccessible canvas.
        // Accept 3 independent home markers, or the exact home heading plus 2.
        // Never use the screenshot fallback on a route/checkout screen.
        if (!routePage && screen.height() > screen.width() * 1.45f &&
            BoltUiLabels.ridesHomeFallback(homeHeading,plan,food,send,homeSearch)) {
            android.graphics.Point physical = new android.graphics.Point();
            android.view.Display display = getDisplay();
            if (display == null) {
                WindowManager wm = (WindowManager)getSystemService(Context.WINDOW_SERVICE);
                if (wm != null)display = wm.getDefaultDisplay();
            }
            if (display != null) {
                display.getRealSize(physical);
                int[] point = BoltUiLabels.screenshotRidesPoint(physical.x,physical.y);
                if (point != null && screen.contains(point[0],point[1]))
                    return new Rect(point[0]-8,point[1]-8,
                                    point[0]+8,point[1]+8);
            }
        }
        return null;
    }

    /** Only click a visible «Рушаймо» label within Bolt's HOME rides tile.
     * The text might be a child of the combined «Поїздки · Рушаймо» card.
     * Neither a timeout nor opening Bolt alone authorizes any coordinates. */
    private BoltStep tapBoltLetsGo() {
        AccessibilityNodeInfo root=currentBoltRoot();
        if (root==null) return BoltStep.WAIT;
        if (isBoltDestinationFocused() || findBoltDropoffTarget(root)!=null)
            return BoltStep.READY;
        Rect frame=new Rect();
        root.getBoundsInScreen(frame);
        ArrayDeque<AccessibilityNodeInfo> nodes=new ArrayDeque<>();
        nodes.add(root);
        Rect chosen=null;
        int bestScore=-1,scanned=0;
        boolean heading=false,hasRides=false,food=false,plan=false,send=false;
        boolean unsafe=false,routeScreen=false;
        while(!nodes.isEmpty() && ++scanned<=MAX_SCAN){
            AccessibilityNodeInfo n=nodes.removeFirst();
            if(!n.isVisibleToUser() || n.isPassword()) continue;
            String text=asString(n.getText());
            String description=asString(n.getContentDescription());
            String combined=(text+" "+description).toLowerCase(Locale.ROOT);
            if(BoltUiLabels.ridesHomeHeading(text) ||
               BoltUiLabels.ridesHomeHeading(description)) heading=true;
            if(BoltUiLabels.rides(text) || BoltUiLabels.rides(description))
                hasRides=true;
            if(combined.contains("bolt food"))food=true;
            if(combined.contains("запланувати") || combined.contains("schedule"))plan=true;
            if(combined.contains("bolt send"))send=true;
            if(BoltUiLabels.routeTitle(text) || BoltUiLabels.routeTitle(description))
                routeScreen=true;
            if(BoltUiLabels.unsafeForTimedRidesTap(text) ||
               BoltUiLabels.unsafeForTimedRidesTap(description)) unsafe=true;
            if(n.isEnabled() && (BoltUiLabels.letsGo(text) ||
               BoltUiLabels.letsGo(description))) {
                Rect box=new Rect();n.getBoundsInScreen(box);
                if(!box.isEmpty() && BoltUiLabels.letsGoRegion(box.centerX(),
                    box.centerY(),frame.left,frame.top,frame.right,frame.bottom)) {
                    int score=(BoltUiLabels.rides(text) ||
                        BoltUiLabels.rides(description))?3:2;
                    if(n.isClickable())score++;
                    if(score>bestScore) {
                        chosen=box;
                        bestScore=score;
                    } else if(score==bestScore && chosen!=null &&
                              !chosen.contains(box) && !box.contains(chosen))
                        return BoltStep.WAIT;
                }
            }
            for(int i=0;i<n.getChildCount();i++){
                AccessibilityNodeInfo child=n.getChild(i);
                if(child!=null)nodes.addLast(child);
            }
        }
        if(scanned>MAX_SCAN || unsafe || routeScreen) return BoltStep.BLOCKED;
        // Require the explicit label AND independent evidence of Bolt home.
        if(chosen==null || !(heading || (hasRides && (food || plan || send))))
            return BoltStep.WAIT;
        return gestureTapBolt(root,chosen.exactCenterX(),chosen.exactCenterY(),
            "Рушаймо");
    }

    /** Physical tap in the verified rides tile; don't tap while it's loading. */
    private BoltStep tapBoltRides() {
        AccessibilityNodeInfo root = currentBoltRoot();
        if (root == null) return BoltStep.WAIT;
        if (isBoltDestinationFocused() || findBoltDropoffTarget(root)!=null)
            return BoltStep.READY;
        Rect target = findBoltRidesTarget(root);
        return target == null ? BoltStep.WAIT :
            gestureTapBolt(root,target.exactCenterX(),target.exactCenterY(),
                "Поїздки");
    }

    /** Called only after the controller's launch delay. The exact label is
     * optional; Bolt must still occupy the foreground, and a payment/route
     * screen or a dialog must never receive the screenshot-calibrated tap. */
    private BoltStep tapBoltRidesTimed() {
        AccessibilityNodeInfo root=currentBoltRoot();
        if(root==null)return BoltStep.WAIT;
        if(isBoltDestinationFocused() || findBoltDropoffTarget(root)!=null)
            return BoltStep.READY;

        android.graphics.Point physical=new android.graphics.Point();
        android.view.Display display=getDisplay();
        if(display==null){
            WindowManager wm=(WindowManager)getSystemService(Context.WINDOW_SERVICE);
            if(wm!=null)display=wm.getDefaultDisplay();
        }
        if(display==null)return BoltStep.BLOCKED;
        display.getRealSize(physical);
        int[] point=BoltUiLabels.screenshotRidesPoint(physical.x,physical.y);
        if(point==null)return BoltStep.BLOCKED;

        Rect rootBounds=new Rect();
        root.getBoundsInScreen(rootBounds);
        if(rootBounds.width()<physical.x*.90f ||
           rootBounds.height()<physical.y*.75f ||
           !rootBounds.contains(point[0],point[1]))
            return BoltStep.BLOCKED;

        // No blind tap on a route, payment, confirmation or unexpected dialog.
        ArrayDeque<AccessibilityNodeInfo> scan=new ArrayDeque<>();
        scan.add(root);
        int scanned=0;
        boolean heading=false, plan=false, food=false, send=false, search=false;
        while(!scan.isEmpty() && ++scanned<=MAX_SCAN) {
            AccessibilityNodeInfo item=scan.removeFirst();
            if(!item.isVisibleToUser())continue;
            String title=asString(item.getText());
            String description=asString(item.getContentDescription());
            if(BoltUiLabels.unsafeForTimedRidesTap(title) ||
               BoltUiLabels.unsafeForTimedRidesTap(description))
                return BoltStep.BLOCKED;
            if(BoltUiLabels.ridesHomeHeading(title) ||
               BoltUiLabels.ridesHomeHeading(description))heading=true;
            String combined=(title+" "+description).toLowerCase(Locale.ROOT);
            if(combined.contains("запланувати") || combined.contains("schedule"))
                plan=true;
            if(combined.contains("bolt food"))food=true;
            if(combined.contains("bolt send"))send=true;
            if(BoltUiLabels.destination(title) ||
               BoltUiLabels.destination(description))search=true;
            for(int i=0;i<item.getChildCount();i++){
                AccessibilityNodeInfo child=item.getChild(i);
                if(child!=null)scan.addLast(child);
            }
        }
        if(scanned>MAX_SCAN)return BoltStep.BLOCKED;

        // At the four-second mark we don't WAIT for the rides label, but a
        // pixel tap is still allowed only on Bolt's recognized home screen.
        // A loading screen, dialog or an unexpected page must never be tapped.
        if(!BoltUiLabels.ridesHomeFallback(heading,plan,food,send,search))
            return BoltStep.WAIT;

        // Prefer an accessible tile when available; otherwise use the
        // screenshot-verified top-left tile center, not the Bolt Food row.
        Rect tile=findBoltRidesTarget(root);
        float x=tile==null?point[0]:tile.exactCenterX();
        float y=tile==null?point[1]:tile.exactCenterY();
        return gestureTapBolt(root,x,y,"Поїздки");
    }

    private BoltStep gestureTapBolt(AccessibilityNodeInfo root,float x,float y,
                                     String targetName) {
        if (!sessionActive || currentBoltRoot() == null) return BoltStep.BLOCKED;
        Rect frame = new Rect();
        root.getBoundsInScreen(frame);
        if (!frame.contains(Math.round(x),Math.round(y))) return BoltStep.BLOCKED;
        Path path = new Path();
        path.moveTo(x,y);
        GestureDescription tap = new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(path,0,90))
            .build();
        // The gesture helper makes the floating face untouchable for the tap,
        // and verifies that Bolt remains the foreground package.
        return dispatchTrackedGesture(tap, (ok, detail) -> {
            if (!ok && sessionActive)
                toast("Не вдалося натиснути «" + targetName + "»: " + detail);
        }) ? BoltStep.CLICKED : BoltStep.BLOCKED;
    }

    /** Type ONLY after the destination editor has been deliberately focused.
     * Never type into the route screen, pickup field or a different app. */
    private BoltStep fillBoltDestination(String address) {
        if (address == null || address.trim().isEmpty() || address.length() > 250)
            return BoltStep.BLOCKED;
        AccessibilityNodeInfo root = currentBoltRoot();
        if (root == null) return BoltStep.WAIT;

        // Only the input currently focused by Bolt may receive the destination.
        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused == null || !focused.isEditable() || !focused.isFocused() ||
            !focused.isVisibleToUser() || !focused.isEnabled())
            return BoltStep.WAIT;
        String label = boltNodeLabel(focused);
        if (BoltUiLabels.pickup(label) || focused.isPassword() ||
            isSensitiveInputType(focused.getInputType()) || isSensitiveFieldLabel(focused))
            return BoltStep.BLOCKED;
        if (focused.getPackageName() == null ||
            !"ee.mtakso.client".contentEquals(focused.getPackageName()))
            return BoltStep.BLOCKED;

        // An unlabeled field is safe only when it is the sole eligible field.
        int eligible = 0;
        ArrayDeque<AccessibilityNodeInfo> nodes = new ArrayDeque<>();
        nodes.add(root);
        int scanned = 0;
        while (!nodes.isEmpty() && ++scanned <= MAX_SCAN) {
            AccessibilityNodeInfo node = nodes.removeFirst();
            if (!node.isVisibleToUser() || !node.isEnabled() || node.isPassword())
                continue;
            if (node.isEditable() && !BoltUiLabels.pickup(boltNodeLabel(node)) &&
                !isSensitiveInputType(node.getInputType()) && !isSensitiveFieldLabel(node))
                eligible++;
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) nodes.addLast(child);
            }
        }
        if (scanned > MAX_SCAN) return BoltStep.WAIT;
        if (!BoltUiLabels.destination(label) && eligible != 1) return BoltStep.WAIT;

        boolean supportsSetText = false;
        for (AccessibilityNodeInfo.AccessibilityAction action : focused.getActionList()) {
            if (action.getId() == AccessibilityNodeInfo.ACTION_SET_TEXT) {
                supportsSetText = true;
                break;
            }
        }
        if (!supportsSetText) return BoltStep.BLOCKED;
        // Revalidate focus and package immediately before writing.
        AccessibilityNodeInfo current = currentBoltRoot();
        if (!sessionActive || current == null || !focused.isFocused() ||
            !focused.isEnabled() || !focused.isVisibleToUser()) return BoltStep.WAIT;
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, address);
        try {
            return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                ? BoltStep.ENTERED : BoltStep.WAIT;
        } catch (SecurityException blocked) {
            return BoltStep.BLOCKED;
        }
    }

    /** Android's ACTION_IME_ENTER invokes the current keyboard action
     * (Search/Enter) for a focused editable field. It is NOT the Bolt
     * confirmation button or any other button on the page.
     *
     * No screen coordinates or root shell key injection: those could hit a
     * suggestion, payment button or another app if the screen has changed.
     */
    private BoltStep submitBoltDestination(String address) {
        if (address == null || address.trim().isEmpty() || address.length()>250)
            return BoltStep.BLOCKED;
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R)
            return BoltStep.BLOCKED; // ACTION_IME_ENTER requires Android 11.
        AccessibilityNodeInfo root=currentBoltRoot();
        if (root==null) return BoltStep.WAIT;
        AccessibilityNodeInfo focused=root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused==null || !focused.isEditable() || !focused.isFocused() ||
            !focused.isVisibleToUser() || !focused.isEnabled())
            return BoltStep.WAIT;
        String label=boltNodeLabel(focused);
        if (focused.isPassword() || BoltUiLabels.pickup(label) ||
            isSensitiveInputType(focused.getInputType()) ||
            isSensitiveFieldLabel(focused) || focused.getPackageName()==null ||
            !"ee.mtakso.client".contentEquals(focused.getPackageName()))
            return BoltStep.BLOCKED;

        // An unlabeled editor must be the only safe editable field; a
        // destination-labelled field can coexist with Bolt's pickup editor.
        if (!BoltUiLabels.destination(label)) {
            int eligible=0,scanned=0;
            ArrayDeque<AccessibilityNodeInfo> q=new ArrayDeque<>();
            q.add(root);
            while (!q.isEmpty() && ++scanned<=MAX_SCAN) {
                AccessibilityNodeInfo item=q.removeFirst();
                if (!item.isVisibleToUser() || item.isPassword())continue;
                if (item.isEditable() && item.isEnabled() &&
                    !BoltUiLabels.pickup(boltNodeLabel(item)) &&
                    !isSensitiveFieldLabel(item) &&
                    !isSensitiveInputType(item.getInputType()))
                    eligible++;
                for(int i=0;i<item.getChildCount();i++) {
                    AccessibilityNodeInfo child=item.getChild(i);
                    if(child!=null)q.addLast(child);
                }
            }
            if (scanned>MAX_SCAN || eligible!=1) return BoltStep.WAIT;
        }
        if (!BoltUiLabels.typedDestinationMatches(address,focused.getText()))
            return BoltStep.WAIT;

        int enterId=AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId();
        boolean supported=false;
        for (AccessibilityNodeInfo.AccessibilityAction action:focused.getActionList()) {
            if (action.getId()==enterId) { supported=true; break; }
        }
        if (!supported) return BoltStep.BLOCKED;
        AccessibilityNodeInfo current=currentBoltRoot();
        if (current==null || !sessionActive) return BoltStep.WAIT;
        AccessibilityNodeInfo stillFocused=current.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (stillFocused==null || !stillFocused.isFocused() ||
            !stillFocused.isEditable() || stillFocused.getPackageName()==null ||
            !"ee.mtakso.client".contentEquals(stillFocused.getPackageName()) ||
            !BoltUiLabels.typedDestinationMatches(address,stillFocused.getText()))
            return BoltStep.WAIT;
        try {
            return stillFocused.performAction(enterId)
                ? BoltStep.SUBMITTED : BoltStep.WAIT;
        } catch (SecurityException | IllegalStateException error) {
            return BoltStep.BLOCKED;
        }
    }

    public interface Completion { void finished(boolean success, String detail); }

    /**
     * Only the explicit "Шазам" voice command may use this target. Never tap
     * screen coordinates until Android confirms the foreground app is Shazam.
     */
    public static boolean shazamVisible() {
        ScreenControlAccessibilityService service = connected.get();
        return service != null && service.sessionActive &&
            service.currentShazamRoot() != null;
    }

    public static void tapShazamCenter(Completion callback) {
        ScreenControlAccessibilityService service = connected.get();
        if (service == null || !service.sessionActive) {
            callback.finished(false, "Керування екраном недоступне.");
            return;
        }
        AccessibilityNodeInfo root = service.currentShazamRoot();
        if (root == null) {
            callback.finished(false, "Shazam не на передньому плані.");
            return;
        }
        Rect bounds = new Rect();
        root.getBoundsInScreen(bounds);
        if (bounds.width() < 200 || bounds.height() < 200) {
            callback.finished(false, "Екран Shazam ще не готовий.");
            return;
        }
        Path tap = new Path();
        tap.moveTo(bounds.exactCenterX(), bounds.exactCenterY());
        GestureDescription gesture = new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(tap, 0, 95))
            .build();
        service.dispatchTrackedGesture(gesture, callback);
    }

    private Completion gestureCompletion;

    public static void perform(ScreenControlCommands.Action action, Completion callback) {
        ScreenControlAccessibilityService service = connected.get();
        if (service == null) {
            callback.finished(false,"Служба керування екраном недоступна.");
            return;
        }
        if (!JarvisPhoneScreenGuard.available(service)) {
            callback.finished(false,JarvisPhoneScreenGuard.MESSAGE);
            return;
        }
        service.gestureCompletion = callback;
        try {
            String result = perform(action);
            if (service.gestureCompletion == callback) {
                service.gestureCompletion = null;
                callback.finished(JarvisControlFeedback.successfulReply(result),result);
            }
        } catch (RuntimeException error) {
            service.gestureCompletion = null;
            callback.finished(false,"Android не дозволив виконати команду.");
        }
    }

    private boolean dispatchTrackedGesture(GestureDescription gesture) {
        Completion callback = gestureCompletion;
        gestureCompletion = null;
        return dispatchTrackedGesture(gesture, callback);
    }

    /** Asynchronous callbacks may run after Bolt has replaced its view tree.
     * Treat stale Accessibility nodes as a failed gesture, never as a process
     * crash. This also protects the shared Spotify/Shazam gesture path. */
    private boolean dispatchTrackedGesture(GestureDescription gesture, Completion callback) {
        long feedback = JarvisControlFeedback.begin();
        final String expectedPackage;
        final Runnable restore;
        try {
            AccessibilityNodeInfo originalRoot=currentExternalRoot();
            expectedPackage=originalRoot==null ? "" : asString(originalRoot.getPackageName());
            restore=FloatingJarvisService.hideForGesture();
        } catch (RuntimeException error) {
            android.util.Log.e("JARVIS_SCREEN",
                "Accessibility gesture setup failed",error);
            JarvisControlFeedback.finish(feedback,false,
                android.os.SystemClock.uptimeMillis());
            if(callback!=null)callback.finished(false,
                "Екран змінився. Не вдалося підготувати натискання.");
            return false;
        }
        main.postDelayed(() -> {
            final java.util.concurrent.atomic.AtomicBoolean finished =
                new java.util.concurrent.atomic.AtomicBoolean();
            Completion done = (ok,detail) -> {
                if (!finished.compareAndSet(false,true)) return;
                try { restore.run(); }
                catch (RuntimeException error) {
                    android.util.Log.e("JARVIS_SCREEN",
                        "Could not restore Jarvis overlay after gesture",error);
                }
                JarvisControlFeedback.finish(feedback,ok,
                    android.os.SystemClock.uptimeMillis());
                if (callback != null) callback.finished(ok,detail);
            };
            try {
                AccessibilityNodeInfo current=currentExternalRoot();
                if (!sessionActive || connected.get() != this || current==null ||
                    !expectedPackage.equals(asString(current.getPackageName()))) {
                    done.finished(false,"Керування зупинено до початку жесту.");
                    return;
                }
                boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
                    @Override public void onCompleted(GestureDescription description) {
                        done.finished(true,"Жест виконано.");
                    }
                    @Override public void onCancelled(GestureDescription description) {
                        done.finished(false,"Android скасував жест.");
                    }
                },main);
                if (!accepted) done.finished(false,"Android відхилив жест.");
                else main.postDelayed(() ->
                    done.finished(false,"Час очікування жесту вичерпано."),5000L);
            } catch (RuntimeException error) {
                android.util.Log.e("JARVIS_SCREEN",
                    "Accessibility tree changed during gesture",error);
                done.finished(false,"Екран змінився під час натискання.");
            }
        },80L); // Let WindowManager remove the orb from hit testing first.
        return true; // Queued; confirmation is in the asynchronous callback.
    }

    public static String perform(ScreenControlCommands.Action action) {
        ScreenControlAccessibilityService s = connected.get();
        if (s == null)
            return "Служба керування екраном не активована в налаштуваннях Android.";
        if (action == null)
            return "Невідома команда керування.";
        if (!JarvisPhoneScreenGuard.available(s)) return JarvisPhoneScreenGuard.MESSAGE;
        if (!s.sessionActive)
            return "Спочатку скажи: увімкни керування екраном.";
        // Phone mode and screen control share the same session; gestures need no second switch.
        switch (action.kind) {
            case CLICK: return s.clickNamed(action.label);
            case LONG_PRESS:
                return s.longPressNamed(action.label);
            case SWIPE_UP: case SWIPE_DOWN: case SWIPE_LEFT: case SWIPE_RIGHT:
                return s.swipeCenterToEdge(action.kind);
            case TYPE_TEXT: return s.writeToFocusedField(action.label);
            case SCROLL_DOWN: return s.scroll(true);
            case SCROLL_UP: return s.scroll(false);
            case BACK:
                return s.performGlobalAction(GLOBAL_ACTION_BACK) ?
                    "Повертаюся назад." : "Система не дозволила повернутися назад.";
            case STATUS: return "Керування екраном активне.";
            default: return "Непідтримувана дія.";
        }
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        connected = new WeakReference<>(this);
        // No automatic activation after install, process restart or service restart.
        sessionActive = false;
        JarvisPhoneMode.setActive(false);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        // Observe only the foreground package. Never read screen contents,
        // start a gesture or transmit any accessibility data on an event.
        if(event!=null &&
           event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
           event.getPackageName()!=null) {
            String next=event.getPackageName().toString();
            // The Jarvis floating face is not a different foreground app.
            // Keep the last actual window identity when it redraws.
            if(!next.equals(getPackageName())) {
                foregroundWindowPackage=next;
                foregroundWindowAt=android.os.SystemClock.elapsedRealtime();
            }
        }
    }

    @Override public void onInterrupt() {
        stopListening();
    }

    private void stopSession() {
        sessionActive = false;
        JarvisPhoneMode.setActive(false);
        if (sequences != null) sequences.cancel();
        if (spotifyPlayback != null) spotifyPlayback.cancel();
        stopListening();
        if (buttonOverlay != null && windows != null) {
            try { windows.removeView(buttonOverlay); } catch (Exception ignored) {}
            buttonOverlay = null;
        }
    }

    @Override public void onDestroy() {
        if(macroPointPicker!=null){ macroPointPicker.close(); macroPointPicker=null; }
        stopSession();
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
        if (connected.get() == this) connected = new WeakReference<>(null);
        super.onDestroy();
    }

    private void toast(String message) {
        main.post(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }

    private void showOverlay() {
        if (buttonOverlay != null) return;
        windows = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Button bubble = new Button(this);
        bubble.setAllCaps(false);
        bubble.setText("🎙 Джарвіс");
        bubble.setTextSize(13f);
        bubble.setContentDescription("Джарвіс — натисни для голосової команди, утримуй для вимкнення");
        bubble.setOnClickListener(v -> recordOneCommand());
        bubble.setOnLongClickListener(v -> {
            stopSession();
            toast("Керування екраном вимкнено.");
            return true;
        });
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        try {
            windows.addView(bubble, lp);
            buttonOverlay = bubble;
        } catch (Exception error) {
            sessionActive = false;
            android.util.Log.e("JARVIS_SCREEN_CONTROL", "Accessibility overlay failed", error);
            toast("Не вдалося показати кнопку керування екраном.");
        }
    }

    private void stopListening() {
        recording = false;
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (Exception ignored) {}
        }
        if(headsetRoute!=null)headsetRoute.end();
        if(listeningAudioFocus!=null)listeningAudioFocus.end();
    }

    private void recordOneCommand() {
        if (!sessionActive) return;
        if (sequences != null && sequences.isRunning()) {
            sequences.cancel(); // same button is an immediate stop
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            toast("Надай Джарвісу доступ до мікрофона в налаштуваннях застосунку.");
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("На пристрої немає доступного розпізнавача мовлення.");
            return;
        }
        try {
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(this);
                recognizer.setRecognitionListener(new RecognitionListener() {
                    @Override public void onReadyForSpeech(Bundle params) {
                        toast("Слухаю команду керування екраном.");
                    }
                    @Override public void onBeginningOfSpeech() {}
                    @Override public void onRmsChanged(float rmsdB) {}
                    @Override public void onBufferReceived(byte[] buffer) {}
                    @Override public void onEndOfSpeech() {}
                    @Override public void onError(int error) {
                        recording = false;
                        if(headsetRoute!=null)headsetRoute.end();
                        if(listeningAudioFocus!=null)listeningAudioFocus.end();
                        if (sessionActive) toast("Не розпізнав команду. Натисни мікрофон ще раз.");
                    }
                    @Override public void onResults(Bundle results) {
                        recording = false;
                        if(headsetRoute!=null)headsetRoute.end();
                        if(listeningAudioFocus!=null)listeningAudioFocus.end();
                        if (!sessionActive) return;
                        ArrayList<String> phrases =
                            results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (phrases == null || phrases.isEmpty()) {
                            toast("Не почув команду.");
                            return;
                        }
                        // Preserve case/punctuation: "Напиши..." is literal dictation,
                        // unlike navigation commands which normalize inside the parser.
                        String raw = phrases.get(0);
                        JarvisPhoneMode.Transition mode = JarvisPhoneMode.transition(raw);
                        if (mode == JarvisPhoneMode.Transition.ENTER) {
                            toast(JarvisPhoneModeManager.enter(
                                ScreenControlAccessibilityService.this, false));
                            return;
                        }
                        if (mode == JarvisPhoneMode.Transition.EXIT &&
                            JarvisPhoneMode.isActive()) {
                            toast(JarvisPhoneModeManager.exit());
                            return;
                        }
                        if (spotifyPlayback != null && spotifyPlayback.isRunning()) {
                            String request = CommandPatterns.normalize(raw);
                            if (request.equals("скасуй spotify") ||
                                request.equals("скасуй спотіфай"))
                                spotifyPlayback.cancel();
                            else toast("Зачекай завершення Spotify або скажи «Скасуй Spotify».");
                            return;
                        }
                        TelegramReplyCommands.Request telegram=
                            TelegramReplyCommands.parse(raw);
                        if(telegram!=null) {
                            TelegramReplyController.perform(
                                ScreenControlAccessibilityService.this,telegram,
                                (success,result)->toast(result));
                            return;
                        }
                        if (FinanceManagerController.isRunning()) {
                            if(FinanceVoiceCommands.cancel(raw))FinanceManagerController.cancel();
                            else toast("Фінансовий запис триває. Скажи «Скасуй витрату».");
                            return;
                        }
                        if(FinanceVoiceCommands.parse(raw)!=null) {
                            FinanceManagerController.start(
                                ScreenControlAccessibilityService.this,raw,
                                new FinanceManagerController.Listener(){
                                    @Override public void onProgress(String detail){toast(detail);}
                                    @Override public void onFinished(boolean ok,String detail){toast(detail);}
                                });
                            return;
                        }
                        if (JarvisTaxiCommands.destination(raw) != null) {
                            BoltTaxiController.start(ScreenControlAccessibilityService.this,
                                raw,new BoltTaxiController.Listener() {
                                    @Override public void onProgress(String info) {toast(info);}
                                    @Override public void onFinished(boolean ok,String info) {toast(info);}
                                });
                            return;
                        }
                        SpotifyMusicCommands.Kind selection = SpotifyMusicCommands.parse(raw);
                        if (selection != null) {
                            if (spotifyPlayback == null)
                                spotifyPlayback = new SpotifyPlaybackController(
                                    ScreenControlAccessibilityService.this);
                            spotifyPlayback.start(selection,raw,new SpotifyPlaybackController.Listener() {
                                @Override public void onProgress(String progress) {
                                    if (sessionActive) toast(progress);
                                }
                                @Override public void onFinished(boolean success, String details) {
                                    if (sessionActive) toast(details);
                                }
                            });
                            return;
                        }
                        JarvisCommandSequence.Plan plan = JarvisCommandSequence.parse(raw);
                        if (plan != null &&
                            (!plan.valid() || plan.multiple() ||
                            plan.steps.get(0).kind == JarvisCommandSequence.Kind.OPEN_APP ||
                            plan.steps.get(0).kind == JarvisCommandSequence.Kind.MEDIA_VOLUME)) {
                            if (!plan.valid()) {
                                toast(plan.error);
                                return;
                            }
                            if (sequences == null)
                                sequences = new JarvisSequenceExecutor(
                                    ScreenControlAccessibilityService.this);
                            sequences.start(plan, new JarvisSequenceExecutor.Listener() {
                                @Override public void onProgress(int step, int total,
                                                                 String description) {
                                    if (sessionActive)
                                        toast("Крок " + step + "/" + total + ": " + description);
                                }
                                @Override public void onFinished(boolean success,
                                                                 String summary) {
                                    if (sessionActive) toast(summary);
                                }
                            });
                            return;
                        }
                        ScreenControlCommands.Action action =
                            ScreenControlCommands.parse(raw);
                        if (action == null) {
                            toast("Скажи «Напиши Привіт», «Відкрий пошук», «Прокрути вниз» або «Назад».");
                        } else if (action.kind == ScreenControlCommands.Kind.DISABLE) {
                            toast(stop());
                        } else if (action.kind == ScreenControlCommands.Kind.STATUS) {
                            toast(isSessionActive() ? "Керування активне." : "Керування вимкнене.");
                        } else if (action.kind == ScreenControlCommands.Kind.CLICK ||
                            action.kind == ScreenControlCommands.Kind.LONG_PRESS ||
                            action.kind == ScreenControlCommands.Kind.SWIPE_UP ||
                            action.kind == ScreenControlCommands.Kind.SWIPE_DOWN ||
                            action.kind == ScreenControlCommands.Kind.SWIPE_LEFT ||
                            action.kind == ScreenControlCommands.Kind.SWIPE_RIGHT ||
                            action.kind == ScreenControlCommands.Kind.TYPE_TEXT ||
                            action.kind == ScreenControlCommands.Kind.SCROLL_DOWN ||
                            action.kind == ScreenControlCommands.Kind.SCROLL_UP ||
                            action.kind == ScreenControlCommands.Kind.BACK) {
                            toast(perform(action));
                        } else {
                            toast("Режим уже запущено. Для зупинки утримуй кнопку.");
                        }
                    }
                    @Override public void onPartialResults(Bundle partialResults) {}
                    @Override public void onEvent(int eventType, Bundle params) {}
                });
            }
            if (recording) {
                // A second tap stops current recognition, never stacks listeners.
                recognizer.stopListening();
                return;
            }
            Intent speech = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            speech.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            speech.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uk-UA");
            speech.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
            speech.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            if(headsetRoute==null){
                headsetRoute=new JarvisHeadsetAudioRouter(this);
                headsetRoute.setOnRouteLost(() -> {
                    String reason=headsetRoute.problem();
                    stopListening();
                    toast(reason);
                });
            }
            if(!headsetRoute.begin()){
                toast(headsetRoute.problem());
                return;
            }
            if(listeningAudioFocus==null)
                listeningAudioFocus=new JarvisListeningAudioFocus(this);
            if(!listeningAudioFocus.begin())
                toast("Android не дав призупинити інші звуки. Мікрофон слухає.");
            recording = true;
            recognizer.startListening(speech);
        } catch (Exception error) {
            recording = false;
            if(headsetRoute!=null)headsetRoute.end();
            if(listeningAudioFocus!=null)listeningAudioFocus.end();
            toast("Не вдалося розпочати розпізнавання. Перевір дозвіл мікрофона.");
        }
    }

    private AccessibilityNodeInfo currentShazamRoot() {
        AccessibilityNodeInfo root = currentExternalRoot();
        return root != null && root.isVisibleToUser() &&
            root.getPackageName() != null &&
            "com.shazam.android".contentEquals(root.getPackageName()) ? root : null;
    }

    private AccessibilityNodeInfo currentExternalRoot() {
        if (!JarvisPhoneScreenGuard.available(this)) return null;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        CharSequence pkg = root.getPackageName();
        // Never operate on our own UI or on system/permission management screens.
        if (pkg == null || pkg.toString().equals(getPackageName()) ||
            pkg.toString().equals("com.android.settings") ||
            pkg.toString().equals("com.android.permissioncontroller") ||
            pkg.toString().equals("com.google.android.permissioncontroller") ||
            pkg.toString().equals("com.android.systemui")) return null;
        return root;
    }

    private static String name(CharSequence value) {
        return value == null ? "" : value.toString().toLowerCase(Locale.forLanguageTag("uk-UA"))
            .replaceAll("\\s+", " ").replaceAll("[:：]+$", "").trim();
    }

    private static AccessibilityNodeInfo clickable(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo n = node;
        for (int i = 0; i < 5 && n != null; i++, n = n.getParent()) {
            if (n.isPassword() || n.isEditable()) return null;
            if (n.isVisibleToUser() && n.isEnabled() && n.isClickable()) return n;
        }
        return null;
    }

    /** Long-clickable list rows are not necessarily ordinary clickable buttons. */
    private static AccessibilityNodeInfo longPressable(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo cursor = node;
        AccessibilityNodeInfo fallback = null;
        for (int depth = 0; depth < 5 && cursor != null; depth++,cursor=cursor.getParent()) {
            if (cursor.isPassword() || cursor.isEditable()) return null;
            if (!cursor.isVisibleToUser() || !cursor.isEnabled()) continue;
            if (cursor.isLongClickable()) return cursor;
            if (fallback == null && cursor.isClickable()) fallback = cursor;
        }
        return fallback;
    }

    private static String asString(CharSequence value) {
        return value == null ? "" : value.toString();
    }

    private String clickNamed(String label) {
        if (label == null || label.isEmpty() ||
            ScreenControlCommands.isSensitiveLabel(label)) {
            return "Цю дію я не виконуватиму автоматично. Платежі, підтвердження, надсилання й видалення потребують ручного натискання.";
        }
        AccessibilityNodeInfo root = currentExternalRoot();
        if (root == null)
            return "Відкрий потрібну програму й подай команду через іконку Джарвіса. Системними дозволами я не керую.";

        if ("com.spotify.music".equals(asString(root.getPackageName())) &&
            SpotifyUiLabels.shuffleRequest(label)) {
            SpotifyStep step = tapSpotifyControl(SpotifyUiLabels.Target.SHUFFLE, null);
            if (step == SpotifyStep.CLICKED) return "Знайшов потрібну кнопку й натиснув.";
            if (step == SpotifyStep.ALREADY) return "Перемішування вже ввімкнено.";
            return "Не знайшов кнопку перемішування праворуч, " +
                "безпосередньо ліворуч від великого зеленого Play. " +
                "Ліву стрілку завантаження не натискатиму.";
        }
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        ArrayList<AccessibilityNodeInfo> controls = new ArrayList<>();
        ArrayList<ScreenTargetMatcher.Target> descriptions = new ArrayList<>();
        java.util.Map<String, Integer> boundsToIndex = new java.util.HashMap<>();
        int scanned = 0;

        while (!queue.isEmpty()) {
            if (++scanned > MAX_SCAN)
                return "На екрані надто багато елементів. Не натискатиму нічого навмання.";
            AccessibilityNodeInfo node = queue.removeFirst();
            if (!node.isVisibleToUser() || node.isPassword()) continue;

            AccessibilityNodeInfo control = clickable(node);
            if (control != null) {
                Rect rect = new Rect();
                control.getBoundsInScreen(rect);
                if (rect.width() > 0 && rect.height() > 0) {
                    String bounds = rect.toShortString();
                    ScreenTargetMatcher.Target target = new ScreenTargetMatcher.Target(
                        asString(node.getText()), asString(node.getContentDescription()),
                        node.getViewIdResourceName(),
                        asString(control.getText()), asString(control.getContentDescription()),
                        control.getViewIdResourceName());
                    Integer oldIndex = boundsToIndex.get(bounds);
                    if (oldIndex == null) {
                        boundsToIndex.put(bounds, controls.size());
                        controls.add(control);
                        descriptions.add(target);
                    } else if (ScreenTargetMatcher.score(label, target) >
                        ScreenTargetMatcher.score(label, descriptions.get(oldIndex))) {
                        controls.set(oldIndex, control);
                        descriptions.set(oldIndex, target);
                    }
                }
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.addLast(child);
            }
        }

        ScreenTargetMatcher.Selection selected =
            ScreenTargetMatcher.select(label, descriptions);
        if (selected.ambiguous)
            return "Знайшов кілька можливих кнопок для «" + label +
                "». Уточни, яку саме натиснути.";
        if (selected.index < 0)
            return "Не зміг однозначно визначити кнопку для «" + label +
                "». Спробуй описати її призначення. Значки без опису поки не розпізнаю.";

        // Re-check the actual control before interacting, in case a screen
        // refresh changed its text or the active app while matching.
        AccessibilityNodeInfo chosen = controls.get(selected.index);
        ScreenTargetMatcher.Target metadata = descriptions.get(selected.index);
        if (!sessionActive || !chosen.isVisibleToUser() || !chosen.isEnabled() ||
            !chosen.isClickable() || chosen.isPassword() ||
            ScreenTargetMatcher.dangerous(metadata)) {
            return "Кнопка більше недоступна або ця дія потребує ручного підтвердження.";
        }
        AccessibilityNodeInfo current = currentExternalRoot();
        if (current == null ||
            !asString(current.getPackageName()).equals(asString(root.getPackageName()))) {
            return "Екран змінився. Не натискатиму кнопку без повторної команди.";
        }
        Rect tapBounds = new Rect();
        chosen.getBoundsInScreen(tapBounds);
        FloatingJarvisService.avoidControl(tapBounds);
        boolean clicked = chosen.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        return clicked ? "Знайшов потрібну кнопку й натиснув." :
            "Кнопку знайдено, але застосунок не дозволив її натиснути.";
    }

    /** Dedicated, allowlisted Spotify UI interaction for explicit music commands.
     * Never guesses arbitrary icon coordinates and never touches other apps.
     */
    public enum SpotifyStep { WAIT, CLICKED, ALREADY, BLOCKED }

    public static SpotifyStep spotifyControl(SpotifyUiLabels.Target target,
                                              SpotifyUiLabels.Target playlist) {
        ScreenControlAccessibilityService service = connected.get();
        if (service == null || !service.sessionActive) return SpotifyStep.BLOCKED;
        return service.tapSpotifyControl(target, playlist);
    }

    /** Spotify global Search tab, explicitly on the Pixel; never on Wear.
     * First try the accessible tab; only if Android rejects ACTION_CLICK,
     * emulate a touch at the centre of a VISIBLE bottom Search label. */
    public static SpotifyStep spotifySearchTab() {
        ScreenControlAccessibilityService service=connected.get();
        if(service==null || !service.sessionActive)return SpotifyStep.BLOCKED;
        AccessibilityNodeInfo root=service.currentSpotifyRoot();
        if(root==null)return SpotifyStep.WAIT;
        if(service.spotifySearchEditor(root)!=null)return SpotifyStep.ALREADY;
        Rect frame=new Rect();root.getBoundsInScreen(frame);
        ArrayDeque<AccessibilityNodeInfo> nodes=new ArrayDeque<>();nodes.add(root);
        AccessibilityNodeInfo found=null;Rect labelBox=null;
        int scanned=0;
        while(!nodes.isEmpty() && ++scanned<=MAX_SCAN) {
            AccessibilityNodeInfo node=nodes.removeFirst();
            if(!node.isVisibleToUser() || !node.isEnabled() || node.isPassword())continue;
            if(SpotifyTrackSearchRules.searchTab(asString(node.getText())) ||
               SpotifyTrackSearchRules.searchTab(asString(node.getContentDescription()))) {
                Rect rect=new Rect();node.getBoundsInScreen(rect);
                if(SpotifyTrackSearchRules.bottomSearchTabRegion(rect.centerX(),
                    rect.centerY(),rect.width(),rect.height(),
                    frame.left,frame.top,frame.right,frame.bottom)) {
                    AccessibilityNodeInfo target=clickable(node);
                    if(labelBox!=null && !labelBox.equals(rect))return SpotifyStep.WAIT;
                    labelBox=rect;
                    found=target;
                }
            }
            for(int i=0;i<node.getChildCount();i++){
                AccessibilityNodeInfo child=node.getChild(i);
                if(child!=null)nodes.addLast(child);
            }
        }
        if(scanned>MAX_SCAN || labelBox==null ||
           service.currentSpotifyRoot()==null)return SpotifyStep.WAIT;
        FloatingJarvisService.avoidControl(labelBox);
        if(found!=null && found.isEnabled() &&
           found.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            return SpotifyStep.CLICKED;
        if(!service.sessionActive || service.currentSpotifyRoot()==null)
            return SpotifyStep.WAIT;
        Path path=new Path();
        path.moveTo(labelBox.exactCenterX(),labelBox.exactCenterY());
        GestureDescription gesture=new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(path,0,85))
            .build();
        return service.dispatchTrackedGesture(gesture,(ok,detail)->{
            if(!ok && service.sessionActive)
                service.toast("Spotify: не вдалося відкрити «Пошук»: "+detail);
        }) ? SpotifyStep.CLICKED : SpotifyStep.WAIT;
    }

    /** Did a deep link or normal UI navigate to this exact song query?
     * Never trust a song-result title alone: only a search editor value or
     * an upper search-query header authorizes selecting track results. */
    public static boolean spotifySearchQueryVisible(String title) {
        ScreenControlAccessibilityService service=connected.get();
        if(service==null || !service.sessionActive || title==null)return false;
        AccessibilityNodeInfo root=service.currentSpotifyRoot();
        return root!=null && service.spotifyQueryPresent(root,title);
    }

    private boolean spotifyQueryPresent(AccessibilityNodeInfo root,String title) {
        AccessibilityNodeInfo field=spotifySearchEditor(root);
        if(field!=null && SpotifyTrackSearchRules.exactSong(title,
            asString(field.getText())))return true;
        Rect frame=new Rect();root.getBoundsInScreen(frame);
        ArrayDeque<AccessibilityNodeInfo> q=new ArrayDeque<>();q.add(root);
        int scanned=0;
        while(!q.isEmpty() && ++scanned<=MAX_SCAN) {
            AccessibilityNodeInfo node=q.removeFirst();
            if(!node.isVisibleToUser() || !node.isEnabled() || node.isPassword())continue;
            if(!node.isEditable() &&
               (SpotifyTrackSearchRules.exactSong(title,asString(node.getText())) ||
                SpotifyTrackSearchRules.exactSong(title,asString(node.getContentDescription())))) {
                Rect bounds=new Rect();node.getBoundsInScreen(bounds);
                if(SpotifyTrackSearchRules.queryHeaderRegion(
                    bounds.centerX(),bounds.centerY(),bounds.width(),bounds.height(),
                    frame.left,frame.top,frame.right,frame.bottom))
                    return true;
            }
            for(int i=0;i<node.getChildCount();i++){
                AccessibilityNodeInfo child=node.getChild(i);
                if(child!=null)q.addLast(child);
            }
        }
        return false;
    }

    private AccessibilityNodeInfo spotifySearchEditor(AccessibilityNodeInfo root) {
        if(root==null)return null;
        Rect frame=new Rect();root.getBoundsInScreen(frame);
        ArrayDeque<AccessibilityNodeInfo> scan=new ArrayDeque<>();scan.add(root);
        AccessibilityNodeInfo field=null;
        int checked=0;
        while(!scan.isEmpty() && ++checked<=MAX_SCAN) {
            AccessibilityNodeInfo item=scan.removeFirst();
            if(!item.isVisibleToUser() || !item.isEnabled() || item.isPassword())continue;
            if(item.isEditable()) {
                Rect r=new Rect();item.getBoundsInScreen(r);
                String labels=asString(item.getText())+" "+
                    asString(item.getContentDescription())+" "+
                    asString(item.getHintText());
                if(r.centerY()>frame.top+frame.height()*.02f &&
                   r.centerY()<frame.top+frame.height()*.32f &&
                   r.width()>frame.width()*.35f &&
                   (SpotifyTrackSearchRules.searchField(asString(item.getHintText())) ||
                    SpotifyTrackSearchRules.searchField(asString(item.getContentDescription())) ||
                    SpotifyTrackSearchRules.searchField(asString(item.getText())) ||
                    item.isFocused())) {
                    if(field!=null && field!=item)return null;
                    field=item;
                }
            }
            for(int i=0;i<item.getChildCount();i++){
                AccessibilityNodeInfo child=item.getChild(i);
                if(child!=null)scan.addLast(child);
            }
        }
        return checked>MAX_SCAN?null:field;
    }

    /** Focus a visible, unambiguous top-of-page Spotify Search field. */
    public static SpotifyStep spotifyFocusSearch() {
        ScreenControlAccessibilityService service=connected.get();
        if(service==null || !service.sessionActive)return SpotifyStep.BLOCKED;
        AccessibilityNodeInfo root=service.currentSpotifyRoot();
        if(root==null)return SpotifyStep.WAIT;
        AccessibilityNodeInfo field=service.spotifySearchEditor(root);
        if(field!=null) {
            if(field.isFocused())return SpotifyStep.ALREADY;
            return field.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                ? SpotifyStep.CLICKED : SpotifyStep.WAIT;
        }
        Rect frame=new Rect();root.getBoundsInScreen(frame);
        ArrayDeque<AccessibilityNodeInfo> nodes=new ArrayDeque<>();nodes.add(root);
        int scanned=0;
        while(!nodes.isEmpty() && ++scanned<=MAX_SCAN) {
            AccessibilityNodeInfo node=nodes.removeFirst();
            if(!node.isVisibleToUser() || !node.isEnabled() || node.isPassword())continue;
            if(SpotifyTrackSearchRules.searchField(asString(node.getText())) ||
               SpotifyTrackSearchRules.searchField(asString(node.getContentDescription()))) {
                AccessibilityNodeInfo target=clickable(node);
                if(target!=null && target.isEnabled()) {
                    Rect r=new Rect();target.getBoundsInScreen(r);
                    if(r.centerY()<frame.top+frame.height()*.35f &&
                       r.width()>frame.width()*.34f) {
                        FloatingJarvisService.avoidControl(r);
                        return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            ? SpotifyStep.CLICKED : SpotifyStep.WAIT;
                    }
                }
            }
            for(int i=0;i<node.getChildCount();i++){
                AccessibilityNodeInfo child=node.getChild(i);
                if(child!=null)nodes.addLast(child);
            }
        }
        return SpotifyStep.WAIT;
    }

    /** Set exact user-dictated song title in Spotify's own Search textbox. */
    public static SpotifyStep spotifyTypeTrack(String title) {
        if(title==null || title.trim().isEmpty() || title.length()>100)
            return SpotifyStep.BLOCKED;
        ScreenControlAccessibilityService service=connected.get();
        if(service==null || !service.sessionActive)return SpotifyStep.BLOCKED;
        AccessibilityNodeInfo root=service.currentSpotifyRoot();
        if(root==null)return SpotifyStep.WAIT;
        AccessibilityNodeInfo input=service.spotifySearchEditor(root);
        if(input==null || !input.isFocused() || input.isPassword())
            return SpotifyStep.WAIT;
        if(SpotifyTrackSearchRules.exactSong(title,asString(input.getText())))
            return SpotifyStep.ALREADY;
        Bundle args=new Bundle();
        args.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,title);
        return input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args)
            ? SpotifyStep.CLICKED : SpotifyStep.WAIT;
    }

    /** Select the FIRST exact-title song on the search-results page only.
     * Never selects a similarly named artist/album/playlist, search field,
     * card in a carousel or an unrelated result. */
    public static SpotifyStep spotifySelectTrack(String title) {
        ScreenControlAccessibilityService service=connected.get();
        if(service==null || !service.sessionActive)return SpotifyStep.BLOCKED;
        AccessibilityNodeInfo root=service.currentSpotifyRoot();
        if(root==null || title==null || title.isEmpty())return SpotifyStep.WAIT;
        if(!service.spotifyQueryPresent(root,title))
            return SpotifyStep.WAIT;
        Rect frame=new Rect();root.getBoundsInScreen(frame);
        ArrayDeque<AccessibilityNodeInfo> nodes=new ArrayDeque<>();nodes.add(root);
        AccessibilityNodeInfo chosen=null;
        Rect chosenBox=null;
        int scanned=0;
        while(!nodes.isEmpty() && ++scanned<=MAX_SCAN) {
            AccessibilityNodeInfo node=nodes.removeFirst();
            if(!node.isVisibleToUser() || !node.isEnabled() || node.isPassword())continue;
            if(!node.isEditable() &&
               (SpotifyTrackSearchRules.exactSong(title,asString(node.getText())) ||
                SpotifyTrackSearchRules.exactSong(title,asString(node.getContentDescription())) ||
                SpotifyTrackSearchRules.exactTypedSong(title,
                    asString(node.getContentDescription())))) {
                AccessibilityNodeInfo action=clickable(node);
                if(action!=null && action.isEnabled() && action.isClickable()) {
                    Rect r=new Rect();action.getBoundsInScreen(r);
                    if(SpotifyTrackSearchRules.resultRegion(r.centerX(),r.centerY(),
                        r.width(),r.height(),frame.left,frame.top,
                        frame.right,frame.bottom)) {
                        if(chosen==null || r.top<chosenBox.top) {
                            chosen=action;chosenBox=r;
                        }
                    }
                }
            }
            for(int i=0;i<node.getChildCount();i++){
                AccessibilityNodeInfo child=node.getChild(i);
                if(child!=null)nodes.addLast(child);
            }
        }
        if(scanned>MAX_SCAN || chosen==null ||
           service.currentSpotifyRoot()==null)return SpotifyStep.WAIT;
        FloatingJarvisService.avoidControl(chosenBox);
        return chosen.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            ? SpotifyStep.CLICKED : SpotifyStep.WAIT;
    }

    /** Open one saved playlist selected by an explicit voice command.
     * A library item must have an exact visible title and a clickable row.
     * Ambiguous duplicates do not get clicked. */
    public static SpotifyStep spotifyOpenNamedPlaylist(String name) {
        return spotifyNamedPlaylistEntry(name,false);
    }

    /** A one-time rescue when Spotify reported an accessibility row click
     * but did not navigate. Do not retry after the detail page appeared. */
    public static SpotifyStep spotifyRetryNamedPlaylistEntry(String name) {
        return spotifyNamedPlaylistEntry(name,true);
    }

    public static SpotifyStep spotifyNamedPlaylistDetailReady(String name) {
        ScreenControlAccessibilityService service=connected.get();
        if(service==null || !service.sessionActive || name==null)
            return SpotifyStep.BLOCKED;
        AccessibilityNodeInfo root=service.currentSpotifyRoot();
        return root!=null && service.spotifyPlaylistHeaderVisible(root,null,name)
            ? SpotifyStep.ALREADY : SpotifyStep.WAIT;
    }

    private static SpotifyStep spotifyNamedPlaylistEntry(String name,
                                                         boolean forceTouch) {
        ScreenControlAccessibilityService service=connected.get();
        if(service==null || !service.sessionActive || name==null)
            return SpotifyStep.BLOCKED;
        AccessibilityNodeInfo root=service.currentSpotifyRoot();
        if(root==null)return SpotifyStep.WAIT;
        // Detect an opened playlist by its exact upper header, even when
        // Spotify hides the big Play icon from Accessibility. The old guard
        // required a text-labelled Play and got stuck after opening a list.
        if(service.spotifyPlaylistHeaderVisible(root,null,name))
            return SpotifyStep.ALREADY;
        Rect frame=new Rect();
        root.getBoundsInScreen(frame);
        ArrayDeque<AccessibilityNodeInfo> q=new ArrayDeque<>();
        q.add(root);
        AccessibilityNodeInfo found=null;
        Rect foundBounds=null;
        Rect titleBounds=null;
        int examined=0;
        while(!q.isEmpty() && ++examined<=MAX_SCAN){
            AccessibilityNodeInfo n=q.removeFirst();
            if(!n.isVisibleToUser() || !n.isEnabled() || n.isPassword())continue;
            boolean title=SpotifyUiLabels.namedPlaylist(name,asString(n.getText())) ||
                SpotifyUiLabels.namedPlaylist(name,asString(n.getContentDescription()));
            if(title){
                Rect labelBounds=new Rect();
                n.getBoundsInScreen(labelBounds);
                AccessibilityNodeInfo control=clickable(n);
                if(control!=null && control.isEnabled() && control.isClickable() &&
                   labelBounds.centerY()>frame.top+frame.height()*.13f &&
                   labelBounds.centerY()<frame.top+frame.height()*.86f) {
                    Rect box=new Rect();
                    control.getBoundsInScreen(box);
                    // Exclude the bottom mini-player and full-screen controls.
                    if(box.width()>16 && box.height()>16 &&
                       box.height()<frame.height()*.24f &&
                       box.centerY()<frame.bottom-frame.height()*.13f) {
                        if(foundBounds!=null && !foundBounds.equals(box))
                            return SpotifyStep.WAIT;
                        found=control;
                        foundBounds=box;
                        titleBounds=labelBounds;
                    }
                }
            }
            for(int k=0;k<n.getChildCount();k++){
                AccessibilityNodeInfo child=n.getChild(k);
                if(child!=null)q.addLast(child);
            }
        }
        if(examined>MAX_SCAN || found==null || service.currentSpotifyRoot()==null)
            return SpotifyStep.WAIT;
        FloatingJarvisService.avoidControl(foundBounds);
        if(!forceTouch)
            return found.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                ? SpotifyStep.CLICKED : SpotifyStep.WAIT;
        if(titleBounds==null || titleBounds.isEmpty() ||
           !foundBounds.contains(titleBounds.centerX(),titleBounds.centerY()) ||
           service.currentSpotifyRoot()==null)return SpotifyStep.WAIT;
        Path path=new Path();
        path.moveTo(titleBounds.exactCenterX(),titleBounds.exactCenterY());
        GestureDescription gesture=new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(path,0,90))
            .build();
        return service.dispatchTrackedGesture(gesture,(ok,detail)->{
            if(!ok && service.sessionActive)
                service.toast("Spotify: повторне відкриття плейлиста не вдалося: "+detail);
        }) ? SpotifyStep.CLICKED : SpotifyStep.WAIT;
    }

    /** PLAY and SHUFFLE can only target the exact already-open playlist,
     * never a visually similar song, other list row or bottom mini-player. */
    public static SpotifyStep spotifyNamedPlaylistControl(SpotifyUiLabels.Target action,
                                                           String name,boolean shuffleEnabled) {
        ScreenControlAccessibilityService service=connected.get();
        if(service==null || !service.sessionActive || name==null)
            return SpotifyStep.BLOCKED;
        AccessibilityNodeInfo root=service.currentSpotifyRoot();
        if(root==null)return SpotifyStep.WAIT;
        if(action==SpotifyUiLabels.Target.SHUFFLE) {
            if(!service.spotifyNamedPlaylistReady(root,name))return SpotifyStep.WAIT;
            return service.tapSpotifyRightShuffle(root,null,name,shuffleEnabled);
        }
        if(action==SpotifyUiLabels.Target.PLAY)
            return service.tapSpotifyPlaylistPlay(null,name);

        return SpotifyStep.BLOCKED;
    }

    /** Play a confirmed RAP/Liked-Songs or Ukrainian playlist, never a
     * generic global MediaPlay that might resume a different audio app. */
    public static SpotifyStep spotifyPlaylistPlay(SpotifyUiLabels.Target playlist) {
        ScreenControlAccessibilityService service=connected.get();
        if(service==null || !service.sessionActive || playlist==null)
            return SpotifyStep.BLOCKED;
        return service.tapSpotifyPlaylistPlay(playlist,null);
    }

    /** Read-only playback evidence for the opened playlist. Never equate
     * an accepted accessibility tap with real playback. */
    public static SpotifyStep spotifyPlaylistPlaybackState(
            SpotifyUiLabels.Target playlist,String named) {
        ScreenControlAccessibilityService service=connected.get();
        if(service==null || !service.sessionActive)return SpotifyStep.BLOCKED;
        AccessibilityNodeInfo root=service.currentSpotifyRoot();
        if(root==null)return SpotifyStep.WAIT;
        if(!service.spotifyPlaylistHeaderVisible(root,playlist,named))
            return SpotifyStep.WAIT;
        return service.spotifyLargePauseVisible(root) ?
            SpotifyStep.ALREADY : SpotifyStep.WAIT;
    }

    /** Only a large upper-right Pause control belonging to the verified
     * playlist indicates the visible UI has switched into playing state. */
    private boolean spotifyLargePauseVisible(AccessibilityNodeInfo root) {
        Rect page=new Rect();root.getBoundsInScreen(page);
        float density=getResources().getDisplayMetrics().density;
        ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>();
        queue.add(root);
        int seen=0;
        while(!queue.isEmpty() && ++seen<=MAX_SCAN) {
            AccessibilityNodeInfo item=queue.removeFirst();
            if(!item.isVisibleToUser() || item.isPassword())continue;
            if(SpotifyUiLabels.largePause(asString(item.getText())) ||
               SpotifyUiLabels.largePause(asString(item.getContentDescription()))) {
                AccessibilityNodeInfo action=clickable(item);
                Rect hit=new Rect();
                if(action!=null)action.getBoundsInScreen(hit);
                else item.getBoundsInScreen(hit);
                if(SpotifyUiLabels.playlistPlayAt(hit.left,hit.top,
                    hit.right,hit.bottom,page.left,page.top,
                    page.right,page.bottom,density))return true;
            }
            for(int i=0;i<item.getChildCount();i++){
                AccessibilityNodeInfo child=item.getChild(i);
                if(child!=null)queue.addLast(child);
            }
        }
        return false;
    }

    /** Prefer the real accessible Play. If a verified Spotify playlist page
     * has an unlabeled Compose button, use its actual bounds, not a guessed
     * top-right location. The old screenshot position is a final fallback
     * ONLY on a matching phone aspect and confirmed detail-page header. */
    private SpotifyStep tapSpotifyPlaylistPlay(SpotifyUiLabels.Target playlist,
                                               String named) {
        AccessibilityNodeInfo root=currentSpotifyRoot();
        if(root==null)return SpotifyStep.WAIT;
        if(playlist==null && (named==null || named.isEmpty()))
            return SpotifyStep.BLOCKED;
        if(!spotifyPlaylistHeaderVisible(root,playlist,named))
            return SpotifyStep.WAIT;
        if(spotifyLargePauseVisible(root))return SpotifyStep.ALREADY;
        SpotifyStep accessible=tapSpotifyControl(SpotifyUiLabels.Target.PLAY,
            playlist);
        if(accessible!=SpotifyStep.WAIT)return accessible;

        Rect page=new Rect();root.getBoundsInScreen(page);
        float density=getResources().getDisplayMetrics().density;
        ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>();
        queue.add(root);
        Rect best=null;
        int bestSize=0,runnerUp=0,seen=0;
        java.util.HashSet<String> unique=new java.util.HashSet<>();
        while(!queue.isEmpty() && ++seen<=MAX_SCAN) {
            AccessibilityNodeInfo node=queue.removeFirst();
            if(!node.isVisibleToUser() || node.isPassword() || !node.isEnabled())
                continue;
            if(node.isClickable()) {
                Rect b=new Rect();node.getBoundsInScreen(b);
                String key=b.toShortString();
                String label=asString(node.getText())+" "+
                    asString(node.getContentDescription());
                String lowered=SpotifyUiLabels.normalize(label);
                if(unique.add(key) &&
                   !SpotifyUiLabels.matches(SpotifyUiLabels.Target.SHUFFLE,label) &&
                   !lowered.contains("download") && !lowered.contains("завантаж") &&
                   !lowered.contains("скачать") && !lowered.contains("more") &&
                   !lowered.contains("menu") && !lowered.contains("ще") &&
                   SpotifyUiLabels.unlabelledPlaylistPlayCircle(
                       b.left,b.top,b.right,b.bottom,page.left,page.top,
                       page.right,page.bottom,density)) {
                    int size=Math.min(b.width(),b.height());
                    if(size>bestSize) {
                        runnerUp=bestSize;bestSize=size;best=b;
                    } else if(size>runnerUp)runnerUp=size;
                }
            }
            for(int i=0;i<node.getChildCount();i++){
                AccessibilityNodeInfo child=node.getChild(i);
                if(child!=null)queue.addLast(child);
            }
        }
        if(seen>MAX_SCAN)return SpotifyStep.WAIT;
        // A nearby icon with comparable size could be Shuffle or a menu.
        // Never pick arbitrarily among nearly-identical touch surfaces.
        if(best!=null && runnerUp>bestSize*.83f)return SpotifyStep.WAIT;
        int x,y;
        if(best!=null) {
            x=best.centerX();y=best.centerY();
        } else {
            android.graphics.Point physical=new android.graphics.Point();
            android.view.Display display=getDisplay();
            if(display==null) {
                WindowManager wm=(WindowManager)getSystemService(Context.WINDOW_SERVICE);
                if(wm!=null)display=wm.getDefaultDisplay();
            }
            if(display==null)return SpotifyStep.WAIT;
            display.getRealSize(physical);
            int[] point=SpotifyUiLabels.screenshotPlaylistPlayPoint(
                physical.x,physical.y);
            if(point==null)return SpotifyStep.WAIT;
            x=point[0];y=point[1];
        }
        if(!page.contains(x,y) || !sessionActive ||
           currentSpotifyRoot()==null)return SpotifyStep.WAIT;
        Rect target=new Rect(x-12,y-12,x+12,y+12);
        FloatingJarvisService.avoidControl(target);
        Path path=new Path();
        path.moveTo(x,y);
        GestureDescription gesture=new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(path,0,85))
            .build();
        return dispatchTrackedGesture(gesture,(ok,detail)->{
            if(!ok && sessionActive)
                toast("Spotify не натиснув Play: "+detail);
        }) ? SpotifyStep.CLICKED : SpotifyStep.WAIT;
    }

    /** Confirm a detail-page action area, not an ordinary library row.
     * Spotify Compose sometimes exposes the playlist title as a 22dp
     * clickable text node. Its toolbar Play/Pause button is the independent
     * evidence that this is the opened playlist screen. */
    private boolean spotifyPlaylistToolbarVisible(AccessibilityNodeInfo root) {
        Rect page=new Rect();root.getBoundsInScreen(page);
        float density=getResources().getDisplayMetrics().density;
        ArrayDeque<AccessibilityNodeInfo> q=new ArrayDeque<>();q.add(root);
        int scanned=0;
        while(!q.isEmpty() && ++scanned<=MAX_SCAN) {
            AccessibilityNodeInfo n=q.removeFirst();
            if(!n.isVisibleToUser() || !n.isEnabled() || n.isPassword())continue;
            AccessibilityNodeInfo action=clickable(n);
            if(action!=null) {
                Rect hit=new Rect();action.getBoundsInScreen(hit);
                boolean explicit=SpotifyUiLabels.matches(SpotifyUiLabels.Target.PLAY,
                    asString(n.getText())) ||
                    SpotifyUiLabels.matches(SpotifyUiLabels.Target.PLAY,
                        asString(n.getContentDescription())) ||
                    SpotifyUiLabels.largePause(asString(n.getText())) ||
                    SpotifyUiLabels.largePause(asString(n.getContentDescription()));
                if(explicit && SpotifyUiLabels.playlistPlayAt(hit.left,hit.top,
                    hit.right,hit.bottom,page.left,page.top,page.right,
                    page.bottom,density))return true;
                if(n.isClickable() && SpotifyUiLabels.unlabelledPlaylistPlayCircle(
                    hit.left,hit.top,hit.right,hit.bottom,page.left,page.top,
                    page.right,page.bottom,density))return true;
            }
            for(int i=0;i<n.getChildCount();i++) {
                AccessibilityNodeInfo child=n.getChild(i);
                if(child!=null)q.addLast(child);
            }
        }
        return false;
    }

    /** Retry a failed ACTION_CLICK only if the *same* playlist page still
     * exposes an actual labelled Play control. Never blindly tap an
     * unlabeled control again: it may have turned into Pause. */
    public static SpotifyStep spotifyRetryExplicitPlaylistPlay(
            SpotifyUiLabels.Target playlist,String named) {
        ScreenControlAccessibilityService service=connected.get();
        if(service==null || !service.sessionActive)return SpotifyStep.BLOCKED;
        AccessibilityNodeInfo root=service.currentSpotifyRoot();
        if(root==null || !service.spotifyPlaylistHeaderVisible(root,playlist,named))
            return SpotifyStep.WAIT;
        if(service.spotifyLargePauseVisible(root))return SpotifyStep.ALREADY;
        Rect page=new Rect();root.getBoundsInScreen(page);
        float density=service.getResources().getDisplayMetrics().density;
        ArrayDeque<AccessibilityNodeInfo> nodes=new ArrayDeque<>();
        nodes.add(root);
        Rect found=null;
        int scanned=0;
        while(!nodes.isEmpty() && ++scanned<=MAX_SCAN) {
            AccessibilityNodeInfo node=nodes.removeFirst();
            if(!node.isVisibleToUser() || !node.isEnabled() || node.isPassword())continue;
            if(SpotifyUiLabels.matches(SpotifyUiLabels.Target.PLAY,
                asString(node.getText())) ||
                SpotifyUiLabels.matches(SpotifyUiLabels.Target.PLAY,
                    asString(node.getContentDescription()))) {
                AccessibilityNodeInfo target=clickable(node);
                if(target!=null) {
                    Rect r=new Rect();target.getBoundsInScreen(r);
                    if(SpotifyUiLabels.playlistPlayAt(r.left,r.top,
                        r.right,r.bottom,page.left,page.top,page.right,
                        page.bottom,density)) {
                        if(found!=null && !found.equals(r))return SpotifyStep.WAIT;
                        found=r;
                    }
                }
            }
            for(int i=0;i<node.getChildCount();i++) {
                AccessibilityNodeInfo child=node.getChild(i);
                if(child!=null)nodes.addLast(child);
            }
        }
        if(scanned>MAX_SCAN || found==null ||
           service.currentSpotifyRoot()==null)return SpotifyStep.WAIT;
        FloatingJarvisService.avoidControl(found);
        Path path=new Path();
        path.moveTo(found.exactCenterX(),found.exactCenterY());
        GestureDescription gesture=new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(path,0,95))
            .build();
        return service.dispatchTrackedGesture(gesture,(ok,detail)->{
            if(!ok && service.sessionActive)
                service.toast("Spotify: повторне натискання Play не вдалося: "+detail);
        }) ? SpotifyStep.CLICKED : SpotifyStep.WAIT;
    }

    /** Only accept an exact playlist title displayed in its large upper
     * header. A small row in My Library is not enough to tap the Play area. */
    private boolean spotifyPlaylistHeaderVisible(AccessibilityNodeInfo root,
                                                  SpotifyUiLabels.Target playlist,
                                                  String named) {
        Rect frame=new Rect();root.getBoundsInScreen(frame);
        ArrayDeque<AccessibilityNodeInfo> q=new ArrayDeque<>();q.add(root);
        boolean title=false,smallClickableTitle=false,libraryTop=false;int scanned=0;
        while(!q.isEmpty() && ++scanned<=MAX_SCAN){
            AccessibilityNodeInfo n=q.removeFirst();
            if(!n.isVisibleToUser())continue;
            String text=asString(n.getText());
            String description=asString(n.getContentDescription());
            Rect rect=new Rect();n.getBoundsInScreen(rect);
            boolean matches= named!=null
                ? SpotifyUiLabels.namedPlaylist(named,text) ||
                  SpotifyUiLabels.namedPlaylist(named,description)
                : SpotifyUiLabels.matches(playlist,text) ||
                  SpotifyUiLabels.matches(playlist,description);
            if(matches && SpotifyUiLabels.headerRegion(rect.centerX(),rect.centerY(),
                rect.width(),rect.height(),frame.left,frame.top,
                frame.right,frame.bottom)) {
                // Library rows have a clickable parent and small text; a
                // detail-page heading is normally non-clickable or large.
                AccessibilityNodeInfo row=clickable(n);
                float density=getResources().getDisplayMetrics().density;
                if(row==null || rect.height()>=27*density)
                    title=true;
                else smallClickableTitle=true;
            }
            String folded=SpotifyUiLabels.normalize(text);
            if((folded.equals("your library") || folded.equals("моя бібліотека") ||
                folded.equals("моя медіатека") || folded.equals("бібліотека")) &&
                rect.centerY() < frame.top+frame.height()*.16f)
                libraryTop=true;
            for(int i=0;i<n.getChildCount();i++){
                AccessibilityNodeInfo child=n.getChild(i);
                if(child!=null)q.addLast(child);
            }
        }
        return SpotifyUiLabels.playlistDetailEvidence(
            title,smallClickableTitle,
            smallClickableTitle && spotifyPlaylistToolbarVisible(root),
            libraryTop,scanned<=MAX_SCAN);
    }

    /** A playlist page can be ready even when Spotify's green Play is
     * unlabeled. Require the actual detail-page header instead of Play text;
     * otherwise some modern Spotify builds never reach playback stage. */
    private boolean spotifyNamedPlaylistReady(AccessibilityNodeInfo root,String name) {
        return spotifyPlaylistHeaderVisible(root,null,name);
    }

    public static boolean spotifyVisible() {
        ScreenControlAccessibilityService s = connected.get();
        return s != null && s.sessionActive && s.currentSpotifyRoot() != null;
    }

    public static boolean spotifyScrollLibrary() {
        ScreenControlAccessibilityService s = connected.get();
        if (s == null || !s.sessionActive) return false;
        AccessibilityNodeInfo root = s.currentSpotifyRoot();
        if (root == null) return false;
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        int scanned = 0;
        while (!queue.isEmpty() && ++scanned < MAX_SCAN) {
            AccessibilityNodeInfo n = queue.removeFirst();
            if (!n.isVisibleToUser()) continue;
            if (n.isScrollable() && n.isEnabled() &&
                n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true;
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) queue.addLast(child);
            }
        }
        return false;
    }

    private AccessibilityNodeInfo currentSpotifyRoot() {
        AccessibilityNodeInfo root = currentExternalRoot();
        return root != null && root.getPackageName() != null &&
            "com.spotify.music".contentEquals(root.getPackageName()) ? root : null;
    }

    private static boolean spotifyTextMatches(SpotifyUiLabels.Target target,
                                               AccessibilityNodeInfo node) {
        if (node == null) return false;
        return SpotifyUiLabels.matches(target, asString(node.getText())) ||
            SpotifyUiLabels.matches(target, asString(node.getContentDescription())) ||
            SpotifyUiLabels.matches(target, asString(node.getViewIdResourceName()));
    }

    private static boolean spotifyHasTitle(AccessibilityNodeInfo root,
                                            SpotifyUiLabels.Target playlist) {
        ArrayDeque<AccessibilityNodeInfo> scan = new ArrayDeque<>();
        scan.add(root);
        int steps = 0;
        while (!scan.isEmpty() && ++steps < MAX_SCAN) {
            AccessibilityNodeInfo n = scan.removeFirst();
            if (!n.isVisibleToUser()) continue;
            if (spotifyTextMatches(playlist,n)) return true;
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) scan.addLast(child);
            }
        }
        return false;
    }

    private static final class SpotifyChoice {
        final AccessibilityNodeInfo node;
        final String description;
        final Rect bounds;
        final int score;
        SpotifyChoice(AccessibilityNodeInfo node, String description,
                      Rect bounds, int score) {
            this.node = node; this.description = description;
            this.bounds = bounds; this.score = score;
        }
    }

    /**
     * Tap the right-hand Spotify shuffle icon at a REAL screen coordinate,
     * rather than clicking an Accessibility parent or matching the green
     * download arrow at the far left. First use the visible big Play as the
     * geometric anchor. The user's Pixel Liked Songs screenshot is the
     * strictly-scoped fallback when Spotify hides Play's accessible label.
     */
    private SpotifyStep tapSpotifyRightShuffle(AccessibilityNodeInfo root,
                                                SpotifyUiLabels.Target playlist) {
        return tapSpotifyRightShuffle(root,playlist,null,true);
    }

    private SpotifyStep tapSpotifyRightShuffle(AccessibilityNodeInfo root,
                                                SpotifyUiLabels.Target playlist,
                                                String named,boolean desiredOn) {
        if (!sessionActive || root==null || currentSpotifyRoot()==null)
            return SpotifyStep.WAIT;
        if (named!=null && !spotifyNamedPlaylistReady(root,named))
            return SpotifyStep.WAIT;
        if (named==null && playlist!=null && !spotifyHasTitle(root,playlist))
            return SpotifyStep.WAIT;
        if (named==null && playlist==null &&
            !spotifyHasTitle(root,SpotifyUiLabels.Target.LIKED_SONGS) &&
            !spotifyHasTitle(root,SpotifyUiLabels.Target.UKR_MUSIC))
            return SpotifyStep.WAIT;

        Rect frame=new Rect();
        root.getBoundsInScreen(frame);
        float density=getResources().getDisplayMetrics().density;
        ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>();
        queue.add(root);
        Rect play=null;
        ArrayList<SpotifyChoice> rightArrows=new ArrayList<>();
        int seen=0;
        while(!queue.isEmpty() && ++seen<=MAX_SCAN) {
            AccessibilityNodeInfo node=queue.removeFirst();
            if(!node.isVisibleToUser() || node.isPassword() || !node.isEnabled())
                continue;
            AccessibilityNodeInfo clickable=clickable(node);
            if(clickable!=null && clickable.isEnabled()) {
                Rect bounds=new Rect();
                clickable.getBoundsInScreen(bounds);
                if(bounds.width()>0 && bounds.height()>0) {
                    if(spotifyTextMatches(SpotifyUiLabels.Target.PLAY,node) &&
                       SpotifyUiLabels.playlistPlayAt(bounds.left,bounds.top,
                           bounds.right,bounds.bottom,frame.left,frame.top,
                           frame.right,frame.bottom,density)) {
                        if(play!=null && !play.equals(bounds))
                            return SpotifyStep.WAIT;
                        play=bounds;
                    }
                    String label=asString(node.getText())+" "+
                        asString(node.getContentDescription())+" "+
                        asString(clickable.getContentDescription());
                    if(SpotifyUiLabels.matches(SpotifyUiLabels.Target.SHUFFLE,label))
                        rightArrows.add(new SpotifyChoice(clickable,label,bounds,100));
                }
            }
            for(int i=0;i<node.getChildCount();i++) {
                AccessibilityNodeInfo child=node.getChild(i);
                if(child!=null)queue.addLast(child);
            }
        }
        if(seen>MAX_SCAN)return SpotifyStep.WAIT;

        int[] position=play==null ? null :
            SpotifyUiLabels.shufflePointFromPlay(play.left,play.top,
                play.right,play.bottom,frame.left,frame.top,frame.right,
                frame.bottom,density);
        if(position==null && named==null &&
           (playlist==SpotifyUiLabels.Target.LIKED_SONGS ||
            playlist==null && spotifyHasTitle(root,SpotifyUiLabels.Target.LIKED_SONGS)) &&
           spotifyLikedHeaderNearTop(root,frame)) {
            // User-confirmed Pixel 9 screenshot, scaled to physical display.
            android.graphics.Point physical=new android.graphics.Point();
            android.view.Display display=getDisplay();
            if(display==null) {
                WindowManager window=(WindowManager)getSystemService(Context.WINDOW_SERVICE);
                if(window!=null)display=window.getDefaultDisplay();
            }
            if(display!=null) {
                display.getRealSize(physical);
                position=SpotifyUiLabels.screenshotShufflePoint(physical.x,physical.y);
            }
        }
        if(position==null)return SpotifyStep.WAIT;
        int x=position[0],y=position[1];
        if(!frame.contains(x,y) ||
           !SpotifyUiLabels.safeRightShufflePoint(x,y,
                frame.left,frame.top,frame.right,frame.bottom))
            return SpotifyStep.WAIT;

        // Check current shuffle state only against an icon actually occupying
        // the correct right-hand point, never a left download label.
        boolean known=false,on=false;
        for(SpotifyChoice arrow:rightArrows) {
            Rect b=arrow.bounds;
            int dx=b.centerX()-x,dy=b.centerY()-y;
            if(Math.abs(dx)>Math.max(30*density,frame.width()*.06f) ||
               Math.abs(dy)>Math.max(25*density,frame.height()*.04f))
                continue;
            if(known && on!=SpotifyUiLabels.shuffleOn(arrow.description,
                arrow.node.isSelected(),arrow.node.isChecked()))
                return SpotifyStep.WAIT;
            on=SpotifyUiLabels.shuffleOn(arrow.description,
                arrow.node.isSelected(),arrow.node.isChecked());
            known=true;
        }
        if(named!=null || playlist!=null) {
            if(known && on==desiredOn)return SpotifyStep.ALREADY;
            // Without an observable OFF state it would be unsafe to toggle
            // an unknown shuffle setting into ON for "без перемішування".
            if(!desiredOn && !known)return SpotifyStep.WAIT;
        }

        if(currentSpotifyRoot()==null)return SpotifyStep.WAIT;
        Rect target=new Rect(x-16,y-16,x+16,y+16);
        FloatingJarvisService.avoidControl(target);
        Path path=new Path();
        path.moveTo(x,y);
        GestureDescription tap=new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(path,0,90))
            .build();
        // dispatchTrackedGesture checks the foreground package after hiding
        // the floating face; it does not send touches to unrelated apps.
        return dispatchTrackedGesture(tap,(ok,detail)->{
            if(!ok && sessionActive)toast("Spotify: натискання перемішування не виконано. "+detail);
        }) ? SpotifyStep.CLICKED : SpotifyStep.WAIT;
    }

    /** Ensure the screenshot fallback applies only to a visible Liked Songs
     * header near the top, never to a scrolled list or another Spotify page. */
    private static boolean spotifyLikedHeaderNearTop(AccessibilityNodeInfo root,Rect frame) {
        ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>();
        queue.add(root);
        int visited=0;
        while(!queue.isEmpty() && ++visited<=MAX_SCAN) {
            AccessibilityNodeInfo node=queue.removeFirst();
            if(!node.isVisibleToUser())continue;
            if(spotifyTextMatches(SpotifyUiLabels.Target.LIKED_SONGS,node)) {
                Rect bounds=new Rect();
                node.getBoundsInScreen(bounds);
                if(bounds.centerY()>frame.top+frame.height()*.04f &&
                   bounds.centerY()<frame.top+frame.height()*.30f)
                    return true;
            }
            for(int i=0;i<node.getChildCount();i++) {
                AccessibilityNodeInfo child=node.getChild(i);
                if(child!=null)queue.addLast(child);
            }
        }
        return false;
    }

    private SpotifyStep tapSpotifyControl(SpotifyUiLabels.Target target,
                                           SpotifyUiLabels.Target playlist) {
        AccessibilityNodeInfo root = currentSpotifyRoot();
        if (root == null) return SpotifyStep.WAIT;
        if (target == SpotifyUiLabels.Target.SHUFFLE)
            return tapSpotifyRightShuffle(root,playlist);
        // Never press shuffle/play on an unrelated screen even within Spotify.
        if ((target == SpotifyUiLabels.Target.SHUFFLE ||
             target == SpotifyUiLabels.Target.PLAY ||
             target == SpotifyUiLabels.Target.RAP_FILTER) &&
             playlist != null && !spotifyHasTitle(root, playlist))
            return SpotifyStep.WAIT;

        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        ArrayList<SpotifyChoice> matches = new ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        Rect rootBounds = new Rect();
        root.getBoundsInScreen(rootBounds);
        int scanned = 0;

        // Play is identified by its OWN top-right toolbar bounds. Shuffle
        // arrows are irrelevant and must never be clicked in this workflow.
        final float displayDensity = getResources().getDisplayMetrics().density;
        while (!queue.isEmpty() && ++scanned <= MAX_SCAN) {
            AccessibilityNodeInfo n = queue.removeFirst();
            if (!n.isVisibleToUser() || n.isPassword()) continue;
            AccessibilityNodeInfo control = clickable(n);
            if (control != null && control.isEnabled()) {
                Rect r = new Rect();
                control.getBoundsInScreen(r);
                if (r.width() > 0 && r.height() > 0) {
                    String name = asString(n.getText()) + " " +
                        asString(n.getContentDescription()) + " " +
                        asString(n.getViewIdResourceName());
                    String selfDesc = asString(n.getContentDescription());
                    if (spotifyTextMatches(target,n)) {
                        // Don't target a control under Spotify's bottom mini player.
                        int score = 100;
                        if (target == SpotifyUiLabels.Target.LIBRARY)
                            score += r.centerY() / 15;
                        if (target == SpotifyUiLabels.Target.PLAY) {
                            // Only the large Play in the UPPER-RIGHT of the
                            // playlist page. Never the bottom mini-player,
                            // a track's small Play, or any arrow icon.
                            if (!SpotifyUiLabels.playlistPlayAt(
                                    r.left,r.top,r.right,r.bottom,
                                    rootBounds.left,rootBounds.top,
                                    rootBounds.right,rootBounds.bottom,
                                    displayDensity))
                                score = 0;
                            else score += Math.min(80,r.width()/3);
                        }
                        String key=r.toShortString();
                        if (score > 0 && seen.add(key) && !ScreenControlCommands.isSensitiveLabel(name))
                            matches.add(new SpotifyChoice(control,selfDesc,r,score));
                    }
                }
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) queue.addLast(child);
            }
        }
        if (scanned > MAX_SCAN) return SpotifyStep.WAIT;
        if (matches.isEmpty()) return SpotifyStep.WAIT;
        // For Library choose the bottom tab, which is the last control.
        SpotifyChoice best = null;
        for (SpotifyChoice choice : matches) {
            if (best == null || choice.score > best.score) best = choice;
            else if (choice.score == best.score && target != SpotifyUiLabels.Target.LIBRARY)
                return SpotifyStep.WAIT;
        }
        if (best == null || !sessionActive) return SpotifyStep.WAIT;
        AccessibilityNodeInfo current = currentSpotifyRoot();
        if (current == null || !best.node.isEnabled() ||
            !best.node.isVisibleToUser() || !best.node.isClickable() ||
            best.node.isPassword()) return SpotifyStep.WAIT;

        // Automatic playlist setup is idempotent; an explicit user click toggles
        // the actual button even when shuffle is already enabled.
        if (target == SpotifyUiLabels.Target.SHUFFLE && playlist != null) {
            if (SpotifyUiLabels.shuffleOn(best.description,
                best.node.isSelected(), best.node.isChecked()))
                return SpotifyStep.ALREADY;
        }
        if ((target == SpotifyUiLabels.Target.LIBRARY ||
             target == SpotifyUiLabels.Target.RAP_FILTER) &&
             (best.node.isSelected() || best.node.isChecked()))
            return SpotifyStep.ALREADY;

        FloatingJarvisService.avoidControl(best.bounds);
        return best.node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            ? SpotifyStep.CLICKED : SpotifyStep.WAIT;
    }

    private String longPressNamed(String label) {
        if (label == null || label.trim().isEmpty() ||
            ScreenControlCommands.isSensitiveLabel(label))
            return "Довге натискання на платежі, видалення або підтвердження заборонене.";
        AccessibilityNodeInfo root = currentExternalRoot();
        if (root == null) return "Відкрий програму, якою хочеш керувати.";
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        ArrayList<AccessibilityNodeInfo> choices = new ArrayList<>();
        ArrayList<ScreenTargetMatcher.Target> targets = new ArrayList<>();
        java.util.HashSet<String> boundsSeen = new java.util.HashSet<>();
        int scanned = 0;
        while (!queue.isEmpty()) {
            if (++scanned > MAX_SCAN) return "Елементів екрана надто багато. Не натискатиму навмання.";
            AccessibilityNodeInfo node = queue.removeFirst();
            if (!node.isVisibleToUser() || node.isPassword()) continue;
            AccessibilityNodeInfo control = longPressable(node);
            if (control != null && control.isEnabled()) {
                Rect rect = new Rect();
                control.getBoundsInScreen(rect);
                if (rect.width() > 0 && rect.height() > 0 &&
                    boundsSeen.add(rect.toShortString())) {
                    choices.add(control);
                    targets.add(new ScreenTargetMatcher.Target(
                        asString(node.getText()),asString(node.getContentDescription()),
                        node.getViewIdResourceName(),asString(control.getText()),
                        asString(control.getContentDescription()),
                        control.getViewIdResourceName()));
                }
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.addLast(child);
            }
        }
        ScreenTargetMatcher.Selection selected = ScreenTargetMatcher.select(label,targets);
        if (selected.ambiguous)
            return "Знайшов декілька відповідних кнопок. Уточни назву.";
        if (selected.index < 0) return "Не знайшов кнопку «" + label +
            "». Без назви або опису не натискатиму навмання.";
        AccessibilityNodeInfo node = choices.get(selected.index);
        if (!sessionActive || currentExternalRoot() == null ||
            !node.isVisibleToUser() || !node.isEnabled() ||
            ScreenTargetMatcher.dangerous(targets.get(selected.index)))
            return "Екран змінився або цю кнопку заборонено натискати.";
        Rect holdBounds = new Rect();
        node.getBoundsInScreen(holdBounds);
        FloatingJarvisService.avoidControl(holdBounds);
        if (node.isLongClickable() &&
            node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK))
            return "Виконав довге натискання на «" + label + "».";
        // Some apps expose only CLICK even when they accept a physical hold.
        // The fallback is permitted solely on one uniquely labelled button.
        Rect area = new Rect();
        node.getBoundsInScreen(area);
        if (area.width() < 12 || area.height() < 12)
            return "Кнопка замала для безпечного довгого натискання.";
        Path finger = new Path();
        finger.moveTo(area.centerX(),area.centerY());
        GestureDescription.Builder gesture = new GestureDescription.Builder();
        gesture.addStroke(new GestureDescription.StrokeDescription(finger, 0, 830));
        return dispatchTrackedGesture(gesture.build()) ?
            "Надіслав довге натискання на «" + label + "»." :
            "Android відхилив довге натискання.";
    }

    /** One stroke from the actual visible screen center to the requested edge. */
    private String swipeCenterToEdge(ScreenControlCommands.Kind kind) {
        AccessibilityNodeInfo root = currentExternalRoot();
        if (root == null) return "Відкрий програму, якою хочеш керувати.";
        Rect bounds = new Rect();
        root.getBoundsInScreen(bounds);
        if (bounds.width() < 110 || bounds.height() < 140)
            return "Не вдалося визначити активну область екрана.";
        float centerX = bounds.exactCenterX(), centerY = bounds.exactCenterY();
        float marginX = Math.max(20f, bounds.width() * .08f);
        float marginY = Math.max(20f, bounds.height() * .08f);
        float endX = centerX, endY = centerY;
        String direction;
        switch (kind) {
            case SWIPE_UP: endY = bounds.top + marginY; direction = "вгору"; break;
            case SWIPE_DOWN: endY = bounds.bottom - marginY; direction = "вниз"; break;
            case SWIPE_LEFT: endX = bounds.left + marginX; direction = "вліво"; break;
            case SWIPE_RIGHT: endX = bounds.right - marginX; direction = "вправо"; break;
            default: return "Напрямок свайпа не визначений.";
        }
        // Revalidate foreground and authorization just before dispatch.
        AccessibilityNodeInfo current = currentExternalRoot();
        if (!sessionActive || current == null ||
            !asString(root.getPackageName()).equals(asString(current.getPackageName())))
            return "Вікно змінилося. Свайп зупинено.";
        Path gesturePath = new Path();
        gesturePath.moveTo(centerX,centerY);
        gesturePath.lineTo(endX,endY);
        GestureDescription.Builder gesture = new GestureDescription.Builder();
        gesture.addStroke(new GestureDescription.StrokeDescription(gesturePath,0,410));
        return dispatchTrackedGesture(gesture.build()) ?
            "Виконую свайп " + direction + " від центра екрана." :
            "Android не дозволив свайп " + direction + ".";
    }

    /**
     * Insert the user's EXACT dictated phrase into the one currently focused
     * editable input in the external app. Never auto-focus another field, touch
     * passwords, use the clipboard, press "send", or forward field data to AI.
     */
    private String writeToFocusedField(String spoken) {
        return writeToFocusedField(spoken, false);
    }

    private String writeToFocusedField(String spoken, boolean macroAuthorized) {
        if (spoken == null || spoken.trim().isEmpty())
            return "Скажи «Напиши», а потім продиктуй слово або речення.";
        if (spoken.length() > ScreenTextInsertion.MAX_DICTATION_LENGTH)
            return "Текст занадто довгий. Продиктуй не більше 1000 символів.";

        AccessibilityNodeInfo root = currentExternalRoot();
        if (root == null)
            return "Відкрий потрібний застосунок, торкнись текстового поля й повтори команду через плаваючий мікрофон.";

        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused == null || !focused.isEditable() || !focused.isFocused())
            return "Немає активного текстового поля. Спочатку торкнися поля для введення тексту.";
        if (!focused.isVisibleToUser() || !focused.isEnabled())
            return "Текстове поле зараз недоступне.";
        if (focused.isPassword() || isSensitiveInputType(focused.getInputType()) ||
            isSensitiveFieldLabel(focused))
            return "У паролі, PIN-коди та інші захищені поля Джарвіс текст не вводить.";

        String rootPackage = asString(root.getPackageName());
        if (rootPackage.isEmpty() || !rootPackage.equals(asString(focused.getPackageName())))
            return "Активне поле належить іншому вікну. Повтори команду.";
        boolean supportsSetText = false;
        for (AccessibilityNodeInfo.AccessibilityAction supported : focused.getActionList()) {
            if (supported.getId() == AccessibilityNodeInfo.ACTION_SET_TEXT) {
                supportsSetText = true;
                break;
            }
        }
        if (!supportsSetText)
            return "Цей застосунок не дозволяє вводити текст через спеціальні можливості.";

        ScreenTextInsertion.Plan plan;
        try {
            plan = ScreenTextInsertion.plan(
                asString(focused.getText()),
                focused.getTextSelectionStart(),
                focused.getTextSelectionEnd(), spoken);
        } catch (IllegalArgumentException error) {
            return error.getMessage();
        }

        // Recheck focus immediately before altering another app's editable node.
        if ((!sessionActive && !macroAuthorized) ||
            !focused.isFocused() || focused.isPassword())
            return "Фокус текстового поля змінився. Нічого не вводив.";
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            plan.result);
        try {
            if (!focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args))
                return "Застосунок відхилив введення тексту. Нічого не надсилав.";
            Bundle selection = new Bundle();
            selection.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT,
                plan.cursor);
            selection.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT,
                plan.cursor);
            // Some widgets do not support cursor repositioning. The text still
            // was written; the default SET_TEXT cursor sits at the end.
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selection);
            return "Текст введено в активне поле. Повідомлення не надсилав.";
        } catch (SecurityException error) {
            return "Android заборонив введення в це поле.";
        }
    }

    private static boolean isSensitiveInputType(int inputType) {
        int variation = inputType & InputType.TYPE_MASK_VARIATION;
        int group = inputType & InputType.TYPE_MASK_CLASS;
        if (group == InputType.TYPE_CLASS_TEXT) {
            return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
        }
        return group == InputType.TYPE_CLASS_NUMBER &&
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
    }

    private static boolean isSensitiveFieldLabel(AccessibilityNodeInfo node) {
        String meta = (
            asString(node.getViewIdResourceName()) + " " +
            asString(node.getContentDescription()) + " " +
            asString(node.getHintText()))
            .toLowerCase(Locale.forLanguageTag("uk-UA"));
        for (String term : new String[]{
            "password", "passwd", "пароль", "пін-код", "pin_code", "pincode",
            "one_time_password", "otp_code", "verification_code", "security_code",
            "cvv", "cvc", "credit_card", "card_number"
        }) {
            if (meta.contains(term)) return true;
        }
        return false;
    }

    private String scroll(boolean down) {
        AccessibilityNodeInfo root = currentExternalRoot();
        if (root == null) return "Відкрий потрібну програму та скористайся плаваючим мікрофоном.";
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        AccessibilityNodeInfo candidate = null;
        int scanned = 0;
        while (!q.isEmpty() && ++scanned <= MAX_SCAN) {
            AccessibilityNodeInfo n = q.removeFirst();
            if (!n.isVisibleToUser() || n.isPassword()) continue;
            if (n.isScrollable()) candidate = n;
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.addLast(child);
            }
        }
        if (candidate != null) {
            int action = down ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD :
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
            if (candidate.performAction(action)) return down ? "Прокрутив вниз." : "Прокрутив вгору.";
        }

        // Only a user-requested, single swipe, never AI-directed coordinates.
        Rect bounds = new Rect();
        root.getBoundsInScreen(bounds);
        if (bounds.width() < 120 || bounds.height() < 240)
            return "Не знайшов область екрана для прокручування.";
        float x = bounds.centerX();
        float startY = down ? bounds.bottom - bounds.height() * 0.23f :
            bounds.top + bounds.height() * 0.25f;
        float endY = down ? bounds.top + bounds.height() * 0.25f :
            bounds.bottom - bounds.height() * 0.23f;
        Path path = new Path();
        path.moveTo(x, startY);
        path.lineTo(x, endY);
        GestureDescription.Builder gesture = new GestureDescription.Builder();
        gesture.addStroke(new GestureDescription.StrokeDescription(path, 0, 420));
        return dispatchTrackedGesture(gesture.build()) ?
            (down ? "Прокручую вниз." : "Прокручую вгору.") :
            "Android не дозволив прокрутити екран.";
    }
}
