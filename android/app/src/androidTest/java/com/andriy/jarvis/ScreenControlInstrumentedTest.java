package com.andriy.jarvis;

import static org.junit.Assert.*;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Intent;
import android.os.SystemClock;
import android.os.ParcelFileDescriptor;

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
        // Instrumentation attaches UiAutomation; without this explicit flag it
        // SUPPRESSES ordinary accessibility services, including Jarvis.
        // This is an instrumentation-only permission, NOT production code.
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        UiAutomation automation = instrumentation.getUiAutomation(
            UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        assertNotNull("Test UiAutomation could not connect", automation);
        // Repair a service binding that was interrupted when AndroidJUnitRunner
        // replaced the app process. Only this test turns settings on via shell.
        try (ParcelFileDescriptor off = automation.executeShellCommand(
                 "settings put secure accessibility_enabled 0")) {
            assertNotNull(off);
        }
        SystemClock.sleep(500);
        try (ParcelFileDescriptor on = automation.executeShellCommand(
                 "settings put secure enabled_accessibility_services " +
                 "com.andriy.jarvis.preview/com.andriy.jarvis.ScreenControlAccessibilityService")) {
            assertNotNull(on);
        }
        try (ParcelFileDescriptor enabled = automation.executeShellCommand(
                 "settings put secure accessibility_enabled 1")) {
            assertNotNull(enabled);
        }
        for (int attempt = 0;
             attempt < 90 && !ScreenControlAccessibilityService.isConnected();
             attempt++) SystemClock.sleep(200);
        assertTrue("AccessibilityService never connected after UiAutomation non-suppressing setup; inspect dumpsys accessibility",
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
        assertEquals("Знайшов потрібну кнопку й натиснув.", done);
        SystemClock.sleep(350);
        String changed = ScreenControlAccessibilityService.perform(
            ScreenControlCommands.parse("натисни кнопку тестова кнопка"));
        assertTrue("Test button must have changed after actual click: " + changed,
            changed.contains("Не зміг"));

        // Semantic matching: Ukrainian request, icon-only button, English contentDescription.
        final String[] semanticClick = new String[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
            semanticClick[0] = ScreenControlAccessibilityService.perform(
                ScreenControlCommands.parse("відкрий пошук")));
        assertEquals("Знайшов потрібну кнопку й натиснув.", semanticClick[0]);

        // One deliberate scroll of the separate test app.
        String scroll = ScreenControlAccessibilityService.perform(
            ScreenControlCommands.parse("прокрути вниз"));
        assertTrue("Expected one scroll action: " + scroll, scroll.startsWith("Прокру"));

        // The dangerous generic confirmation is always rejected.
        String denied = ScreenControlAccessibilityService.perform(
            ScreenControlCommands.parse("натисни підтвердити"));
        assertTrue(denied.contains("не виконуватиму"));

        InstrumentationRegistry.getInstrumentation().runOnMainSync(
            () -> ScreenControlAccessibilityService.stop());
        assertFalse(ScreenControlAccessibilityService.isSessionActive());
    }
}
