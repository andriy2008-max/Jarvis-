package com.andriy.jarvis;

import android.content.Context;

/** User's explicit, reversible Phone mode; never silently enables Accessibility. */
public final class JarvisPhoneModeManager {
    private JarvisPhoneModeManager() {}

    public static String enter(Context context, boolean useFloatingMic) {
        if (context == null || !context.getResources().getBoolean(
            R.bool.screen_control_available))
            return "Ця конфігурація Джарвіса не підтримує керування екраном.";
        if (!ScreenControlAccessibilityService.isConnected())
            return "Спочатку вручну ввімкни «Джарвіс — керування екраном» " +
                "у Спеціальних можливостях Android. Потім повтори «Телефон».";
        if (JarvisPhoneMode.isActive() &&
            ScreenControlAccessibilityService.isSessionActive())
            return "Режим «Телефон» уже активний. Скажи «Джарвіс» для виходу.";
        String status = useFloatingMic ?
            ScreenControlAccessibilityService.startFromBubble() :
            ScreenControlAccessibilityService.start();
        if (!ScreenControlAccessibilityService.isSessionActive()) return status;
        JarvisPhoneMode.setActive(true);
        return "Режим «Телефон» активний. Скажи «Натисни…», «Зажми…» " +
            "або «Свайпни вгору, вниз, вліво чи вправо». " +
            "Для виходу скажи «Джарвіс» або натисни на 8-бітний телефон.";
    }

    public static String exit() {
        if (!JarvisPhoneMode.isActive()) return "Звичайний режим Джарвіса вже активний.";
        ScreenControlAccessibilityService.stop();
        JarvisPhoneMode.setActive(false);
        return "Вийшов із режиму «Телефон». Звичайний Джарвіс активний.";
    }
}
