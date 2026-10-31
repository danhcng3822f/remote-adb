<#
.SYNOPSIS
    Connect to Remote ADB device via Relay Server (Windows PowerShell)
.EXAMPLE
    .\adb-remote.ps1 -Server "relay.example.com" -Port 15000
    .\adb-remote.ps1 -ShowKey
#>

param (
    [string]$Server,
    [int]$Port,
    [switch]$ShowKey,
    [switch]$Scrcpy
)

$adbKeyPath = "$HOME\.android\adbkey.pub"

if ($ShowKey) {
    if (Test-Path $adbKeyPath) {
        $keyContent = Get-Content $adbKeyPath -Raw
        Write-Host "================ ADB PUBLIC KEY ================" -ForegroundColor Cyan
        Write-Host $keyContent.Trim() -ForegroundColor Green
        Write-Host "================================================" -ForegroundColor Cyan
        Write-Host "Copy the key above or add it to Remote ADB app on your phone."
    } else {
        Write-Error "Could not find adbkey.pub at $adbKeyPath. Please start adb once using 'adb start-server'."
    }
    exit 0
}

if (-not $Server -or -not $Port) {
    Write-Host "Usage: .\adb-remote.ps1 -Server <host> -Port <port> [-Scrcpy]" -ForegroundColor Yellow
    Write-Host "   Or: .\adb-remote.ps1 -ShowKey" -ForegroundColor Yellow
    exit 1
}

$target = "$Server`:$Port"
Write-Host "Connecting to remote ADB at $target..." -ForegroundColor Cyan

$res = adb connect $target
Write-Host $res

if ($res -match "connected to" -or $res -match "already connected") {
    Write-Host "Connected successfully!" -ForegroundColor Green
    if ($Scrcpy) {
        Write-Host "Starting scrcpy on $target..." -ForegroundColor Cyan
        scrcpy -s $target
    }
} else {
    Write-Host "Connection failed. Please verify that the phone is online and connected to the relay server." -ForegroundColor Red
}
