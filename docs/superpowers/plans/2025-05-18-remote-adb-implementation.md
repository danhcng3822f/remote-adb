# Remote ADB Over Internet Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a complete Remote ADB system allowing wireless, cross-network ADB connections without manual USB debugging toggling, powered by Root (`libsu`), with a high-performance Relay Server and PC CLI tooling.

**Architecture:** Android App uses `libsu` to toggle `adbd` on TCP:5555 and inject PC keys into `/data/misc/adb/adb_keys`. A background Foreground Service maintains a reverse TCP/WebSocket tunnel to the Node.js/TypeScript Relay Server. The Relay Server dynamically opens a public port forwarding traffic to the device. The PC connects via standard `adb connect <server>:<port>` or via the PC CLI Tool.

**Tech Stack:** 
- Android: Kotlin 2.0, Android Gradle Plugin 8.5+, Jetpack Compose, Material 3, `com.github.topjohnwu.libsu:core:6.0.0`, OkHttp / WebSockets.
- Relay Server: Node.js, TypeScript, ws, net socket multiplexer, Docker.
- PC Tool: PowerShell (Windows) + Bash (Linux/macOS) + Node.js CLI.

**Spec:** `docs/superpowers/specs/2025-05-18-remote-adb-design.md`

## Global Constraints
- Target Android SDK: 34 (Android 14) / Min SDK: 26 (Android 8.0).
- Root compatibility: Magisk, KernelSU, APatch, SuperSU.
- No cable / no Developer Options switch required once Root is granted.
- Zero external adb confirmation popups via direct `adb_keys` injection.

---

### Task 1: Relay Server Core & Protocol

**Files:**
- Create: `relay-server/package.json`
- Create: `relay-server/tsconfig.json`
- Create: `relay-server/src/types.ts`
- Create: `relay-server/src/session.ts`
- Create: `relay-server/src/server.ts`
- Create: `relay-server/Dockerfile`
- Create: `relay-server/docker-compose.yml`
- Test: `relay-server/test/server.test.ts`

- [ ] **Step 1: Create Relay Server project definition and TypeScript configuration**
- [ ] **Step 2: Define tunnel protocol message types (Register, Auth, StreamData, KeepAlive)**
- [ ] **Step 3: Implement SessionManager to track connected devices and assign dynamic TCP ports**
- [ ] **Step 4: Implement TCP Forwarder and WebSocket Server to bridge PC ADB sockets to device streams**
- [ ] **Step 5: Write unit tests verifying dynamic port allocation and socket forwarding**
- [ ] **Step 6: Run tests with `npm test` and verify PASS**

---

### Task 2: PC CLI Client & Connection Scripts

**Files:**
- Create: `pc-tool/package.json`
- Create: `pc-tool/src/cli.ts`
- Create: `pc-tool/adb-remote.ps1`
- Create: `pc-tool/adb-remote.sh`
- Test: `pc-tool/test/cli.test.ts`

- [ ] **Step 1: Create `adb-remote.ps1` for Windows PowerShell with automated `adbkey.pub` extraction and `adb connect` execution**
- [ ] **Step 2: Create `adb-remote.sh` for Linux/macOS**
- [ ] **Step 3: Implement Node.js CLI tool with QR code generator for PC key sharing and automated discovery**
- [ ] **Step 4: Test scripts and CLI execution**

---

### Task 3: Android App Project Setup & Root Layer (`libsu`)

**Files:**
- Create: `android-app/settings.gradle.kts`
- Create: `android-app/build.gradle.kts`
- Create: `android-app/app/build.gradle.kts`
- Create: `android-app/app/src/main/AndroidManifest.xml`
- Create: `android-app/app/src/main/java/com/remoteadb/app/App.kt`
- Create: `android-app/app/src/main/java/com/remoteadb/app/root/RootController.kt`
- Create: `android-app/app/src/main/java/com/remoteadb/app/root/AdbManager.kt`
- Test: `android-app/app/src/test/java/com/remoteadb/app/root/AdbManagerTest.kt`

- [ ] **Step 1: Scaffolding Gradle multi-module Android project with Jetpack Compose & libsu dependencies**
- [ ] **Step 2: Initialize libsu in Application class (`App.kt`) with shell pre-warming**
- [ ] **Step 3: Implement `RootController` to check Root status and detect root provider (Magisk/KernelSU/APatch)**
- [ ] **Step 4: Implement `AdbManager` for shell commands: `setprop service.adb.tcp.port`, daemon restart, and `/data/misc/adb/adb_keys` injection**
- [ ] **Step 5: Write unit tests for command generation and state parsing**

---

### Task 4: Android App Tunnel Client & Foreground Service

**Files:**
- Create: `android-app/app/src/main/java/com/remoteadb/app/tunnel/TunnelClient.kt`
- Create: `android-app/app/src/main/java/com/remoteadb/app/tunnel/LocalAdbBridge.kt`
- Create: `android-app/app/src/main/java/com/remoteadb/app/service/TunnelForegroundService.kt`
- Create: `android-app/app/src/main/java/com/remoteadb/app/data/AppPreferences.kt`

- [ ] **Step 1: Implement `LocalAdbBridge` that forwards raw binary streams between WebSocket and `127.0.0.1:5555`**
- [ ] **Step 2: Implement `TunnelClient` with auto-reconnect, ping/pong heartbeat, and session registration**
- [ ] **Step 3: Implement `TunnelForegroundService` with persistent Notification and quick action toggles**
- [ ] **Step 4: Implement `AppPreferences` with DataStore/SharedPreferences to store Server URL, Device ID, PIN, and Port**

---

### Task 5: Android App UI (Jetpack Compose & Material 3)

**Files:**
- Create: `android-app/app/src/main/java/com/remoteadb/app/ui/theme/Theme.kt`
- Create: `android-app/app/src/main/java/com/remoteadb/app/ui/MainViewModel.kt`
- Create: `android-app/app/src/main/java/com/remoteadb/app/ui/MainActivity.kt`
- Create: `android-app/app/src/main/java/com/remoteadb/app/ui/screens/HomeScreen.kt`
- Create: `android-app/app/src/main/java/com/remoteadb/app/ui/screens/SettingsScreen.kt`
- Create: `android-app/app/src/main/java/com/remoteadb/app/ui/screens/KeyManagerScreen.kt`

- [ ] **Step 1: Implement `MainViewModel` binding Root state, ADB status, and Tunnel connection state**
- [ ] **Step 2: Implement `HomeScreen` with Root banner, ADB toggle switch, Tunnel status card, and 1-Click Copy command**
- [ ] **Step 3: Implement `KeyManagerScreen` allowing manual PC public key addition or scanning PC QR code**
- [ ] **Step 4: Implement `SettingsScreen` for configuring custom Relay Server host, port range, and credentials**
- [ ] **Step 5: Set up `MainActivity` with Material 3 Navigation bar**

---

### Task 6: Documentation, Docker Setup & End-to-End Verification Guide

**Files:**
- Create: `README.md`
- Create: `docs/USAGE_GUIDE.md`
- Create: `docs/SERVER_DEPLOYMENT.md`

- [ ] **Step 1: Write comprehensive README with Quick Start guide**
- [ ] **Step 2: Write Server Deployment documentation with Docker Compose & Nginx Reverse Proxy guide**
- [ ] **Step 3: Write Root troubleshooting guide for Magisk, KernelSU, and APatch**
