# JARVIS neural Ukrainian voice engine — Dmytro & Lada

**This is a real free/open-source voice model, not Android pitch-shifting.** Source: [robinhad/ukrainian-tts](https://github.com/robinhad/ukrainian-tts) (MIT licensed v6.0.0 model). Supports `dmytro` and `lada` as named speakers. The upstream model is ~446 MB **before** Python/ESPnet dependencies; it is installed on a *separate server/PC*, not bundled in the Android APK.

## Easiest free setup: Windows PC + Android (private Tailscale)

For personal use, the Windows PC can host both voices without TTS API charges. The PC needs to remain powered on while you use the neural voices. No RunPod GPU is required for a CPU-only first test.

1. On the Windows PC, install **Python 3.10**, **Git**, and **Tailscale**. On Android install Tailscale as well and log in to the **same Tailscale account**. Tailscale Personal is free for individual non-commercial use. Download this repository as ZIP from GitHub and extract it.
2. Open `server/jarvis-tts-server/` and double-click **Start-Jarvis-Voices.cmd**. On first run it installs the Python libraries and downloads the upstream model (around 446 MB plus dependencies). The program generates a private access code and displays it in the terminal. **Do not share the code**. It is locally stored using your Windows account's DPAPI encryption.
3. Once the terminal says the server is ready, open another Windows terminal (with administrator rights if your Tailscale installation requires them) and run:
   ```powershell
   tailscale serve --bg 8765
   ```
   Tailscale displays an HTTPS URL such as `https://my-pc.your-tailnet.ts.net`. You may need to approve HTTPS in your tailnet when prompted. **Do not use Tailscale Funnel**, which makes services publicly accessible.
4. In the Android Tailscale app, connect. In JARVIS select **🔊 Вибрати голос Джарвіса → Рушій TTS**, set endpoint to **that HTTPS URL + `/tts`**, enter the private access code from Windows, save, and select Дмитро or Лада.
5. Switch between the voices and speak a test phrase. If the server is offline, Android TTS will be used. To disable Tailscale Serve on Windows, run `tailscale serve off` (or `tailscale serve reset` on newer clients).

**Caution:** These scripts have not been executed on your own Windows PC, so the actual package installation and compatibility must still be verified there. This is a prototype personal setup, not a managed always-on cloud service.

## 1. Run locally (Windows/Linux)

Python **3.10** is recommended for compatibility with the upstream ESPnet/PyTorch dependencies.

```bash
cd server/jarvis-tts-server
python3.10 -m venv .venv
# Activate .venv on Linux/macOS:
source .venv/bin/activate
pip install -r requirements.txt
```

If your platform needs a specific PyTorch wheel, install the supported PyTorch build for CPU/GPU first, then install the requirements. Initial start downloads the v6 model and caches it in `model-cache/`. The server only requires CPU, but synthesis may be slow. Running on your own PC avoids per-character TTS API fees; electricity/cloud hosting is not free.

Set a unique, high-entropy access token (20–256 printable ASCII chars, no spaces). Never commit it to GitHub.

```bash
export JARVIS_TTS_TOKEN='replace-with-a-long-random-private-token'
python tts_server.py
```

Windows PowerShell (from the same directory):
```powershell
py -3.10 -m venv .venv
.\\.venv\\Scripts\\Activate.ps1
python -m pip install -r requirements.txt
$env:JARVIS_TTS_TOKEN = "replace-with-a-long-random-private-token"
python tts_server.py
```

By default listens on **127.0.0.1:8765**. Check `http://127.0.0.1:8765/health` on the same machine. The default listening socket is HTTP **only for the local machine**; do not expose it to the public internet directly.

## 2. Make accessible to Android securely

Use a **trusted HTTPS reverse proxy or tunnel** in front of port 8765 (for example a private Cloudflare Tunnel with appropriate access controls) and obtain a URL such as `https://your-own-host.example/tts`. The Android client *refuses HTTP*. Anyone who knows the public HTTPS URL still needs the TTS access token. Keep the endpoint private, rotate the token on exposure, and apply firewall/rate limits. The PC or server must stay running whenever you want neural voices.

In JARVIS: **🔊 Вибрати голос Джарвіса → Рушій TTS →** enter the URL and access token, save, select **Дмитро** or **Лада**. Test either via the profile selection preview or the menu. AI and local device replies use the same synthesis path. If the server is missing/timed out, **the app falls back** to a system Ukrainian Android voice with an explicit warning; it must not pretend the fallback is the named voice. When disconnected, no text is sent to the TTS server.

**Privacy:** Requests include the spoken response text (including AI replies) and selected speaker. Host this only on a trusted server; do not connect an untrusted public demo service to private conversations. Access tokens are stored in Android Keystore-encrypted preferences. There are no paid third-party TTS keys in the APK.

## API contract

`POST /tts`, Bearer token in `Authorization`, JSON: `{"text":"Привіт!","voice":"dmytro"}` or `"lada"`. Max 1200 characters per request. Response: 200 `audio/wav` (PCM 16-bit), or JSON error with 400, 401, 413, 415, or 503. `GET /health` responds 200. Requests are serialized for safe model inference.

## Tests

```bash
python -m unittest discover -s server/jarvis-tts-server -p 'test_*.py' -v
```

The tests mock the expensive model; they verify HTTP contract and auth, **not** the actual voice quality, performance or an Android device. Verify with real hardware and a deployed server before publishing an APK. No APK build or deployment is triggered by these changes.
