# Phone background companion

The phone starts JarvisBackgroundService when MainActivity opens, unless the user
has disabled background mode. This is a connectedDevice foreground service for
the Wear Data Layer companion. It is independent of the microphone overlay.
Swiping Recents leaves it running (stopWithTask=false); START_STICKY accepts null
restart intents after process reclamation. Boot after first unlock and package
replacement restore an already activated, enabled phone installation. Wear OS
never starts this phone-only service.

The service posts a low-importance ongoing notification and never polls, opens
an overlay, captures microphone audio, or holds an idle wake lock. Received
commands hold a partial wake lock for at most 180 seconds and release it when the
reply send completes (including failure). Closing MainActivity no longer cancels
the shared FinanceManagerController that can belong to a watch command.

Phone UI: «Фонова робота» displays actual service status, enables/disables it, and
opens Android app settings. Users can allow notifications and choose unrestricted
battery use. The watch's phone connection report includes service status.

Limits: Android may reclaim a process; sticky restart is best effort, not a
promise of continuous uptime. Force-stop requires reopening the app. The service
does not bypass lock screens, battery/network restrictions, or grant permissions.
It does not automatically restart a microphone or watch UI. No APK is built here.

Validation: tools/background-service/test_lifecycle.py compiles the production
Service and boot Receiver against a deterministic Android API shell. It checks
Recents removal, null-intent recovery, boot/update, explicit disable, watch
exclusion, foreground denial, and API 28 fallback. Manifest checks and Android
compilation run in voice-tests.yml. Actual Pixel/Watch7 battery and lifecycle
behavior still needs hardware testing.

Android references:
https://developer.android.com/develop/background-work/services/fgs/service-types
https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
