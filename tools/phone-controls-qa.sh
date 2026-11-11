#!/usr/bin/env bash
set -euo pipefail
adb wait-for-device
adb shell id
adb install --no-streaming -r android/app/build/outputs/apk/debug/app-debug.apk
adb install --no-streaming -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm grant com.andriy.jarvis.preview android.permission.RECORD_AUDIO
adb shell appops set com.andriy.jarvis.preview SYSTEM_ALERT_WINDOW allow
adb logcat -c
adb shell am start -n com.andriy.jarvis.preview/com.andriy.jarvis.MainActivity
adb shell appops set com.andriy.jarvis.preview ACCESS_RESTRICTED_SETTINGS allow || true
adb shell settings put secure enabled_accessibility_services com.andriy.jarvis.preview/com.andriy.jarvis.ScreenControlAccessibilityService
adb shell settings put secure accessibility_enabled 1
sleep 2
adb shell am broadcast -n com.andriy.jarvis.preview/com.andriy.jarvis.ScreenControlDebugReceiver -a com.andriy.jarvis.preview.DEBUG_SCREEN_CONTROL --es op show_overlay
sleep 2
adb shell am start -n com.andriy.jarvis.preview.test/com.andriy.jarvis.ScreenControlTestTarget
for i in $(seq 1 20); do adb shell am broadcast -n com.andriy.jarvis.preview/com.andriy.jarvis.ScreenControlDebugReceiver -a com.andriy.jarvis.preview.DEBUG_SCREEN_CONTROL --es op status >/dev/null; if adb logcat -d -s JARVIS_SCREEN_QA:I '*:S' | grep -Fq 'qa_op=status qa_connected=true'; then break; fi; sleep 2; done
adb logcat -d -s JARVIS_SCREEN_QA:I '*:S' | grep -F 'qa_op=status qa_connected=true'
adb shell am broadcast -n com.andriy.jarvis.preview/com.andriy.jarvis.ScreenControlDebugReceiver -a com.andriy.jarvis.preview.DEBUG_SCREEN_CONTROL --es op start
sleep 1
adb shell am broadcast -n com.andriy.jarvis.preview/com.andriy.jarvis.ScreenControlDebugReceiver -a com.andriy.jarvis.preview.DEBUG_SCREEN_CONTROL --es op phone
adb shell "am broadcast -n com.andriy.jarvis.preview/com.andriy.jarvis.ScreenControlDebugReceiver -a com.andriy.jarvis.preview.DEBUG_SCREEN_CONTROL --es op gesture --es phrase 'зажми кнопку тестова кнопка'"
sleep 1
adb shell "am broadcast -n com.andriy.jarvis.preview/com.andriy.jarvis.ScreenControlDebugReceiver -a com.andriy.jarvis.preview.DEBUG_SCREEN_CONTROL --es op click --es phrase 'натисни перехрещені стрілочки'"
sleep 1
sleep 1
adb shell "am broadcast -n com.andriy.jarvis.preview/com.andriy.jarvis.ScreenControlDebugReceiver -a com.andriy.jarvis.preview.DEBUG_SCREEN_CONTROL --es op click --es phrase 'натисни кнопку тестова кнопка'"
sleep 1
adb shell "am broadcast -n com.andriy.jarvis.preview/com.andriy.jarvis.ScreenControlDebugReceiver -a com.andriy.jarvis.preview.DEBUG_SCREEN_CONTROL --es op click --es phrase 'відкрий пошук'"
sleep 1
adb shell "am broadcast -n com.andriy.jarvis.preview/com.andriy.jarvis.ScreenControlDebugReceiver -a com.andriy.jarvis.preview.DEBUG_SCREEN_CONTROL --es op type --es phrase 'Напиши Привіт, Тест! 42'"
sleep 1
adb shell am broadcast -n com.andriy.jarvis.preview/com.andriy.jarvis.ScreenControlDebugReceiver -a com.andriy.jarvis.preview.DEBUG_SCREEN_CONTROL --es op scroll_down
adb shell am broadcast -n com.andriy.jarvis.preview/com.andriy.jarvis.ScreenControlDebugReceiver -a com.andriy.jarvis.preview.DEBUG_SCREEN_CONTROL --es op blocked
adb shell "am broadcast -n com.andriy.jarvis.preview/com.andriy.jarvis.ScreenControlDebugReceiver -a com.andriy.jarvis.preview.DEBUG_SCREEN_CONTROL --es op gesture --es phrase 'свайпни вліво'"
sleep 2
adb shell "am broadcast -n com.andriy.jarvis.preview/com.andriy.jarvis.ScreenControlDebugReceiver -a com.andriy.jarvis.preview.DEBUG_SCREEN_CONTROL --es op gesture --es phrase 'свайпни вправо'"
sleep 2
adb shell "am broadcast -n com.andriy.jarvis.preview/com.andriy.jarvis.ScreenControlDebugReceiver -a com.andriy.jarvis.preview.DEBUG_SCREEN_CONTROL --es op gesture --es phrase 'свайпни вгору'"
sleep 2
adb shell "am broadcast -n com.andriy.jarvis.preview/com.andriy.jarvis.ScreenControlDebugReceiver -a com.andriy.jarvis.preview.DEBUG_SCREEN_CONTROL --es op gesture --es phrase 'свайпни вниз'"
sleep 2
adb shell "am broadcast -n com.andriy.jarvis.preview/com.andriy.jarvis.ScreenControlDebugReceiver -a com.andriy.jarvis.preview.DEBUG_SCREEN_CONTROL --es op gesture --es phrase 'прокрути вгору'"
sleep 2
adb shell am broadcast -n com.andriy.jarvis.preview/com.andriy.jarvis.ScreenControlDebugReceiver -a com.andriy.jarvis.preview.DEBUG_SCREEN_CONTROL --es op transcript
sleep 1
mkdir -p qa-results
adb exec-out screencap -p > qa-results/phone-transcript.png
adb shell am broadcast -n com.andriy.jarvis.preview/com.andriy.jarvis.ScreenControlDebugReceiver -a com.andriy.jarvis.preview.DEBUG_SCREEN_CONTROL --es op clear_transcript
adb shell "am broadcast -n com.andriy.jarvis.preview/com.andriy.jarvis.ScreenControlDebugReceiver -a com.andriy.jarvis.preview.DEBUG_SCREEN_CONTROL --es op gesture --es phrase 'назад'"
sleep 1
adb shell am broadcast -n com.andriy.jarvis.preview/com.andriy.jarvis.ScreenControlDebugReceiver -a com.andriy.jarvis.preview.DEBUG_SCREEN_CONTROL --es op stop
adb logcat -d -s JARVIS_SCREEN_QA:I JARVIS_INPUT_QA:I JARVIS_FLOATING_QA:I '*:S' > /tmp/screen-control-test.log
cat /tmp/screen-control-test.log
grep -Fq 'qa_op=start qa_active=true' /tmp/screen-control-test.log
test "$(grep -Fc 'qa_op=click qa_success=true' /tmp/screen-control-test.log)" -eq 3
grep -F 'qa_op=type ' /tmp/screen-control-test.log | grep -Fq 'qa_typed=true'
grep -Fq 'JARVIS_INPUT_QA: qa_exact_text=true' /tmp/screen-control-test.log
grep -Fq 'qa_op=scroll_down qa_success=false qa_denied=false qa_scrolled=true' /tmp/screen-control-test.log
grep -Fq 'qa_op=blocked qa_success=false qa_denied=true' /tmp/screen-control-test.log
grep -Fq 'qa_op=stop qa_active=false' /tmp/screen-control-test.log

grep -Fq 'qa_phone=true qa_control=true' /tmp/screen-control-test.log
grep -Fq 'qa_long_press=true' /tmp/screen-control-test.log
grep -Fq 'qa_toolbar_click=Enable shuffle' /tmp/screen-control-test.log
! grep -Eq 'qa_toolbar_click=(Play|Repeat|Back)' /tmp/screen-control-test.log
for direction in left right up down; do
  grep -Fq "qa_touch_swipe=$direction" /tmp/screen-control-test.log
done
grep -Fq 'qa_overlay_gesture_hidden=true' /tmp/screen-control-test.log
test "$(grep -Fc 'qa_gesture_done=true qa_phone=true qa_control=true' /tmp/screen-control-test.log)" -eq 7
! grep -Eq 'qa_gesture_done=false|qa_exception=true' /tmp/screen-control-test.log
cp /tmp/screen-control-test.log qa-results/
echo 'PASS: unified phone session, exact shuffle, four actual swipes, long press, scroll, back, type, overlay avoidance'
