@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-windows.ps1"
echo.
if errorlevel 1 echo Could not start JARVIS TTS. Check the messages above.
pause
