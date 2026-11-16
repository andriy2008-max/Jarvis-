# JARVIS — Watch7 persistent session + Spotify App Remote + Mobilerun Portal

**Source branch:** `feature/spotify-portal-ui-v0.1.35`, based on `v0.1.35` and the watch-local-timer fixes.  
**Status:** source changes, Android compilation/contract tests only. **No new APK built.**  
**Limitation:** Pixel 9 and Samsung Galaxy Watch7 must be checked manually. Android can force-stop/kill a process for power or policy reasons. Foreground service does not override deliberate force-stop, Home key, or the screen turning off.

## What changed: Watch7 session

- [x] The activity starts `JarvisWatchSessionService` only after the watch user opens Jarvis.
- [x] `JarvisWatchSessionService` has `START_STICKY` and `android:stopWithTask="false"`.
- [x] Wear `OngoingActivity` notification is now tied to the foreground service instead of `MainActivity.onDestroy`.
- [x] A regular speech reply, API callback, or local timer must NOT call `finish()` or stop the watch service.
- [x] An edge-back gesture in the full-screen Watch7 activity does not automatically exit; the system Home key still works.
- [x] The three-dot menu provides **⏹ Завершити Джарвіса (вручну)** with explicit confirmation; the ongoing notification also has a stop action.
- [x] The foreground service does not use microphone, wake locks or automatic background activity launches.
- [x] An owner-enabled session is restored after watch reboot or app upgrade when Android allows its foreground-service restart.
- [x] Phone-only AI / offline neural voice managers are no longer initialized on the resource-constrained watch.
- [x] `FLAG_KEEP_SCREEN_ON` remains while the watch screen is visible; it is cleared on pause, respecting Android's battery and screen-off policy.
- [ ] Confirm on a real Galaxy Watch7 that the Activity stays visible after voice commands and after TTS.
- [ ] Confirm the ongoing indicator returns to Jarvis after the user presses the Home key.
- [ ] Confirm that force-stop from system settings stops the session (cannot be overridden).

## Watch7 system TTS crash protection (user-observed closure before speaking)

- [x] System TextToSpeech constructor, voice configuration, speech request,
      status lookup, stop and shutdown no longer propagate RuntimeException to the UI.
- [x] A failed or disconnected Android speech binder resets only the voice
      engine, leaving the persistent Watch7 Jarvis session intact.
- [x] Reconnect attempts are bounded to two per run of failures, avoiding
      speech-engine loops and excess battery drain.
- [x] Error diagnostics persist only the operation and exception class —
      no voice transcripts, AI answers or user data.
- [x] The Watch7 menu ⋮ has `🛠 Діагностика голосу Watch7` showing
      current engine status and the previous fault code.
- [x] Wear OS TTS uses assistant/speech audio attributes.
- [ ] **On device**: run `Скажи Привіт` 10 times. The face must remain
      visible for every utterance, including errors and interrupted speech.
- [ ] **On device**: `Увімкни плейлист`, `Таксі до...`, `Скільки часу на таймері`.
      Jarvis must speak responses without returning to the watch face.
- [ ] **On device**: open menu ⋮ → `Перевірити голос Watch7` and inspect
      `Діагностика голосу Watch7` if speech fails.
- [ ] **On device**: force a missing or broken Android TTS voice provider and
      verify that the error appears, reconnection is bounded, and Jarvis stays open.
- [ ] **On device**: if the entire process still closes, capture
      `adb logcat -b crash -d` and logs filtered by `JARVIS_WATCH_TTS`.
      Android native OOM/process kills may not be caught by Java exception guards.
- [ ] **On device**: verify normal voice output after a TTS provider reconnect.

## Watch7 manual tests

- [ ] Say `Скажи Привіт`. The face speaks and remains on the watch.
- [ ] Say `Постав таймер на 1 хвилину`. Timer is local to Watch7; after completion Jarvis remains available. Ring/vibration occurs on watch if permissions permit.
- [ ] Say `Скільки часу на таймері`, `Скасуй таймер`. Both operate on Watch7.
- [ ] Say `Увімкни пісню [назва]`, `Увімкни плейлист [назва]`, `Увімкни реп`. After phone reply Jarvis is still visible.
- [ ] Say `Таксі до [адреса]`. Bolt runs on Pixel, Watch7 stays visible.
- [ ] Trigger AI, notes, Telegram, and phone screen control commands; watch UI does not exit on success or failure.
- [ ] Tap Back gesture once; Jarvis remains visible, with a status message explaining how to exit.
- [ ] Press Home; Android may show the watch face, but the ongoing notification offers a shortcut back.
- [ ] Close and reopen the watch Activity; ongoing service survives while enabled.
- [ ] Reboot the watch and check restoration of the ongoing session without automatic microphone activation.
- [ ] Use menu ⋮ → Завершити Джарвіса → Завершити; watch service and notification stop.
- [ ] Check Wear OS battery/notification permission and device power-saving behavior separately.

## Spotify App Remote / fallback

- [x] The pre-existing official Spotify App Remote SDK is present in Gradle.
- [x] Spotify saved playlist and track aliases can now use direct `spotify:playlist:...` / `spotify:track:...` URIs, not just playlists.
- [x] Direct playback is attempted only with an explicitly owner-configured Spotify Client ID and matching saved URI.
- [x] If authorization fails or no URI exists, the existing Accessibility search is preserved as fallback; no guessing Spotify identifiers.
- [ ] On Pixel, open `🎵 Spotify · прямий запуск`; set up an authorized Spotify developer app Client ID, Android package/SHA-1, redirect URI, and at least one playlist/track alias.
- [ ] Check that App Remote starts a saved track or playlist without an Accessibility tap.
- [ ] Check that voice search for an unknown song still works through the old UI flow.

## Mobilerun Portal / Bolt and Spotify

- [x] Optional local-only Portal HTTP adapter, disabled until the owner enables it.
- [x] Local API token protected in Android Keystore; cleartext restricted to loopback via network security config.
- [x] Reads `/state` and verifies `phone_state.packageName` before choosing a permitted target.
- [x] Only allowlisted actions: Bolt «Рушаймо», «Місце висадки», Spotify «Пошук», «Моя бібліотека».
- [x] Does not automatically confirm payments or taxi bookings.
- [x] Duplicate/ambiguous targets stop; unconfirmed taps are not retried blindly.
- [ ] Install Portal separately on Pixel, grant its Accessibility permission, enable local HTTP 8080, keep cloud/reverse connection disabled.
- [ ] Open `👁 Mobilerun Portal` within Jarvis, enter Portal local API token and enable it, then run the read-only element inspection.
- [ ] Test Bolt navigation and Spotify tabs with Portal enabled. Compare with fallback behavior when Portal is disabled.
- [ ] Test that Portal never taps if another app is in the foreground.

## Automated gate

- `Neural voice contract tests` for parser/command isolation.
- `android_compile` compiles code only; it **does not build an APK**.
- Physical Android navigation/foreground-service behavior is not simulated by those tests.
