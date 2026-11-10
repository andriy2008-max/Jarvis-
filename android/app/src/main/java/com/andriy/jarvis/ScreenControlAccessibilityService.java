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
    private WindowManager windows;
    private View buttonOverlay;
    private SpeechRecognizer recognizer;
    private boolean recording;
    private final Handler main = new Handler(Looper.getMainLooper());

    public static boolean isConnected() { return connected.get() != null; }
    public static boolean isSessionActive() {
        ScreenControlAccessibilityService s = connected.get();
        return s != null && s.sessionActive;
    }

    public static String start() {
        ScreenControlAccessibilityService s = connected.get();
        if (s == null)
            return "Спочатку ввімкни службу «Джарвіс — керування екраном» у Спеціальних можливостях Android.";
        s.sessionActive = true;
        s.showOverlay();
        return s.sessionActive
            ? "Керування екраном увімкнено. Відкрий потрібну програму й натисни плаваючий мікрофон Джарвіса. Довго натисни на нього, щоб зупинити."
            : "Не вдалося показати плаваючу кнопку. Режим вимкнено.";
    }

    public static String stop() {
        ScreenControlAccessibilityService s = connected.get();
        if (s != null) s.stopSession();
        return "Керування екраном зупинено.";
    }

    public static String perform(ScreenControlCommands.Action action) {
        ScreenControlAccessibilityService s = connected.get();
        if (s == null)
            return "Служба керування екраном не активована в налаштуваннях Android.";
        if (action == null)
            return "Невідома команда керування.";
        if (!s.sessionActive)
            return "Спочатку скажи: увімкни керування екраном.";
        switch (action.kind) {
            case CLICK: return s.clickNamed(action.label);
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
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        // Deliberately no autonomous screen scanning, action or transmission.
    }

    @Override public void onInterrupt() {
        stopListening();
    }

    private void stopSession() {
        sessionActive = false;
        stopListening();
        if (buttonOverlay != null && windows != null) {
            try { windows.removeView(buttonOverlay); } catch (Exception ignored) {}
            buttonOverlay = null;
        }
    }

    @Override public void onDestroy() {
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
    }

    private void recordOneCommand() {
        if (!sessionActive) return;
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
                        if (sessionActive) toast("Не розпізнав команду. Натисни мікрофон ще раз.");
                    }
                    @Override public void onResults(Bundle results) {
                        recording = false;
                        if (!sessionActive) return;
                        ArrayList<String> phrases =
                            results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (phrases == null || phrases.isEmpty()) {
                            toast("Не почув команду.");
                            return;
                        }
                        String spoken = CommandPatterns.normalize(phrases.get(0));
                        ScreenControlCommands.Action action = ScreenControlCommands.parse(spoken);
                        if (action == null) {
                            toast("Скажи «Відкрий пошук», «Знайди чати», «Натисни лупу», «Прокрути вниз» або «Назад».");
                        } else if (action.kind == ScreenControlCommands.Kind.DISABLE) {
                            toast(stop());
                        } else if (action.kind == ScreenControlCommands.Kind.STATUS) {
                            toast(isSessionActive() ? "Керування активне." : "Керування вимкнене.");
                        } else if (action.kind == ScreenControlCommands.Kind.CLICK ||
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
            recording = true;
            recognizer.startListening(speech);
        } catch (Exception error) {
            recording = false;
            toast("Не вдалося розпочати розпізнавання. Перевір дозвіл мікрофона.");
        }
    }

    private AccessibilityNodeInfo currentExternalRoot() {
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
            return "Відкрий потрібну програму й подай команду через плаваючий мікрофон Джарвіса. Системними дозволами я не керую.";

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
        boolean clicked = chosen.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        return clicked ? "Знайшов потрібну кнопку й натиснув." :
            "Кнопку знайдено, але застосунок не дозволив її натиснути.";
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
        return dispatchGesture(gesture.build(), null, null) ?
            (down ? "Прокручую вниз." : "Прокручую вгору.") :
            "Android не дозволив прокрутити екран.";
    }
}
