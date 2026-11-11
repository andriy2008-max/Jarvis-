package com.andriy.jarvis;

import android.content.Context;
import java.util.UUID;

/** Explicit and scoped screen-control sessions; never grants Accessibility. */
public final class JarvisPhoneModeManager {
    private JarvisPhoneModeManager() {}

    /** A user voice command takes priority over an in-progress automatic task.
     * If the user claims control, the automatic task must not close the mode.
     */
    private static String automaticOwner;

    public static final class Lease {
        private final String id;
        private final long feedback;
        public final boolean granted;
        public final String message;
        private Lease(String id, boolean granted, String message) {
            this.id = id;
            this.feedback = granted ? JarvisControlFeedback.begin() : 0L;
            this.granted = granted;
            this.message = message;
        }
    }

    public static synchronized boolean automatic() { return automaticOwner != null; }

    public static void showPhoneIcon(Context context) {
        if (FloatingJarvisService.isShowing() ||
            !android.provider.Settings.canDrawOverlays(context) ||
            context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        try {
            android.content.Intent show = new android.content.Intent(context, FloatingJarvisService.class);
            show.setAction(FloatingJarvisService.ACTION_SHOW);
            if (android.os.Build.VERSION.SDK_INT >= 26) context.startForegroundService(show);
            else context.startService(show);
        } catch (RuntimeException ignored) { /* Android can require a foreground launch. */ }
    }

    public static synchronized String enter(Context context, boolean useFloatingMic) {
        if (!supported(context))
            return "Ця конфігурація Джарвіса не підтримує керування екраном.";
        if (!ScreenControlAccessibilityService.isConnected())
            return consentNeeded();
        // An explicit "Телефон" supersedes temporary automated control.
        automaticOwner = null;
        showPhoneIcon(context);
        if (JarvisPhoneMode.isActive() &&
            ScreenControlAccessibilityService.isSessionActive())
            return "Режим «Телефон» уже активний. Скажи «Джарвіс» для виходу.";
        String status = ScreenControlAccessibilityService.startFromBubble();
        if (!ScreenControlAccessibilityService.isSessionActive()) return status;
        JarvisPhoneMode.setActive(true);
        return "Режим «Телефон» активний. Скажи «Натисни», «Зажми» або «Свайпни». " +
            "Натисни телефон, щоб слухати безперервно. Команда — після 2 с тиші. Для виходу скажи «Джарвіс».";
    }

    /** Run a single, user-requested action such as "увімкни реп". Consent
     * must already exist; this NEVER opens Android Accessibility settings
     * or silently grants the system-level permission.
     */
    public static synchronized Lease acquireForCommand(Context context) {
        if (!supported(context))
            return new Lease(null,false,"Керування екраном недоступне на цьому пристрої.");
        if (!ScreenControlAccessibilityService.isConnected())
            return new Lease(null,false,consentNeeded());
        if (automaticOwner != null)
            return new Lease(null,false,"Інша команда вже керує екраном.");
        if (ScreenControlAccessibilityService.isSessionActive()) {
            // Borrow a pre-existing manually enabled session, never close it.
            return new Lease(null,true,"Користуюся вже активним керуванням екраном.");
        }
        String result = ScreenControlAccessibilityService.startFromBubble();
        if (!ScreenControlAccessibilityService.isSessionActive())
            return new Lease(null,false,result);
        showPhoneIcon(context);
        String token = UUID.randomUUID().toString();
        automaticOwner = token;
        JarvisPhoneMode.setActive(true);
        return new Lease(token,true,"Тимчасовий режим «Телефон» активовано.");
    }

    public static void perform(Context context, ScreenControlCommands.Action action,
                               ScreenControlAccessibilityService.Completion completion) {
        Lease lease = acquireForCommand(context);
        if (!lease.granted) { completion.finished(false,lease.message); return; }
        ScreenControlAccessibilityService.perform(action,(ok,detail) -> {
            releaseForCommand(lease,ok);
            completion.finished(ok,detail);
        });
    }

    public static void releaseForCommand(Lease lease) {
        releaseForCommand(lease, false);
    }

    public static synchronized void releaseForCommand(Lease lease, boolean success) {
        if (lease == null) return;
        JarvisControlFeedback.finish(lease.feedback,success,android.os.SystemClock.uptimeMillis());
        if (lease.id == null) return;
        if (!lease.id.equals(automaticOwner)) return; // user has claimed control
        automaticOwner = null;
        ScreenControlAccessibilityService.stop();
        JarvisPhoneMode.setActive(false);
    }

    public static synchronized String exit() {
        JarvisControlFeedback.clear();
        automaticOwner = null;
        if (!JarvisPhoneMode.isActive()) return "Звичайний режим Джарвіса вже активний.";
        ScreenControlAccessibilityService.stop();
        JarvisPhoneMode.setActive(false);
        return "Вийшов із режиму «Телефон». Звичайний Джарвіс активний.";
    }

    private static boolean supported(Context context) {
        return context != null && !context.getPackageManager().hasSystemFeature(
            android.content.pm.PackageManager.FEATURE_WATCH) &&
            context.getResources().getBoolean(R.bool.screen_control_available);
    }

    private static String consentNeeded() {
        return "На телефоні потрібно вручну ввімкнути «Джарвіс — керування екраном» " +
            "в налаштуваннях Android → Спеціальні можливості. Без цього дозвіл не надається.";
    }
}
