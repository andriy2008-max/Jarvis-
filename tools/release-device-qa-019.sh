#!/usr/bin/env bash
set -euo pipefail
adb wait-for-device
# boot_completed may precede PackageManager readiness.
for i in $(seq 1 30); do
  if adb shell pm path android | grep -q '^package:'; then break; fi
  sleep 2
done
adb shell pm path android | grep '^package:'
adb install --no-streaming "$RUNNER_TEMP/jarvis-old/JARVIS-0.1.18-stable.apk"
adb install --no-streaming -r android/dist/JARVIS-0.1.19-stable.apk
adb shell dumpsys package com.andriy.jarvis.stable | grep -F 'versionCode=29'
adb logcat -c
adb shell am start -W -n com.andriy.jarvis.stable/com.andriy.jarvis.MainActivity
sleep 7
adb shell pidof com.andriy.jarvis.stable
adb logcat -d -s AndroidRuntime:E '*:S' > /tmp/jarvis-crashes.txt
if grep -Eq 'FATAL EXCEPTION|Process: com.andriy.jarvis.stable' /tmp/jarvis-crashes.txt; then
  cat /tmp/jarvis-crashes.txt
  exit 1
fi
echo 'PASS: 0.1.18 upgraded in-place to 0.1.19 and launched'
adb shell am force-stop com.andriy.jarvis.stable
adb wait-for-device
adb shell id
adb install --no-streaming -r android/app/build/outputs/apk/debug/app-debug.apk
adb install --no-streaming -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb logcat -c
adb shell am start -n com.andriy.jarvis.preview/com.andriy.jarvis.MainActivity
sleep 1
adb shell appops set com.andriy.jarvis.preview ACCESS_RESTRICTED_SETTINGS allow || true
adb shell settings put secure enabled_accessibility_services com.andriy.jarvis.preview/com.andriy.jarvis.ScreenControlAccessibilityService
adb shell settings put secure accessibility_enabled 1
adb shell am start -n com.andriy.jarvis.preview.test/com.andriy.jarvis.ScreenControlTestTarget
sleep 1
for i in $(seq 1 20); do adb shell am broadcast -n com.andriy.jarvis.preview/com.andriy.jarvis.ScreenControlDebugReceiver -a com.andriy.jarvis.preview.DEBUG_SCREEN_CONTROL --es op status >/dev/null; if adb logcat -d -s JARVIS_SCREEN_QA:I '*:S' | grep -Fq 'qa_op=status qa_connected=true'; then break; fi; sleep 2; done
adb logcat -d -s JARVIS_SCREEN_QA:I '*:S' | grep -F 'qa_op=status qa_connected=true'
adb shell am broadcast -n com.andriy.jarvis.preview/com.andriy.jarvis.ScreenControlDebugReceiver -a com.andriy.jarvis.preview.DEBUG_SCREEN_CONTROL --es op start
sleep 1
adb shell "am broadcast -n com.andriy.jarvis.preview/com.andriy.jarvis.ScreenControlDebugReceiver -a com.andriy.jarvis.preview.DEBUG_SCREEN_CONTROL --es op click --es phrase 'натисни кнопку тестова кнопка'"
sleep 1
adb shell "am broadcast -n com.andriy.jarvis.preview/com.andriy.jarvis.ScreenControlDebugReceiver -a com.andriy.jarvis.preview.DEBUG_SCREEN_CONTROL --es op click --es phrase 'відкрий пошук'"
sleep 1
adb shell "am broadcast -n com.andriy.jarvis.preview/com.andriy.jarvis.ScreenControlDebugReceiver -a com.andriy.jarvis.preview.DEBUG_SCREEN_CONTROL --es op type --es phrase 'Напиши Привіт, Тест! 42'"
sleep 1
adb shell am broadcast -n com.andriy.jarvis.preview/com.andriy.jarvis.ScreenControlDebugReceiver -a com.andriy.jarvis.preview.DEBUG_SCREEN_CONTROL --es op scroll_down
sleep 1
adb shell am broadcast -n com.andriy.jarvis.preview/com.andriy.jarvis.ScreenControlDebugReceiver -a com.andriy.jarvis.preview.DEBUG_SCREEN_CONTROL --es op blocked
sleep 1
adb shell am broadcast -n com.andriy.jarvis.preview/com.andriy.jarvis.ScreenControlDebugReceiver -a com.andriy.jarvis.preview.DEBUG_SCREEN_CONTROL --es op stop
sleep 1
adb logcat -d -s JARVIS_SCREEN_QA:I JARVIS_INPUT_QA:I '*:S' > /tmp/screen-control-test.log
cat /tmp/screen-control-test.log
grep -Fq 'qa_op=start qa_active=true' /tmp/screen-control-test.log
test "$(grep -Fc 'qa_op=click qa_success=true' /tmp/screen-control-test.log)" -eq 2
grep -F 'qa_op=type ' /tmp/screen-control-test.log | grep -Fq 'qa_typed=true'
grep -Fq 'JARVIS_INPUT_QA: qa_exact_text=true' /tmp/screen-control-test.log
grep -Fq 'qa_op=scroll_down qa_success=false qa_denied=false qa_scrolled=true' /tmp/screen-control-test.log
grep -Fq 'qa_op=blocked qa_success=false qa_denied=true' /tmp/screen-control-test.log
grep -Fq 'qa_op=stop qa_active=false' /tmp/screen-control-test.log
echo "PASS: real click, semantic search, exact Unicode input, scroll, denial and stop"
