# Jarvis floating face from Android Quick Settings

## What was added

- `JarvisQuickTileService`: real Android Quick Settings tile labeled **Джарвіс**. Clicking it does **not** launch `MainActivity`. It launches a short-lived transparent permission activity compatible with Android 14/15 `PendingIntent` tile API.
- `JarvisBubbleLauncherActivity`: requests two user-controlled permissions only if missing: microphone and **Display over other apps** (`SYSTEM_ALERT_WINDOW`). No root and no Accessibility grant are required for the bubble itself.
- `FloatingJarvisService`: microphone-type foreground service with a visible ongoing notification and **Закрити** action. Uses `TYPE_APPLICATION_OVERLAY` to draw a small **112dp round 8-bit blue face** while keeping the current app visible. No automatic microphone recording when the bubble appears.
- `JarvisOrbView`: reusable lightweight pixel-art face with idle, listening, thinking and speaking animations, matching the main Jarvis visual concept.
- Short tap: start a **user-triggered** recognition session. Second tap: finish and process current utterance. Long press: close and release the microphone, speech runtime and overlay.
- Commands in the orb: **«Скажи [текст]»**, timer start/cancel/status, time/date, media play/pause/next/previous, volume, switch Dmytro/Lada and AI chat if a private HTTPS AI connection was previously configured. Speech replies use installed Dmytro/Lada offline model when available, then private TTS server, then Android TTS. The floating orb has its **own** recognition session and does not claim that every command in MainActivity is available.
- In **full** editions with Accessibility previously manually granted, the user may say «Увімкни керування екраном» to enable one-step screen commands controlled through the **same orb**, without another microphone bubble. The user's dedicated screen permission is never enabled silently. In **Lite**, screen navigation still remains disabled; the orb itself is available.
- No persistent microphone recording; notifications show the foreground service while the bubble is on-screen. A microphone indicator can appear only when the user initiates recognition. The user can stop at any time by long-pressing the orb or pressing **Закрити** in its notification.

## Add the Quick Settings button

1. Open Jarvis and tap **«Додати Джарвіса в швидкі налаштування»**. Android 13+ may offer a one-tap system dialog to add the tile.
2. If the system does not show the dialog, swipe down twice, press the edit/pencil control and drag **Джарвіс** into your active Quick Settings tiles.
3. Tap the new Quick Settings tile. Android will ask for a one-time **Display over other apps** permission and, if missing, microphone permission. These permissions are required only when the user explicitly wants the bubble.
4. The shade collapses and a small round face appears over the current application. Tap to speak; tap again to finish, long-press to close.

A shortcut **«Показати плаваюче обличчя»** is also available in the main phone UI so users can test the bubble without editing Quick Settings.

## Important limitations

- Android version/vendor background-service and overlay policies vary; test on physical phones. Google Play Protect may still classify an APK with powerful permissions; do not advise disabling Play Protect.
- The floating bubble's separate `SpeechRecognizer` depends on available Android language packs or connectivity; offline voice **playback** does not guarantee offline speech **recognition**.
- A persistent low-importance notification shows while the bubble is visible, allowing the user to close the service.
- If a feature needs the full settings UI (e.g. adding AI credentials, first voice-model download, microphone permission for camera), open the main app **manually**; the Quick Settings tile never opens it for routine commands.
- **Android 11 and Android 15 Quick Settings integration PASSED** in [run 42814267846](https://github.com/andriy2008-max/Jarvis-/actions/runs/42814267846): real Quick Settings tile callback, microphone foreground service, visible floating face and a real tap on the orb were verified on both emulators. These automated tests do not check actual spoken audio or speech recognition quality on a physical phone.
- **Version 0.1.15 / versionCode 25** has original-certificate signed **release-QA** and **Lite-QA** APK artifacts in [run 42814492405](https://github.com/andriy2008-max/Jarvis-/actions/runs/42814492405). Both have the original `com.andriy.jarvis.stable` package ID and matching pinned original certificate. Lite excludes the full AccessibilityService, but includes the floating Quick Settings orb. These are test artifacts expiring after seven days, **not** public stable releases.
- Android 17 / Pixel 9 physical-device behavior still requires voluntary user validation. Do not disable Play Protect merely to install the full build.
