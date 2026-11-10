import json
import tempfile
import unittest
import zipfile
from pathlib import Path
from export_voice_pack import create_package, sha256_file


class PackTests(unittest.TestCase):
    def test_requires_real_smoke_check(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            (root / "tts_model_encoder.onnx").write_bytes(b"example ONNX placeholder")
            with self.assertRaises(RuntimeError):
                create_package(root, root / "invalid.zip", ["token"] * 48,
                               {"dmytro": [0.0] * 192, "lada": [0.1] * 192},
                               22050)

    def test_pack_manifest_has_both_voice_vectors_and_hashes(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            (root / "full").mkdir()
            model = root / "full" / "tts_model_encoder.onnx"
            model.write_bytes(b"synthetic bytes for contract test only")
            out = root / "voice-pack.zip"
            digest = create_package(
                root, out, ["<unk>"] + ["a"] * 47,
                {"dmytro": [0.0] * 192, "lada": [0.5] * 192},
                22050, smoke_verified=True)
            self.assertEqual(digest, sha256_file(out))
            with zipfile.ZipFile(out) as archive:
                manifest = json.loads(archive.read("manifest.json"))
                self.assertEqual(["dmytro", "lada"], manifest["voices"])
                entry = manifest["files"][0]
                self.assertEqual("onnx/full/tts_model_encoder.onnx", entry["path"])
                self.assertEqual(sha256_file(model), entry["sha256"])
                self.assertEqual(192, len(manifest["spembs"]["lada"]))
                self.assertFalse(manifest["offline_runtime_verified_on_android"])


if __name__ == "__main__":
    unittest.main()
