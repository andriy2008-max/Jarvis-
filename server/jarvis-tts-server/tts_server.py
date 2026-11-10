"""Private Dmytro/Lada Ukrainian TTS endpoint. Use behind an HTTPS reverse proxy/tunnel."""
import hmac
import io
import json
import os
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

MAX_JSON_BYTES = 8192
MAX_TEXT_CHARS = 1200
VOICES = frozenset({"dmytro", "lada"})


class SpeechEngine:
    """Models are loaded once on the server, never into the mobile APK."""
    def __init__(self, cache_dir: str, device: str) -> None:
        from ukrainian_tts.tts import TTS, Voices, Stress
        os.makedirs(cache_dir, exist_ok=True)
        self.tts_model = TTS(cache_folder=cache_dir, device=device)
        self.stress = Stress.Dictionary.value
        self.voices = {Voices.Dmytro.value, Voices.Lada.value}
        self.lock = threading.Lock()

    def synthesize(self, text: str, voice: str) -> bytes:
        if voice not in self.voices:
            raise ValueError("Unsupported voice")
        stream = io.BytesIO()
        # ESPnet inference is serialized to avoid GPU/CPU overload.
        with self.lock:
            self.tts_model.tts(text, voice, self.stress, stream)
        return stream.getvalue()


def make_handler(engine, token: str):
    if len(token) < 20 or len(token) > 256 or any(ord(c) < 33 or ord(c) > 126 for c in token):
        raise ValueError("JARVIS_TTS_TOKEN must be 20-256 printable ASCII characters without spaces")

    class Handler(BaseHTTPRequestHandler):
        def send_bytes(self, status: int, body: bytes, content_type: str):
            self.send_response(status)
            self.send_header("Content-Type", content_type)
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def error_json(self, code: int, message: str):
            self.send_bytes(code, json.dumps({"error": message}).encode("utf-8"),
                            "application/json; charset=utf-8")

        def do_GET(self):
            if self.path == "/health":
                self.send_bytes(200, b'{"status":"ok"}', "application/json")
            else:
                self.error_json(404, "Not found")

        def do_POST(self):
            if self.path != "/tts":
                return self.error_json(404, "Not found")
            # Compare without revealing whether a token is almost correct.
            header = self.headers.get("Authorization", "")
            if not hmac.compare_digest(header, "Bearer " + token):
                return self.error_json(401, "Unauthorized")
            try:
                length = int(self.headers.get("Content-Length", "0"))
            except ValueError:
                return self.error_json(400, "Invalid Content-Length")
            if not 0 < length <= MAX_JSON_BYTES:
                return self.error_json(413, "Invalid request size")
            if not self.headers.get("Content-Type", "").lower().startswith("application/json"):
                return self.error_json(415, "Expected application/json")
            try:
                payload = json.loads(self.rfile.read(length).decode("utf-8"))
            except (ValueError, UnicodeDecodeError):
                return self.error_json(400, "Invalid JSON")
            if not isinstance(payload, dict):
                return self.error_json(400, "Invalid request")
            text = payload.get("text")
            voice = payload.get("voice")
            if not isinstance(text, str) or not text.strip() or len(text) > MAX_TEXT_CHARS:
                return self.error_json(400, "Invalid text")
            if voice not in VOICES:
                return self.error_json(400, "Invalid voice")
            try:
                wav = engine.synthesize(text, voice)
                if not wav.startswith(b"RIFF") or wav[8:12] != b"WAVE":
                    raise RuntimeError("Invalid synthesized audio")
            except Exception:
                return self.error_json(503, "Synthesis unavailable")
            self.send_bytes(200, wav, "audio/wav")

        def log_message(self, format, *args):
            # Do not log request text or access tokens.
            pass

    return Handler


def main():
    token = os.environ.get("JARVIS_TTS_TOKEN", "")
    if len(token) < 20:
        raise SystemExit("Set JARVIS_TTS_TOKEN (minimum 20 characters).")
    host = os.environ.get("JARVIS_TTS_BIND", "127.0.0.1")
    port = int(os.environ.get("JARVIS_TTS_PORT", "8765"))
    cache = os.environ.get("JARVIS_TTS_CACHE_DIR", "./model-cache")
    device = os.environ.get("JARVIS_TTS_DEVICE", "cpu")
    engine = SpeechEngine(cache, device)
    server = ThreadingHTTPServer((host, port), make_handler(engine, token))
    print(f"JARVIS TTS ready on {host}:{port}; HTTPS proxy required for Android.")
    try:
        server.serve_forever()
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
