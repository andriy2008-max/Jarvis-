package com.andriy.jarvis;

import static org.junit.Assert.*;

import android.content.ComponentName;
import android.content.Intent;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * End-to-end user-triggered node click and scrolling against a separate test APK.
 * The AccessibilityService is enabled by CI using Android secure settings.
 */
@RunWith(AndroidJUnit4.class)
public final class ScreenControlInstrumentedTest {
    @Test public void explicitlyNamedClickAndStopOnExternalScreen() throws Exception {
        // Android binds enabled accessibility services asynchronously, especially
        // after first installation on a fresh emulator.
        for (int attempt = 0;
             attempt < 50 && !ScreenControlAccessibilityService.isConnected();
             attempt++) SystemClock.sleep(200);
        assertTrue("AccessibilityService never connected after 10 seconds; inspect dumpsys accessibility",
            ScreenControlAccessibilityService.isConnected());
        Intent external = new Intent();
        external.setComponent(new ComponentName(
            "com.andriy.jarvis.preview.test",
            "com.andriy.jarvis.ScreenControlTestTarget"));
        external.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        InstrumentationRegistry.getInstrumentation().getTargetContext().startActivity(external);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        SystemClock.sleep(500);

        assertFalse("Must be off until explicitly enabled",
            ScreenControlAccessibilityService.isSessionActive());
        String notYet = ScreenControlAccessibilityService.perform(
            ScreenControlCommands.parse("натисни кнопку тестова кнопка"));
        assertTrue("Must reject action before user opt-in", notYet.contains("Спочатку"));

        final String[] enabled = new String[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
            () -> enabled[0] = ScreenControlAccessibilityService.start());
        assertTrue("Cannot show accessibility overlay: " + enabled[0],
            enabled[0].contains("увімкнено"));
        assertTrue(ScreenControlAccessibilityService.isSessionActive());
        final String[] clicked = new String[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
            clicked[0] = ScreenControlAccessibilityService.perform(
                ScreenControlCommands.parse("натисни кнопку тестова кнопка")));
        String done = clicked[0];
        assertEquals("Натиснув «тестова кнопка».", done);
        SystemClock.sleep(350);
        String changed = ScreenControlAccessibilityService.perform(
            ScreenControlCommands.parse("натисни кнопку тестова кнопка"));
        assertTrue("Test button must have changed after actual click: " + changed,
            changed.contains("Не знайшов"));

        // One deliberate scroll of the separate test app.
        String scroll = ScreenControlAccessibilityService.perform(
            ScreenControlCommands.parse("прокрути вниз"));
        assertTrue("Expected one scroll action: " + scroll, scroll.startsWith("Прокру"));

        // The dangerous generic confirmation is always rejected.
        String denied = ScreenControlAccessibilityService.perform(
            ScreenControlCommands.parse("натисни підтвердити"));
        assertTrue(denied.contains("не натискатиму"));

        InstrumentationRegistry.getInstrumentation().runOnMainSync(
            () -> ScreenControlAccessibilityService.stop());
        assertFalse(ScreenControlAccessibilityService.isSessionActive());
    }
}
