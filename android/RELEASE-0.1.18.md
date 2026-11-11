# JARVIS 0.1.18 (28)

Full Android + Wear OS APK, stable package and original signing certificate. Includes all main-branch changes since the 0.1.17 release (aee6ad8).

## Included changes

- Unified Phone and screen-control mode, entered by “телефон” or automatically for supported phone actions. Commands spoken to the watch in Phone mode run on the companion phone.
- Tap the same phone/face icon or shake the open watch app to start continuous listening. Phone-mode phrases are submitted after two seconds of silence; listening resumes after a task. No separate floating microphone.
- Reference-shaped cyan pixel phone; ear and cupped hand while listening; clockwise pixel loading indicator while a task runs; check only after confirmed success, X for failure/cancellation. Shared appearance in full screen and floating face.
- Scoped Spotify phone automation (including rap/Ukrainian music), watch forwarding, completion acknowledgement and cancellation; automatic Spotify sessions restore the previous face, manually entered mode stays active.
- Android speech service error 11 reconnect, stale callback protection and preservation of already captured words.
- Best-effort wired/USB/Bluetooth headset microphone routing and safe fallback on disconnection.
- Retains previous sequential commands with waits, screen gestures, voice options, recognized words, lip sync, timers and other changes already present in 0.1.17.

## Release gate

The publishing workflow must pass Java behavior contracts, AI/voice-server/export tests, Android compilation, pinned certificate/package/version checks, an in-place update from the signed 0.1.17 APK, emulator launch, and real accessibility click/search/type/scroll/stop checks. Debug test bridges must be absent from the release APK.

## Hardware checks still needed

Paired watch/phone delivery, physical shake sensitivity, headset selection by the installed speech service, actual two-second speech endpointing, Spotify UI on the user's account, and background timer audio need testing on the real devices. Watch shake is supported while the watch app is open. Offline voice models download separately. AI Worker deployment is separate from APK installation.
