# JARVIS offline voices — technical status for version 0.1.13

## Required behavior

The small Android APK contains ONNX Runtime and the native inference code, but **does not contain the model weights**. After the user installs a verified, shared Dmytro/Lada VITS model pack once, all TTS generation can occur on the phone without Internet, PC, cloud, or paid API. The pack is kept in app-private no-backup files and remains on disk through in-place updates. Speech recognition / free-form AI chat have separate connectivity requirements.

## What has been proven

1. Real **robinhad/ukrainian-tts v6.0.0** weights were downloaded and exported by `espnet_onnx`. This checkpoint exports a **single VITS graph** (not Tacotron2, despite an older source-tree config).
2. Both named speaker xvectors synthesized a non-empty WAV through **Python ONNX Runtime** in [the completed export workflow](https://github.com/andriy2008-max/Jarvis-/actions/runs/42779515189).
3. The actual ZIP was checked with SHA-256 and its graph inspected. The ONNX inputs are `text`: int64[dynamic], `spembs`: float32[192]; the output `wav` is float32[dynamic]. Both voices use the **same** 145,115,169-byte graph. Sample rate 22,050 Hz. Tokens count: 50.
4. **Java ONNX Runtime 1.20.0** loaded the real ONNX graph and synthesized independently with both xvectors in [the Java test run](https://github.com/andriy2008-max/Jarvis-/actions/runs/42780780000). These are **Linux runner** measurements, not Android-device measurements.
5. The verified 145 MB ZIP is preserved in a **draft GitHub Release** `jarvis-offline-voices-v6-experimental` so the temporary Actions artifact can expire without losing the data. SHA-256: `12fdd6521ae864f5eafac2d5a205d32f299ba65229800dafbd647b4a3ff8e0d5`. **Do not publish until Android testing.**
6. Android source has the single-graph VITS inference implementation `OfflineOnnxSynthesizer`, cached sessions and WAV playback `OfflineSpeechClient`, a checked ZIP installer `OfflineVoicePackManager`, and an opt-in fallback path: offline → private HTTPS TTS server → system Android TTS. Source compilation and unit tests pass, but real-device Android inference **is not yet proven**.

## Android release gate — incomplete

- Verify ARM64 ONNX Runtime can load and execute this exact graph on a real phone.
- Test both speakers in airplane mode with clear audio, correct stress, numbers and punctuation.
- Measure model load latency, per-sentence generation latency, storage and RAM; ensure cancellation, background behavior and playback do not crash Android.
- Confirm signed upgrade keeps installed voice pack and original application ID/signing key.
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
