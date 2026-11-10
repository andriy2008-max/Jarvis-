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
    private WindowManager windows;
    private View buttonOverlay;
    private SpeechRecognizer recognizer;
    private boolean recording;
    private final Handler main = new Handler(Looper.getMainLooper());
    private JarvisSequenceExecutor sequences;
    private SpotifyPlaybackController spotifyPlayback;

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

    /**
     * Starts a user-requested accessibility session controlled by the separate
     * Quick Settings orb. No second floating microphone is displayed.
     * The user must first grant Accessibility in Android system settings.
     */
    public static String startFromBubble() {
        ScreenControlAccessibilityService service = connected.get();
        if (service == null)
            return "Увімкни Джарвіс — керування екраном у Спеціальних можливостях Android.";
        service.sessionActive = true;
        return "Керування екраном активне в плаваючому Джарвісі.";
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
            case LONG_PRESS:
                return JarvisPhoneMode.isActive() ?
                    s.longPressNamed(action.label) :
                    "Спочатку скажи «Телефон», щоб увімкнути довге натискання.";
            case SWIPE_UP: case SWIPE_DOWN: case SWIPE_LEFT: case SWIPE_RIGHT:
                return JarvisPhoneMode.isActive() ?
                    s.swipeCenterToEdge(action.kind) :
                    "Спочатку скажи «Телефон», щоб керувати свайпами.";
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
        // Deliberately no autonomous screen scanning, action or transmission.
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
                        SpotifyMusicCommands.Kind selection = SpotifyMusicCommands.parse(raw);
                        if (selection != null) {
                            if (spotifyPlayback == null)
                                spotifyPlayback = new SpotifyPlaybackController(
                                    ScreenControlAccessibilityService.this);
                            spotifyPlayback.start(selection, new SpotifyPlaybackController.Listener() {
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

    private SpotifyStep tapSpotifyControl(SpotifyUiLabels.Target target,
                                           SpotifyUiLabels.Target playlist) {
        AccessibilityNodeInfo root = currentSpotifyRoot();
        if (root == null) return SpotifyStep.WAIT;
        // Never press shuffle/play on an unrelated screen even within Spotify.
        if ((target == SpotifyUiLabels.Target.SHUFFLE ||
             target == SpotifyUiLabels.Target.PLAY ||
             target == SpotifyUiLabels.Target.RAP_FILTER) &&
             (playlist == null || !spotifyHasTitle(root, playlist)))
            return SpotifyStep.WAIT;

        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        ArrayList<SpotifyChoice> matches = new ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        Rect rootBounds = new Rect();
        root.getBoundsInScreen(rootBounds);
        int scanned = 0;

        // The playlist's giant green Play and Shuffle are in the same toolbar
        // row; this rejects buttons in the mini-player and on individual songs.
        Rect shuffleBounds = null;
        ArrayList<SpotifyChoice> possibleShuffle = new ArrayList<>();
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
                    if (spotifyTextMatches(SpotifyUiLabels.Target.SHUFFLE, n))
                        possibleShuffle.add(new SpotifyChoice(control,
                            selfDesc + " " + asString(control.getContentDescription()),r,100));
                    if (spotifyTextMatches(target,n)) {
                        // Don't target a control under Spotify's bottom mini player.
                        int score = 100;
                        if (target == SpotifyUiLabels.Target.LIBRARY)
                            score += r.centerY() / 15;
                        if (target == SpotifyUiLabels.Target.PLAY) {
                            if (r.centerX() <= rootBounds.centerX() ||
                                r.centerY() > rootBounds.bottom - Math.max(65,rootBounds.height()/8))
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
        if (target == SpotifyUiLabels.Target.PLAY) {
            // Prefer the visible green playlist Play adjacent to Shuffle.
            for (SpotifyChoice c : possibleShuffle) {
                if (shuffleBounds == null || c.bounds.width() < shuffleBounds.width())
                    shuffleBounds = c.bounds;
            }
            if (shuffleBounds == null) {
                // Without the reference shuffle icon we cannot distinguish the
                // requested large green Play from a mini-player/individual track.
                return SpotifyStep.WAIT;
            }
            final Rect shuffle = shuffleBounds;
            final int minimumPlaySize = Math.round(36f * getResources()
                .getDisplayMetrics().density);
            matches.removeIf(c -> c.bounds.centerX() <= shuffle.centerX() ||
                c.bounds.width() < minimumPlaySize ||
                c.bounds.height() < minimumPlaySize ||
                Math.abs(c.bounds.centerY()-shuffle.centerY()) > Math.max(
                    shuffle.height()*2,rootBounds.height()/7));
        }
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

        if (target == SpotifyUiLabels.Target.SHUFFLE) {
            if (SpotifyUiLabels.shuffleOn(best.description,
                best.node.isSelected(), best.node.isChecked()))
                return SpotifyStep.ALREADY;
        }
        if ((target == SpotifyUiLabels.Target.LIBRARY ||
             target == SpotifyUiLabels.Target.RAP_FILTER) &&
             (best.node.isSelected() || best.node.isChecked()))
            return SpotifyStep.ALREADY;

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
            AccessibilityNodeInfo control = clickable(node);
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
        return dispatchGesture(gesture.build(), null, null) ?
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
        if (!sessionActive || !JarvisPhoneMode.isActive() || current == null ||
            !asString(root.getPackageName()).equals(asString(current.getPackageName())))
            return "Вікно змінилося. Свайп зупинено.";
        Path gesturePath = new Path();
        gesturePath.moveTo(centerX,centerY);
        gesturePath.lineTo(endX,endY);
        GestureDescription.Builder gesture = new GestureDescription.Builder();
        gesture.addStroke(new GestureDescription.StrokeDescription(gesturePath,0,410));
        return dispatchGesture(gesture.build(),null,null) ?
            "Виконую свайп " + direction + " від центра екрана." :
            "Android не дозволив свайп " + direction + ".";
    }

    /**
     * Insert the user's EXACT dictated phrase into the one currently focused
     * editable input in the external app. Never auto-focus another field, touch
     * passwords, use the clipboard, press "send", or forward field data to AI.
     */
    private String writeToFocusedField(String spoken) {
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
        if (!sessionActive || !focused.isFocused() || focused.isPassword())
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
        return dispatchGesture(gesture.build(), null, null) ?
            (down ? "Прокручую вниз." : "Прокручую вгору.") :
            "Android не дозволив прокрутити екран.";
    }
}
