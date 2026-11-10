# Local Windows bootstrap for JARVIS neural voices. Run from this directory.
# Requires Python 3.10, Git, internet for model download. No third-party paid TTS.
$ErrorActionPreference = "Stop"
Set-Location -LiteralPath $PSScriptRoot

$pythonLauncher = Get-Command py -ErrorAction SilentlyContinue
if (-not $pythonLauncher) {
    throw "Install Python 3.10 from python.org with the Windows py launcher first."
}
& py -3.10 --version
if ($LASTEXITCODE -ne 0) {
    throw "Python 3.10 is required. Install it and retry."
}

$venvPython = Join-Path $PSScriptRoot ".venv\Scripts\python.exe"
if (-not (Test-Path -LiteralPath $venvPython)) {
    & py -3.10 -m venv .venv
    if ($LASTEXITCODE -ne 0) { throw "Could not create a Python environment." }
}
$ready = Join-Path $PSScriptRoot ".venv\.jarvis-tts-installed"
if (-not (Test-Path -LiteralPath $ready)) {
    Write-Host "Installing Ukrainian TTS and its dependencies..."
    & $venvPython -m pip install --upgrade pip
    if ($LASTEXITCODE -ne 0) { throw "pip update failed." }
    & $venvPython -m pip install -r requirements.txt
    if ($LASTEXITCODE -ne 0) { throw "Could not install the TTS model runtime." }
    New-Item -ItemType File -Path $ready -Force | Out-Null
}

# The access code is generated locally and encrypted for this Windows account using DPAPI.
$secureDir = Join-Path $env:LOCALAPPDATA "JarvisTTS"
New-Item -ItemType Directory -Path $secureDir -Force | Out-Null
$secureFile = Join-Path $secureDir "access-code.dpapi"
if (Test-Path -LiteralPath $secureFile) {
    $secure = Get-Content -LiteralPath $secureFile -Raw | ConvertTo-SecureString
} else {
    $newCode = [guid]::NewGuid().ToString("N") + [guid]::NewGuid().ToString("N")
    $secure = ConvertTo-SecureString $newCode -AsPlainText -Force
    $secure | ConvertFrom-SecureString | Set-Content -LiteralPath $secureFile
}
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
    $env:JARVIS_TTS_TOKEN = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
}
$env:JARVIS_TTS_BIND = "127.0.0.1"
$env:JARVIS_TTS_CACHE_DIR = Join-Path $PSScriptRoot "model-cache"
$env:JARVIS_TTS_DEVICE = "cpu"

Write-Host ""
Write-Host "JARVIS neural TTS (Dmytro/Lada):"
Write-Host "Private access code for the Android settings (do not share):"
Write-Host $env:JARVIS_TTS_TOKEN
Write-Host ""
Write-Host "After the server starts, in a separate PowerShell console run:"
Write-Host "  tailscale serve --bg 8765"
Write-Host "Copy its HTTPS URL, append /tts, and paste with the access code in JARVIS."
Write-Host "The same Tailscale account must be connected on the phone."
Write-Host "Close this window to stop the speech engine (but Tailscale Serve persists)."
Write-Host ""
& $venvPython -u tts_server.py
if ($LASTEXITCODE -ne 0) { throw "TTS server exited with an error." }
