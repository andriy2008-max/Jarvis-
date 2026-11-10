# JARVIS offline voice packs — technical status (0.1.13)

## Goal

One compact Android APK, with a single separate download of a shared neural model for the two **real** voices:

- `dmytro` (Дмитро, male);
- `lada` (Лада, female).

After a working ONNX runtime is integrated and validated, synthesized audio should be computed **on the phone** without the PC, cloud or Internet. A model file alone cannot speak: the Android app also needs the inference graph runner, exact Ukrainian text/number/stress preparation, input tokenization and speaker vectors.

## Status: download infrastructure, not an offline TTS release

- **Implemented**: strict HTTPS ZIP URL + SHA-256 installer, streaming download, archive/path/size limits, individual ONNX file checksums, manifest validation, and persistent storage in Android's private `getNoBackupFilesDir()`. The files survive in-place app upgrades and are intentionally kept out of the APK and Android backup.
- **Implemented**: settings path **Вибрати голос Джарвіса → Офлайн-пакет** on phone and Wear OS. Prototype UI asks for a release URL and a trusted SHA-256; it intentionally displays that offline inference is not yet enabled.
- **Implemented**: reproducible *experimental* export script in `tools/voice-export/export_voice_pack.py`, which loads upstream v6.0.0 weights, runs `espnet_onnx` export, attempts synthesis with **both** speaker embeddings using Python ONNX, and packages the model only after both smoke tests pass. ZIP contains manifest + `.onnx` components, not `.pth` weights.
- **Implemented but NOT validated with a real model**: experimental Android ONNX Runtime Tacotron2 autoregressive decoder, optional postnet, inverse GlobalMVN and HiFiGAN vocoder; WAV audio playback and fallback. See `OfflineOnnxSynthesizer.java` and `OfflineSpeechClient.java`. The app now attempts local synthesis when a verified pack is installed and falls back to the private TTS server / Android TTS on failure.
- **Implemented**: one-tap official pack registry at `android/offline-voices.json` over HTTPS. It is deliberately set to `ready:false` until the model export + Android tests pass; manual HTTPS + SHA-256 installs remain available for testing.
- **Not yet completed**: confirmed ONNX export from the v6 checkpoint, trustworthy release URL/SHA-256, actual Android runtime synthesis tests on a device, exact upstream text/number/stress normalization (basic text normalizer is in place), speech quality / latency / RAM measurements on real hardware. **No downloadable official JARVIS pack exists yet.**
- **Fallback today**: the currently configured private TTS server when available, or Android system Ukrainian TTS. The two original voices are **not yet proven to work offline on a real phone**. The UI explicitly marks the local model path as experimental and must not imply the real voices are ready to ship.

## Architecture caveat

The `robinhad/ukrainian-tts` **v6** `config.yaml` uses `joint_text2wav` (Tacotron2 plus HiFi-GAN), **not** a single VITS graph. Generic VITS-Android samples do not implement this model. The open-source `espnet_onnx` library documents support for JointText2Wav, but its export and Python inference must be proven on this **specific** checkpoint and both xvectors. On Android, the decoder may require iterative ONNX sessions and carefully maintained state. A successful export does not by itself prove Android compatibility.

The upstream release is [robinhad/ukrainian-tts v6.0.0](https://github.com/robinhad/ukrainian-tts/releases/tag/v6.0.0), released with model weights, config, speaker embeddings and normalization stats under the MIT license. Source `model.pth` is approximately 446 MB, **not** the final compressed ONNX pack size.

## Manual experimental export

Do **not** run this during an ordinary Android APK build or upload unverified binaries to GitHub:

```sh
# Use a compatible Python 3.10 environment (upstream ESPnet requires older dependencies).
pip install -r server/jarvis-tts-server/requirements.txt
pip install espnet_onnx onnx onnxruntime
python tools/voice-export/export_voice_pack.py \
  --cache-dir ./uktts-v6-cache \
  --output ./dist/jarvis-uktts-v6-onnx.zip
```

The script should print **both** speaker smoke-test sample counts and a ZIP SHA-256. Failures must be fixed in the export path; do **not** bypass `smoke_verified` to ship a model. Then an actual Android inference implementation, device benchmark and listening tests are required before releasing the ZIP. The Python export may require a powerful workstation or temporary cloud machine; do not start billable infrastructure without permission.

## Pack schema v1

The ZIP contains `manifest.json` and `onnx/*.onnx` components (possibly nested under `onnx/full/`). The manifest records:

```json
{
  "format": "jarvis-espnet-onnx-v1",
  "upstream": "robinhad/ukrainian-tts@v6.0.0",
  "architecture": "espnet-exported-joint-text2wav",
  "sample_rate": 22050,
  "voices": ["dmytro", "lada"],
  "tokens": ["<blank>", "<unk>"],
  "spembs": {"dmytro": [], "lada": []},
  "files": [{"path": "onnx/full/example.onnx", "bytes": 123, "sha256": "64 hex characters"}],
  "smoke_verified": true,
  "offline_runtime_verified_on_android": false
}
```

Example values above illustrate the schema only, **not an actual model manifest**. The entire ZIP must have its own trusted SHA-256 supplied by an authenticated JARVIS release. No default download URL or hash has been invented.

## Test strategy

`voice-tests.yml` runs Python pack contract tests, Java SHA/path rules and Android **compilation only**, never publishes or signs an APK. Once export is validated, add a reproducible Android instrumented test on a physical ARM64 phone that verifies equal recognized text from both offline voices, speed, memory, airplane-mode operation, and reuse after an APK upgrade.

Stable signing must continue using the *existing* keystore and application ID `com.andriy.jarvis.stable` so installation remains upgradable. Preview builds use `.preview` separately.

## User-visible limitations for this stage

1. No model release URL available yet; installing a random `.pth` or `.zip` will be rejected.
2. The browser can download a ZIP, but it must be a verified converted export.
3. Keep the application open during prototype downloads (background download services are not yet implemented).
4. Android TTS still speaks until a **tested** local ONNX engine is integrated.
