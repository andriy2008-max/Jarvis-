# Watch7 / Pixel reliability fixes — 2026-11-14

Changes after ac766a510051e6209ad0163ff690582c752c9513:

- Watch replies honor voiceEnabled in manual Phone mode. Recognition is retired before TTS; stale callbacks cannot restart capture. Continuous listening resumes through the existing loop after speech ends.
- Successful screen, Spotify, enter/exit and cancellation replies can be spoken, as well as errors.
- Phone-owned voice macros run through both Wear dispatch routes. Results carry actual completion status; duplicate recognition gets an explicit response. Remote macros stop after 120 seconds, before the 150-second watch timeout. MacroDroid broadcast delivery is not reported as completed execution.
- Spotify cancellation uses a new correlated request/reply. Sending a message alone is not success.
- Screen-command timeout is 150 seconds, exceeding six 18-second sequence steps. Heartbeat/cancellation remain bounded to 12 seconds.
- Launching an app waits up to 10 seconds for the target foreground package. This requires the user's Accessibility permission. A locked/noninteractive phone returns an explicit unlock message; no unlock is attempted. Screen workflows stop if the phone locks.
- AI connection check performs an authenticated model request with empty history; it neither clears nor contaminates the conversation. This small request may use API credits.
- Unknown control verbs are not silently treated as conversational AI questions.
- Financial save confirmation is conservative: closing the form is NOT evidence of a saved ledger row. The result says verification is required and instructs the user not to repeat the command before checking history. No second save is attempted.

## Verification

Run `.github/workflows/voice-tests.yml`: Java contracts, mocked AI worker tests, Python contracts and Android Java compilation. Protocol regressions cover the six-step timing budget, remote macro deadline and cancellation timeout.

Compilation and contract tests do not prove real Wear Data Layer, ASR, TTS, locked-phone behavior, or third-party API authorization. Physical Watch7/Pixel tests remain necessary. No new APK is built by these checks; stable signing/package/version are unchanged.

## Remaining device-dependent limits

Money Manager has no implemented ledger readback API. Actual record existence and uniqueness require checking history manually. Generic UI heuristics must not be presented as proof of saving. Real-time API tests need the settings already stored on the user's Pixel. Android background restrictions are reported rather than bypassed.

## Watch7 returning to the watch face after voice commands — 2026-11-16

- The Wear `MainActivity` never calls `finish()` at command completion; a return to the Samsung watch face can be an OS idle transition rather than a crash.
- An `OngoingActivity` now posts when the user opens Jarvis on Watch7 and is cancelled only when its Activity is actually destroyed (not merely paused for ambient mode). It provides a one-tap return entry and lets Wear OS preserve the active user session during idle transitions. It does not forcibly relaunch Jarvis after an intentional Home press, keep the microphone on continuously, or bypass battery restrictions.
- Android 13+ notification permission is requested once on the watch. If denied, grant Notifications for Jarvis from the watch's app settings and relaunch. A notification posted by the app is distinct from the phone's background companion service.
- Test each of `Скажи привіт`, `Увімкни реп`, `Виклич таксі`, note/AI replies and voice alarms. After each response verify that the Jarvis face stays visible when the screen is interactive; let the device dim and check the active-session return icon. Press Home deliberately and verify the watch respects that action.
- If the app *immediately* disappears with the screen still bright, collect `adb logcat -b crash -d` and logs tagged `JARVIS_WATCH_LIFECYCLE`. An app crash is not fixed by OngoingActivity and must be diagnosed separately.
- Physical Galaxy Watch7 tests and signed APK are not part of this source-code change. The system may still return to the face depending on power saving and Samsung firmware policies.
