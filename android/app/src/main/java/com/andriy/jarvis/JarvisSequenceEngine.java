package com.andriy.jarvis;

/**
 * Platform-independent state machine. One explicitly requested action at a time;
 * it never relies on a fixed sleep to guess that a screen has opened.
 */
public final class JarvisSequenceEngine {
    public enum Attempt { WAIT, OK, DENY }
    public enum Progress { WAITING, COMPLETED, FAILED }

    public interface Environment {
        /** A recognized launcher app's real package name, or null on failure. */
        String launchApp(String name);
        /** Current visible, permitted third-party application package or null. */
        String visiblePackage();
        /** Unique safe clickable button, no auto-clicking during ambiguity. */
        Attempt click(String label);
        /** Exact focused, editable, non-password field in the expected package. */
        boolean focusedInputReady(String expectedPackage);
        Attempt type(String text);
        boolean setMediaVolume(int percent);
        boolean hasScreenControlPermission();
        String failureReason();
    }

    public static final int STEP_TIMEOUT_MS = 18000;
    public static final int INPUT_TIMEOUT_MS = 12000;

    private final JarvisCommandSequence.Plan plan;
    private final Environment env;
    private int index;
    private long stepStart = -1L;
    private boolean appLaunchIssued;
    private String expectedPackage;
    private Progress progress = Progress.WAITING;
    private String result = "";

    public JarvisSequenceEngine(JarvisCommandSequence.Plan plan, Environment env) {
        if (plan == null || !plan.valid() || env == null)
            throw new IllegalArgumentException("Expected a valid explicit user command plan");
        this.plan = plan;
        this.env = env;
    }

    public Progress progress() { return progress; }
    public String result() { return result; }
    public int completedSteps() { return index; }
    public int totalSteps() { return plan.steps.size(); }

    public void cancel() {
        if (progress != Progress.WAITING) return;
        progress = Progress.FAILED;
        result = "Послідовність скасовано. Наступні команди не виконуватимуться.";
    }

    private Progress fail(String reason) {
        progress = Progress.FAILED;
        result = "Зупинився на кроці " + (index + 1) + "/" + plan.steps.size() +
            ". " + reason + " Наступні кроки не виконано.";
        return progress;
    }

    private Progress advance() {
        index++;
        stepStart = -1L;
        appLaunchIssued = false;
        if (index >= plan.steps.size()) {
            progress = Progress.COMPLETED;
            result = "Готово. Послідовно виконав усі " + index + " команди.";
        }
        return progress;
    }

    public Progress tick(long elapsedRealtimeMs) {
        if (progress != Progress.WAITING) return progress;
        if (!env.hasScreenControlPermission())
            return fail("Режим керування екраном вимкнено.");

        JarvisCommandSequence.Step step = plan.steps.get(index);
        if (stepStart < 0) stepStart = elapsedRealtimeMs;
        int timeout = step.kind == JarvisCommandSequence.Kind.TYPE_TEXT
            ? INPUT_TIMEOUT_MS : STEP_TIMEOUT_MS;
        if (elapsedRealtimeMs - stepStart > timeout)
            return fail(step.kind == JarvisCommandSequence.Kind.OPEN_APP
                ? "Застосунок не відкрив потрібний екран за час очікування."
                : step.kind == JarvisCommandSequence.Kind.CLICK
                    ? "Не знайшов потрібну кнопку на готовому екрані."
                    : "Не з'явилося активне текстове поле.");

        switch (step.kind) {
            case OPEN_APP:
                if (!appLaunchIssued) {
                    expectedPackage = env.launchApp(step.text);
                    if (expectedPackage == null || expectedPackage.isEmpty())
                        return fail(orReason("Не зміг відкрити застосунок."));
                    appLaunchIssued = true;
                    return Progress.WAITING;
                }
                if (!expectedPackage.equals(env.visiblePackage()))
                    return Progress.WAITING;
                return advance();
            case CLICK:
                // A splash screen, a different app, or hidden system screen is
                // never a valid target for the next click.
                String active = env.visiblePackage();
                if (active == null || active.isEmpty() ||
                    expectedPackage != null && !expectedPackage.equals(active))
                    return Progress.WAITING;
                if (expectedPackage == null) expectedPackage = active;
                Attempt clicked = env.click(step.text);
                if (clicked == Attempt.WAIT) return Progress.WAITING;
                if (clicked == Attempt.DENY)
                    return fail(orReason("Не вдалося безпечно натиснути кнопку."));
                return advance();
            case TYPE_TEXT:
                String visible = env.visiblePackage();
                if (visible == null || visible.isEmpty() ||
                    expectedPackage != null && !expectedPackage.equals(visible))
                    return Progress.WAITING;
                if (expectedPackage == null) expectedPackage = visible;
                // Do not type until a real editable input becomes focused
                // in the app where the preceding click succeeded.
                if (!env.focusedInputReady(expectedPackage))
                    return Progress.WAITING;
                Attempt typed = env.type(step.text);
                if (typed == Attempt.WAIT) return Progress.WAITING;
                if (typed == Attempt.DENY)
                    return fail(orReason("Поле відхилило введення тексту."));
                return advance();
            case MEDIA_VOLUME:
                if (!env.setMediaVolume(step.volume))
                    return fail(orReason("Не вдалося змінити медіагучність."));
                return advance();
            default:
                return fail("Непідтримувана дія.");
        }
    }

    private String orReason(String fallback) {
        String reason = env.failureReason();
        return reason == null || reason.trim().isEmpty() ? fallback : reason;
    }
}
