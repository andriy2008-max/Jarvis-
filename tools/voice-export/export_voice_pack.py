#!/usr/bin/env python3
"""Export robinhad Ukrainian TTS v6.0.0 to a checked two-voice ONNX pack.

This is an EXPERIMENT, not a confirmed compatible export. It must not publish
anything unless both voices synthesize WAV with espnet_onnx on the export PC.
Requires the upstream Ukrainian TTS environment and espnet_onnx installed.
"""
import argparse
import hashlib
import json
import shutil
import tempfile
import zipfile
from pathlib import Path

FORMAT = "jarvis-espnet-onnx-v1"
SAMPLE_TEXT = "Привіт! Я Джарвіс, і це перевірка українського озвучення."


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def create_package(onnx_root, output_zip, token_list, speaker_vectors,
                   sample_rate, *, architecture="unverified", smoke_verified=False):
    """Package an exported ONNX model, NEVER PyTorch weights.

    Validation deliberately requires smoke verification; metadata-only packages
    cannot accidentally be uploaded as usable JARVIS voice models.
    """
    if not smoke_verified:
        raise RuntimeError("Both voices must pass ONNX synthesis before packaging.")
    onnx_root = Path(onnx_root)
    output_zip = Path(output_zip)
    files = sorted(p for p in onnx_root.rglob("*.onnx") if p.is_file())
    if not files:
        raise ValueError("No ONNX files found in export.")
    if not isinstance(token_list, (list, tuple)) or len(token_list) < 40:
        raise ValueError("Missing real ESPnet character vocabulary")
    for voice in ("dmytro", "lada"):
        vector = speaker_vectors.get(voice)
        if vector is None or not 64 <= len(vector) <= 512:
            raise ValueError("Invalid speaker embedding for " + voice)
    if not 16000 <= int(sample_rate) <= 48000:
        raise ValueError("Invalid model sample rate")

    model_files = []
    for source in files:
        rel = source.relative_to(onnx_root).as_posix()
        archive_name = "onnx/" + rel
        if (not archive_name.endswith(".onnx") or ".." in archive_name or
                any(c not in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_./-"
                    for c in archive_name)):
            raise ValueError("Unsafe ONNX file path")
        model_files.append({
            "path": archive_name,
            "bytes": source.stat().st_size,
            "sha256": sha256_file(source),
        })
    manifest = {
        "format": FORMAT,
        "upstream": "robinhad/ukrainian-tts@v6.0.0",
        "architecture": architecture,
        "sample_rate": int(sample_rate),
        "voices": ["dmytro", "lada"],
        "tokens": list(token_list),
        "spembs": {voice: [float(v) for v in speaker_vectors[voice]]
                   for voice in ("dmytro", "lada")},
        "files": model_files,
        "smoke_verified": True,
        "offline_runtime_verified_on_android": False,
    }
    # This flag is documentary; Android must still validate actual inference.
    output_zip.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=output_zip.parent, suffix=".partial",
                                     delete=False) as temp:
        temporary = Path(temp.name)
    try:
        with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_STORED) as pack:
            pack.writestr("manifest.json", json.dumps(
                manifest, ensure_ascii=False, sort_keys=True,
                separators=(",", ":")).encode("utf-8"))
            for item, source in zip(model_files, files):
                pack.write(source, item["path"])
        temporary.replace(output_zip)
    finally:
        temporary.unlink(missing_ok=True)
    return sha256_file(output_zip)


def export_model(cache_dir, output):
    # Deferred imports: make packaging contract unit-testable without 446 MB model.
    import numpy as np
    from ukrainian_tts.tts import TTS
    from ukrainian_tts.formatter import preprocess_text
    from ukrainian_tts.stress import sentence_to_stress, stress_dict
    from espnet_onnx.export import TTSModelExport
    from espnet_onnx import Text2Speech as OnnxSpeech

    cache_dir = Path(cache_dir)
    cache_dir.mkdir(parents=True, exist_ok=True)
    native = TTS(cache_folder=str(cache_dir), device="cpu")
    native_model = native.synthesizer

    with tempfile.TemporaryDirectory(prefix="jarvis-espnet-export-") as temp:
        export_base = Path(temp)
        converter = TTSModelExport(cache_dir=export_base)
        converter.export(native_model, tag_name="jarvis-uktts-v6", quantize=False)
        model_dir = export_base / "jarvis-uktts-v6"
        if not model_dir.is_dir():
            raise RuntimeError("espnet_onnx did not create the expected model directory")
        tested = OnnxSpeech(model_dir=model_dir)
        prepared = sentence_to_stress(preprocess_text(SAMPLE_TEXT), stress_dict)
        vecs = {}
        for voice in ("dmytro", "lada"):
            vector = np.asarray(native.xvectors[voice][0], dtype=np.float32).reshape(-1)
            if vector.size < 64:
                raise RuntimeError("Unexpected speaker embedding size")
            output_audio = tested(prepared, spembs=vector)
            wav = np.asarray(output_audio["wav"])
            if wav.size < 1000 or not np.isfinite(wav).all():
                raise RuntimeError("ONNX synthesis failed for " + voice)
            print(voice, "ONNX samples:", wav.size)
            vecs[voice] = vector.tolist()
        vocab = list(native_model.preprocess_fn.token_id_converter.token_list)
        sample_rate = int(native_model.fs)
        # Supports multiple generated ONNX submodels for Tacotron2+HiFiGAN.
        digest = create_package(
            model_dir, Path(output), vocab, vecs, sample_rate,
            architecture="espnet-exported-joint-text2wav",
            smoke_verified=True,
        )
    print("Model pack:", output)
    print("SHA-256:", digest)
    print("Publish only after testing offline inference and speech quality on Android.")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cache-dir", default="./model-cache")
    ap.add_argument("--output", default="./dist/jarvis-uktts-v6-onnx.zip")
    args = ap.parse_args()
    export_model(args.cache_dir, args.output)


if __name__ == "__main__":
    main()
