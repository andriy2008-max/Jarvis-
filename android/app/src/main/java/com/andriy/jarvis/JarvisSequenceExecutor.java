package com.andriy.jarvis;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;

/**
 * Executes explicitly requested steps in order on the main thread.
 * Polls the ACTUAL Accessibility window state, never assumes a fixed delay
 * means YouTube/Search/editable input has loaded. Stops on the first failure.
 */
public final class JarvisSequenceExecutor {
    public interface Listener {
        void onProgress(int step, int total, String description);
        void onFinished(boolean success, String summary);
    }

    private static final long POLL_INTERVAL_MS = 400L;
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private JarvisSequenceEngine engine;
    private Listener listener;
    private int lastAnnounced = -1;
    private String lastError;
    private boolean running;
    private JarvisPhoneModeManager.Lease phoneLease;
    private final Runnable poll = this::tick;

    public JarvisSequenceExecutor(Context context) {
        this.context = context;
    }

    public boolean isRunning() { return running; }

    public void start(JarvisCommandSequence.Plan plan, Listener callback) {
        if (running) {
            if (callback != null) callback.onFinished(false,
                "Попередня послідовність ще виконується. Спочатку скасуй її.");
            return;
        }
        if (plan == null || !plan.valid()) {
            if (callback != null) callback.onFinished(false,
                plan == null ? "Не розпізнав послідовність." : plan.error);
            return;
        }
        phoneLease = JarvisPhoneModeManager.acquireForCommand(context);
        if (!phoneLease.granted) {
            if (callback != null) callback.onFinished(false, phoneLease.message);
            phoneLease = null;
            return;
        }
        this.listener = callback;
        this.lastAnnounced = -1;
        this.lastError = null;
        this.planSteps = plan.steps;
        this.engine = new JarvisSequenceEngine(plan, new AndroidEnvironment());
        this.running = true;
        handler.post(poll);
    }

    public void cancel() {
        handler.removeCallbacks(poll);
        if (!running) return;
        JarvisSequenceEngine current = engine;
        if (current != null) current.cancel();
        finish(false, current == null ? "Послідовність скасовано." : current.result());
    }

    private void tick() {
        if (!running || engine == null) return;
        if (!JarvisPhoneScreenGuard.available(context)) {
            finish(false,JarvisPhoneScreenGuard.MESSAGE); return;
        }
        int step = engine.completedSteps();
        if (step != lastAnnounced) {
            lastAnnounced = step;
            if (listener != null) {
                String label;
                switch (getStepKind(step)) {
                    case OPEN_APP: label = "Відкриваю застосунок і чекаю готового екрана"; break;
                    case CLICK: label = "Чекаю потрібну кнопку й натискаю"; break;
                    case TYPE_TEXT: label = "Чекаю активного поля й вводжу текст"; break;
                    case MEDIA_VOLUME: label = "Змінюю медіагучність"; break;
                    default: label = "Виконую команду"; break;
                }
                listener.onProgress(step + 1, engine.totalSteps(), label);
            }
        }
        JarvisSequenceEngine.Progress progress = engine.tick(SystemClock.elapsedRealtime());
        if (progress == JarvisSequenceEngine.Progress.WAITING) {
            handler.postDelayed(poll, POLL_INTERVAL_MS);
        } else {
            finish(progress == JarvisSequenceEngine.Progress.COMPLETED, engine.result());
        }
    }

    // The runner does not need to handle or reveal the dictated text in a
    // progress notification.
    private JarvisCommandSequence.Kind getStepKind(int index) {
        return planSteps.get(index).kind;
    }
    private List<JarvisCommandSequence.Step> planSteps;

    private void finish(boolean success, String summary) {
        handler.removeCallbacks(poll);
        if (!running) return;
        running = false;
        Listener cb = listener;
        listener = null;
        engine = null;
        planSteps = null;
        lastError = null;
        JarvisPhoneModeManager.releaseForCommand(phoneLease, success);
        phoneLease = null;
        if (cb != null) cb.onFinished(success, summary);
    }

