package com.andriy.jarvis;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * DEBUG-BUILD-ONLY instrumentation bridge for adb shell testing.
 * Protected by android.permission.DUMP (system/shell only), never merged into
 * signed stable/release APK. Allows exercising the REAL accessibility service
 * without AndroidJUnitRunner terminating its host app process.
 */
public final class ScreenControlDebugReceiver extends BroadcastReceiver {
    private static final String TAG = "JARVIS_SCREEN_QA";
    private static final String ACTION = "com.andriy.jarvis.preview.DEBUG_SCREEN_CONTROL";

    @Override public void onReceive(Context context, Intent intent) {
        // This class is compiled only from src/debug and absent from release.
        if (intent == null || !ACTION.equals(intent.getAction())) return;
        String operation = intent.getStringExtra("op");
        if (operation == null) return;
        try {
            String result;
            switch (operation) {
                case "status":
                    Log.i(TAG, "qa_op=status qa_connected=" +
                        ScreenControlAccessibilityService.isConnected() +
                        " qa_active=" + ScreenControlAccessibilityService.isSessionActive());
                    return;
                case "start":
                    result = ScreenControlAccessibilityService.start();
                    Log.i(TAG, "qa_op=start qa_active=" +
                        ScreenControlAccessibilityService.isSessionActive() + " result=" + result);
                    return;
                case "stop":
                    result = ScreenControlAccessibilityService.stop();
                    Log.i(TAG, "qa_op=stop qa_active=" +
                        ScreenControlAccessibilityService.isSessionActive() + " result=" + result);
                    return;
                case "click":
                case "scroll_down":
                case "blocked":
                    ScreenControlCommands.Action action;
                    if (operation.equals("scroll_down")) {
                        action = ScreenControlCommands.parse("прокрути вниз");
                    } else if (operation.equals("blocked")) {
                        action = ScreenControlCommands.parse("натисни підтвердити");
                    } else {
                        String phrase = intent.getStringExtra("phrase");
                        action = ScreenControlCommands.parse(phrase);
                    }
                    result = ScreenControlAccessibilityService.perform(action);
                    Log.i(TAG, "qa_op=" + operation +
                        " qa_success=" + result.startsWith("Знайшов потрібну кнопку й натиснув.") +
                        " qa_denied=" + result.contains("не виконуватиму") +
                        " qa_scrolled=" + result.startsWith("Прокру") +
                        " result=" + result);
                    return;
                default:
                    Log.w(TAG, "Unknown debug QA command");
            }
        } catch (Exception error) {
            Log.e(TAG, "qa_op=" + operation + " qa_exception=true", error);
        }
    }
}
