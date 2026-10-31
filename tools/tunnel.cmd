@echo off
REM Quick Cloudflare tunnel for the Remote-ADB relay server (HTTP + WebSocket on :8080).
REM Prints a public https://<random>.trycloudflare.com URL. Ephemeral: URL changes on every run.
setlocal
cd /d "%~dp0.."
tools\cloudflared.exe tunnel --url http://localhost:8080 --no-autoupdate
