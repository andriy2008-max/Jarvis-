# JARVIS offline voices — technical status for version 0.1.13

## Required behavior

The small Android APK contains ONNX Runtime and the native inference code, but **does not contain the model weights**. After the user installs a verified, shared Dmytro/Lada VITS model pack once, all TTS generation can occur on the phone without Internet, PC, cloud, or paid API. The pack is kept in app-private no-backup files and remains on disk through in-place updates. Speech recognition / free-form AI chat have separate connectivity requirements.

## Android emulator verification — PASSED on 2026-11-10

[GitHub Actions run 42785778308](https://github.com/andriy2008-max/Jarvis-/actions/runs/42785778308) completed **successfully on both Android API 30 (Android 11) and API 35 (Android 15)** using the actual 145 MB VITS graph and both 192-element xvectors.

Each emulator was placed into airplane mode with Wi-Fi/mobile data disabled. The instrumentation tests validated model file checksums **on Android**, then ran both original voices through the production `OfflineOnnxSynthesizer`, verified their two WAVs contain different, non-silent audio, and used Android `MediaPlayer` to decode/play them. A second test re-installed the **same** debug app ID via `adb install -r` and verified the existing model remained readable and could synthesize again without a network connection. The upgrade test verifies data persistence, **not** the production stable signing certificate.

| Measure (emulator, not physical phone) | Android 11 API 30 | Android 15 API 35 |
|---|---:|---:|
| VITS session load | 560 ms | 1287 ms |
| Process PSS after model load | 229,265 KB | 230,547 KB |
| Both original voices offline | Passed | Passed |
| Android MediaPlayer WAV handling | Passed | Passed |
| Model survives same-package app update | Passed | Passed |

The test-only workflow is [`.github/workflows/android-offline-instrumentation.yml`](../.github/workflows/android-offline-instrumentation.yml) and uses temporary debug/test APKs; **it did not build or publish a user-facing stable APK**. A prior, simpler Android 11 verification also passed in [run 42784881537](https://github.com/andriy2008-max/Jarvis-/actions/runs/42784881537).

**Still open:** ARM64 physical-phone validation (speaker quality, memory and CPU on actual hardware); stable APK install/upgrade signed with the original keystore; and exercising an actual HTTPS one-time download via a user-accessible official pack release. The existing binary is **draft**, not publicly downloadable. The production one-tap registry stays `ready:false` until the user-facing rollout is deliberately enabled.

## What has been proven

1. Real **robinhad/ukrainian-tts v6.0.0** weights were downloaded and exported by `espnet_onnx`. This checkpoint exports a **single VITS graph** (not Tacotron2, despite an older source-tree config).
2. Both named speaker xvectors synthesized a non-empty WAV through **Python ONNX Runtime** in [the completed export workflow](https://github.com/andriy2008-max/Jarvis-/actions/runs/42779515189).
3. The actual ZIP was checked with SHA-256 and its graph inspected. The ONNX inputs are `text`: int64[dynamic], `spembs`: float32[192]; the output `wav` is float32[dynamic]. Both voices use the **same** 145,115,169-byte graph. Sample rate 22,050 Hz. Tokens count: 50.
4. **Java ONNX Runtime 1.20.0** loaded the real ONNX graph and synthesized independently with both xvectors in [the Java test run](https://github.com/andriy2008-max/Jarvis-/actions/runs/42780780000). These are **Linux runner** measurements, not Android-device measurements.
5. The verified 145 MB ZIP is preserved in a **draft GitHub Release** `jarvis-offline-voices-v6-experimental` so the temporary Actions artifact can expire without losing the data. SHA-256: `12fdd6521ae864f5eafac2d5a205d32f299ba65229800dafbd647b4a3ff8e0d5`. **Do not publish until Android testing.**
6. Android source has the single-graph VITS inference implementation `OfflineOnnxSynthesizer`, cached sessions and WAV playback `OfflineSpeechClient`, a checked ZIP installer `OfflineVoicePackManager`, and an opt-in fallback path: offline → private HTTPS TTS server → system Android TTS. Android 11/15 **emulator** inference is now verified; physical ARM64 Android device testing remains.

## Android release gate — incomplete

- Verify ARM64 ONNX Runtime can load and execute this exact graph on a **physical** phone; x86_64 Android 11/15 emulator already passed.
- Listen to both speakers on a real phone and verify pronunciation, stress, numbers and punctuation. Airplane-mode synthesis passed on emulators.
- Measure physical-phone load latency, speech quality, RAM, cancellation, background behavior and battery usage; preliminary emulator PSS and decoding are recorded above.
- Confirm **stable signed** in-place upgrade keeps installed voice pack and original application ID/signing key (debug update preservation already passed).
- Re-test Wear OS separately (model may exceed available RAM or run too slowly).
- The one-tap [official pack registry](offline-voices.json) is intentionally `ready:false`. The draft release has no public direct download URL. The app must not claim voices are ready to install before these checks pass.
- No user-facing APK is being assembled yet, per user request to add more changes first.

## Pack format

ZIP entries:
- `manifest.json`
- `onnx/full/vits.onnx`

Manifest critical fields:

```json
{
  "format": "jarvis-espnet-onnx-v1",
  "upstream": "robinhad/ukrainian-tts@v6.0.0",
  "architecture": "espnet-exported-vits",
  "sample_rate": 22050,
  "voices": ["dmytro", "lada"],
  "tokens": ["<blank>", "<unk>", "+", "<space>"],
  "spembs": {"dmytro": ["192 float numbers"], "lada": ["192 float numbers"]},
  "synthesis": {"model_type": "VITS", "graph": "onnx/full/vits.onnx"},
  "files": [{
    "path": "onnx/full/vits.onnx",
    "bytes": 145115169,
    "sha256": "64 hex characters"
  }],
  "smoke_verified": true,
  "offline_runtime_verified_on_android": false
}
```

Example is abbreviated: the real metadata contains 50 tokens, numeric voice vectors, and actual file hash.

The Android installer validates HTTPS URL and outer ZIP SHA-256, prevents ZIP traversal and size bombs, and verifies every listed ONNX file and required inputs. The `UkrainianVoiceText` preprocessor implements deterministic number wording, explicit acute accents and common word stress. **It is not a replacement for the upstream complete morphological stress dictionary**, so quality work remains.

## Reproducing export

Use `.github/workflows/export-uktts-onnx.yml` for the tested Python 3.10 and legacy ESPnet dependency matrix. Do not use a random latest SciPy, ONNX or PyTorch install. Python export entry point: `tools/voice-export/export_voice_pack.py`.

The verified binary lives in a **draft** release and is not yet available for one-tap in-app download. Do not set `ready:true` without the Android release gate. The original checkpoint `model.pth` is ~446 MB but the exported VITS graph is 145 MB.

## Signing

The stable application ID remains `com.andriy.jarvis.stable`, with a persistent, user-owned keystore. Preview and stable builds must not be confused. Only sign and publish a user-facing APK after the remaining requested changes and tests.
