import http.client
import json
import threading
import unittest
from http.server import ThreadingHTTPServer
from tts_server import make_handler

KEY = "test-tts-bearer-key-over-20"


class Engine:
    def __init__(self):
        self.calls = []

    def synthesize(self, text, voice):
        self.calls.append((text, voice))
        return b"RIFF" + b"0000" + b"WAVE" + bytes(40)


class TtsApiTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.engine = Engine()
        cls.http = ThreadingHTTPServer(("127.0.0.1", 0), make_handler(cls.engine, KEY))
        cls.thread = threading.Thread(target=cls.http.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.http.shutdown()
        cls.http.server_close()
        cls.thread.join()

    def request(self, voice="dmytro", text="Привіт!", token=KEY, path="/tts"):
        conn = http.client.HTTPConnection("127.0.0.1", self.http.server_port, timeout=3)
        payload = json.dumps({"voice": voice, "text": text}).encode("utf-8")
        headers = {"Content-Type": "application/json", "Authorization": "Bearer " + token}
        conn.request("POST", path, payload, headers=headers)
        response = conn.getresponse()
        status = response.status
        data = response.read()
        conn.close()
        return status, data

    def test_both_real_voice_ids(self):
        for voice in ("dmytro", "lada"):
            code, data = self.request(voice)
            self.assertEqual(200, code)
            self.assertTrue(data.startswith(b"RIFF"))
            self.assertIn(("Привіт!", voice), self.engine.calls)

    def test_requires_auth(self):
        self.assertEqual(401, self.request(token="invalid")[0])

    def test_rejects_bad_inputs(self):
        self.assertEqual(400, self.request(voice="other")[0])
        self.assertEqual(400, self.request(text=" ")[0])
        self.assertEqual(400, self.request(text="x" * 1201)[0])
        self.assertEqual(404, self.request(path="/chat")[0])


if __name__ == "__main__":
    unittest.main()
