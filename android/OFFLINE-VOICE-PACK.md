# JARVIS offline voices — Android QA status (code 0.1.14)

**Updated 2026-11-10. This is a test-ready implementation, not a physical-phone-certified stable release.**

## Architecture and assets

- One native ONNX Runtime VITS speech engine shared by **Dmytro** and **Lada**, original [robinhad/ukrainian-tts v6.0.0](https://github.com/robinhad/ukrainian-tts/releases/tag/v6.0.0).
- Model graph: `onnx/full/vits.onnx` (145,115,169 bytes); model archive: 145,123,420 bytes. Audio sampling rate 22,050 Hz. Each speaker has a separate 192-dimensional embedding.
- The APK includes the Android inference engine and player, **not** the 145MB voice data. The user explicitly downloads the model once, keeps it in app-private no-backup storage and can generate speech offline afterwards.
- The model's **MIT license** and attribution are included in [THIRD_PARTY_UKRAINIAN_TTS_LICENSE.txt](THIRD_PARTY_UKRAINIAN_TTS_LICENSE.txt).

## Download for Android phone QA

The original verified graph is now published as a **PUBLIC EXPERIMENTAL PRERELEASE**, not as stable:

[Experimental Dmytro + Lada VITS model release](https://github.com/andriy2008-max/Jarvis-/releases/tag/jarvis-offline-voices-v6-experimental)

- APK download registry: [offline-voices.json](offline-voices.json). `ready:true` only indicates *opt-in QA availability* (`channel:android-qa-prerelease`); it **does not** mean physical ARM64 phone certification.
- ZIP SHA-256: `12fdd6521ae864f5eafac2d5a205d32f299ba65229800dafbd647b4a3ff8e0d5`.
- The user opens the offline voice pack settings and presses Download once. The app fetches the registry over HTTPS, validates the complete ZIP SHA-256, checks archive safety, verifies every ONNX file SHA-256 and installs atomically.
- Leave sufficient free space for a 145MB ZIP plus extracted model and temporary files. Allow the download to complete while Jarvis is open. Use Wi-Fi.
- No computer, RunPod or TTS server is required to *generate* speech after installation. **Speech recognition and conversational AI are separate and may still require connectivity.**
- Do not mirror this ZIP or replace its URL without updating the pinned SHA-256.

## Automated verification — actual Android, no mock voice signals

**PASS**: [Android 11 and 15 offline voice inference run](https://github.com/andriy2008-max/Jarvis-/actions/runs/42785778308) with the real graph, speaker embeddings and airplane mode.

Each Android emulator validated the real model hash, synthesized different non-silent audio from both voices, played WAV via `MediaPlayer`, and reloaded the model after same-package debug APK update. These test conditions were on **x86_64 emulator**, not a physical ARM64 phone.

| Measure | Android 11 emulator | Android 15 emulator |
| --- | ---: | ---: |
| Model session load | 560 ms | 1287 ms |
| Process PSS after load | 229,265 KB | 230,547 KB |
| Offline Dmytro + Lada synthesis | PASS | PASS |
| Android WAV decoding | PASS | PASS |
| Model retained on same-package debug update | PASS | PASS |

Additional verified stages:
- [True Python ONNX synthesis for both voices](https://github.com/andriy2008-max/Jarvis-/actions/runs/42779515189).
- [Java ONNX Runtime synthesis for both voices](https://github.com/andriy2008-max/Jarvis-/actions/runs/42780780000).
- [Source/Java regression and Android compilation](https://github.com/andriy2008-max/Jarvis-/actions/runs/42796360374).
- Model SHA, graph shape and metadata inspected independently during [artifact preservation](https://github.com/andriy2008-max/Jarvis-/actions/runs/42780287892).

## Phone QA APK

A **0.1.14 signed QA APK** with `versionCode=24` and application ID `com.andriy.jarvis.stable` was successfully assembled and verified against the original certificate pinned in [RELEASE-CERT-SHA256.txt](RELEASE-CERT-SHA256.txt).

- [Signed QA APK action and downloadable artifact](https://github.com/andriy2008-max/Jarvis-/actions/runs/42799355295).
- Original *stable public* release remains 0.1.8. The signed QA artifact is **not** a new stable GitHub release.
- The QA APK has a **same-certificate stable package ID** to update the existing program rather than creating the `.preview` app.
- GitHub Actions did not allow creating a new public APK release (`403: Resource not accessible by integration`), so the temporary artifact and the direct signed APK supplied in the conversation are the available test downloads.
- A separate Android 11/15 system package-manager test verifies upgrading the real 0.1.8 stable APK to this QA build: [upgrade test](https://github.com/andriy2008-max/Jarvis-/actions/runs/42800938799).

## Screen control: separate Android feature

[Android 11 and 15 real external-app click/scroll integration tests PASSED](https://github.com/andriy2008-max/Jarvis-/actions/runs/42800347706), using a **debug-only** receiver protected by `android.permission.DUMP`. This receiver is NOT included in the signed release APK.

The production service `ScreenControlAccessibilityService` requires the user to manually enable accessibility and explicitly start a session. It finds a unique safe control by its accessible text, `contentDescription` or resource ID and uses local Ukrainian/English intent synonyms, not screenshots or a freely autonomous AI agent. It refuses ambiguous, protected or sensitive actions. Holding the visible floating microphone stops the session.

Unlabeled graphic-only icons without any accessibility metadata still require **explicit-consent screen capture plus visual AI**; this is not currently implemented.

## Still NOT complete

- Physical ARM64 Android QA: performance, battery, audio quality, background playback and pronunciation of difficult Ukrainian words/number forms.
- User verification that signed 0.1.14 upgrades the exact already-installed APK and preserves their settings/data, and that the voice pack downloads reliably on their own network.
- Manual accessibility permission and microphone recognition behavior on a physical device; system prompts and restrictions differ by OS/vendor.
- Wear OS RAM/performance testing: the 145MB model may be too large for some watches.
- Stable rollout only after explicit phone QA approval, final version bump and comprehensive regression tests (including background timer).

## Manifest summary

```json
{
  "format": "jarvis-espnet-onnx-v1",
  "architecture": "espnet-exported-vits",
  "sample_rate": 22050,
  "voices": ["dmytro", "lada"],
  "synthesis": {"model_type": "VITS", "graph": "onnx/full/vits.onnx"},
  "smoke_verified": true,
  "offline_runtime_verified_on_android": false
}
```

The last field remains false in the archive, because it was exported *before* Android emulator testing and is not intended to claim physical device certification. Runtime validation is recorded in CI above. The `UkrainianVoiceText` Android preprocessor handles ordinary numbers and basic stress; it is **not** the full upstream linguistic dictionary.

The original `model.pth` was about 446MB and exported to the single VITS graph; the old source-tree Tacotron2 config should not be used to infer the v6 checkpoint's architecture.