    private final class AndroidEnvironment implements JarvisSequenceEngine.Environment {
        @Override public boolean hasScreenControlPermission() {
            return ScreenControlAccessibilityService.isSessionActive();
        }

        @Override public String visiblePackage() {
            return ScreenControlAccessibilityService.visibleExternalPackage();
        }

        @Override public String launchApp(String spoken) {
            try {
                Intent query = new Intent(Intent.ACTION_MAIN);
                query.addCategory(Intent.CATEGORY_LAUNCHER);
                PackageManager manager = context.getPackageManager();
                List<ResolveInfo> results = manager.queryIntentActivities(query, 0);
                List<AppNameMatcher.App> launchers = new ArrayList<>();
                for (ResolveInfo info : results) {
                    if (info.activityInfo == null ||
                        info.activityInfo.packageName == null ||
                        info.activityInfo.name == null) continue;
                    CharSequence title = info.loadLabel(manager);
                    if (title != null) launchers.add(new AppNameMatcher.App(
                        info.activityInfo.packageName,
                        info.activityInfo.name, title.toString()));
                    if (info.activityInfo.applicationInfo != null) {
                        CharSequence appTitle =
                            manager.getApplicationLabel(info.activityInfo.applicationInfo);
                        if (appTitle != null) launchers.add(new AppNameMatcher.App(
                            info.activityInfo.packageName,
                            info.activityInfo.name, appTitle.toString()));
                    }
                }
                AppNameMatcher.Result matched = AppNameMatcher.match(spoken, launchers);
                if (matched.ambiguous()) {
                    lastError = "Назва застосунку неоднозначна. Назви його точніше.";
                    return null;
                }
                if (!matched.found()) {
                    lastError = "Не знайшов застосунку «" + spoken + "» на телефоні.";
                    return null;
                }
                Intent launch = new Intent(Intent.ACTION_MAIN);
                launch.addCategory(Intent.CATEGORY_LAUNCHER);
                launch.setClassName(matched.app.packageName, matched.app.activityName);
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(launch);
                return matched.app.packageName;
            } catch (Exception error) {
                lastError = "Android не дозволив відкрити застосунок.";
                return null;
            }
        }

        @Override public JarvisSequenceEngine.Attempt click(String label) {
            ScreenControlCommands.Action command =
                ScreenControlCommands.parse("натисни кнопку " + label);
            String result = ScreenControlAccessibilityService.perform(command);
            if (result.startsWith("Знайшов потрібну кнопку й натиснув."))
                return JarvisSequenceEngine.Attempt.OK;
            if (result.startsWith("Не зміг однозначно визначити кнопку") ||
                result.startsWith("Відкрий потрібну програму")) {
                return JarvisSequenceEngine.Attempt.WAIT;
            }
            lastError = result;
            return JarvisSequenceEngine.Attempt.DENY;
        }

        @Override public boolean focusedInputReady(String expectedPackage) {
            return ScreenControlAccessibilityService.focusedEditableIn(expectedPackage);
        }

        @Override public JarvisSequenceEngine.Attempt type(String text) {
            ScreenControlCommands.Action command =
                ScreenControlCommands.parse("напиши " + text);
            String result = ScreenControlAccessibilityService.perform(command);
            if (result.startsWith("Текст введено в активне поле."))
                return JarvisSequenceEngine.Attempt.OK;
            lastError = result;
            return JarvisSequenceEngine.Attempt.DENY;
        }

        @Override public boolean setMediaVolume(int percentage) {
            if (percentage < 0 || percentage > 100) {
                lastError = "Гучність повинна бути від 0 до 100 відсотків.";
                return false;
            }
            AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audio == null) {
                lastError = "Керування звуком недоступне.";
                return false;
            }
            try {
                int maximum = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int value = Math.round(maximum * percentage / 100f);
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, value,
                    AudioManager.FLAG_SHOW_UI);
                return true;
            } catch (SecurityException error) {
                lastError = "Android заборонив змінити гучність.";
                return false;
            }
        }

        @Override public String failureReason() { return lastError; }
    }
}
