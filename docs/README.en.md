<div align="center">
  <img src="./images/logo.png" alt="AstrBot for Android" width="120" />
  <h1>AstrBot for Android · SnowLuma Edition</h1>
  <p><a href="../README.md">简体中文</a> | <strong>English</strong></p>
  <p><strong>Run AstrBot, SnowLuma and QQ on your Android device.</strong></p>
  <p>One APK · Local execution · Native management · Built-in desktop and WebUIs</p>
  <p>
    <img src="https://img.shields.io/badge/Kotlin-2.1.20-7F52FF?style=for-the-badge&amp;logo=kotlin&amp;logoColor=white" alt="Kotlin" />
    <img src="https://img.shields.io/badge/Jetpack_Compose-Native_UI-4285F4?style=for-the-badge&amp;logo=jetpackcompose&amp;logoColor=white" alt="Jetpack Compose" />
    <img src="https://img.shields.io/badge/Ubuntu-PRoot-E95420?style=for-the-badge&amp;logo=ubuntu&amp;logoColor=white" alt="PRoot Ubuntu" />
  </p>
  <p>
    <a href="https://github.com/HZP-BT/astrbot-for-android-snowluma/releases/latest"><img src="https://img.shields.io/badge/Version-1.3.0s6-3867FF?style=flat-square" alt="Version 1.3.0s6" /></a>
    <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&amp;logo=android&amp;logoColor=white" alt="Android 8.0 and above" />
    <img src="https://img.shields.io/badge/Architecture-ARM64-64748B?style=flat-square" alt="ARM64" />
    <a href="../LICENSE"><img src="https://img.shields.io/badge/License-MIT-18A978?style=flat-square" alt="MIT License" /></a>
    <img src="https://img.shields.io/badge/SnowLuma-v2.2.1-0A7E8C?style=flat-square" alt="SnowLuma 2.2.1" />
    <a href="https://github.com/HZP-BT"><img src="https://img.shields.io/badge/Maintainer-HZP--BT-111827?style=flat-square&amp;logo=github" alt="HZP-BT" /></a>
  </p>
  <p>
    <a href="#features">Features</a> ·
    <a href="#architecture">Architecture</a> ·
    <a href="#getting-started">Getting started</a> ·
    <a href="#snowluma-setup">SnowLuma setup</a> ·
    <a href="#building-from-source">Build</a> ·
    <a href="#troubleshooting">Troubleshooting</a> ·
    <a href="./使用指南.md">User guide (Chinese)</a> ·
    <a href="./CHANGELOG.md">Changelog</a>
  </p>
</div>

## Overview

**AstrBot for Android (SnowLuma Edition)** runs AstrBot and SnowLuma entirely on an Android phone. A single APK bundles a PRoot Ubuntu environment, AstrBot, Linux QQ, a lightweight Ubuntu desktop and SnowLuma; the native management UI handles runtime setup, service start/stop, QQ sign-in, model configuration and embedded WebUIs. **No root is required.**

AstrBot provides models, platforms, plugins and personas. SnowLuma converts the signed-in Linux QQ session into OneBot v11 events and actions, and connects back to AstrBot over a reverse WebSocket. QQ sign-in happens in the built-in desktop by scanning a QR code.

The current release is **1.3.0-snowluma-6** and pins **SnowLuma v2.2.1**, the version verified on this stack.

## Features

- **Self-contained runtime**: Ubuntu 24.04.3, AstrBot 4.29.3, Linux QQ 3.2.34, Xvfb/Fluxbox/noVNC and dependencies are bundled for offline setup.
- **Native service management**: home screen cards for AstrBot, Ubuntu desktop and SnowLuma with start/stop, WebUI and details.
- **SnowLuma integration**: official release download with SHA-256 verification, guest-compat startup injection, WebUI on `127.0.0.1:5099`, OneBot v11 reverse WebSocket to AstrBot.
- **Pinned and preserved**: SnowLuma v2.2.1 is pinned; reinstalling or switching versions keeps `config` and `data`, including the WebUI password.
- **Crash recovery**: leftover processes from system kills (exit code 137) are reaped and ports are released before restart.
- **Desktop, terminal and extensions**: built-in Ubuntu desktop for QQ sign-in, terminal and SSH, optional DeepSeek Harness and cpolar.
- **Backups and safety**: local snapshots and portable backups, redacted logs, SHA-256 verification for every download.

## Architecture

```
Android App (Kotlin / Compose)
├─ RuntimeService — foreground service, supervision, ports and logs
├─ PRoot Ubuntu 24.04 (bundled image)
│  ├─ AstrBot 4.29.3 — WebUI 6185
│  ├─ Linux QQ 3.2.34 — SnowLuma native hook
│  ├─ SnowLuma 2.2.1 — WebUI 5099 — OneBot v11 reverse WS → AstrBot
│  ├─ Xvfb / Fluxbox / noVNC — desktop
│  └─ Terminal / SSH / DeepSeek Harness
└─ WebView — embedded panels
```

## Getting started

1. Install the ARM64 APK from [Releases](https://github.com/HZP-BT/astrbot-for-android-snowluma/releases/latest).
2. Open the app and initialize the Ubuntu runtime (about 2.1 GB unpacked; keep 3 GB free).
3. Tap **Start all** to start AstrBot, then configure the dashboard password, a model provider and the default model.
4. Open the **Ubuntu desktop** card, install desktop dependencies and the original QQ, start the desktop and sign in to QQ.
5. Open the **SnowLuma** card, accept the official license, install SnowLuma (v2.2.1) and start it.
6. Open `http://127.0.0.1:5099`, sign in with the initial password, change it, and add a reverse WebSocket client for the account.
7. Test with another QQ account.

## SnowLuma setup

Add a **WebSocket client (reverse connection)** in the account's OneBot endpoints:

| Field | Value |
| --- | --- |
| Type | WebSocket client (reverse) |
| URL | `ws://127.0.0.1:6199/ws` (same port as AstrBot) |
| Token | Same as AstrBot's OneBot connection; empty by default |
| Role | `Universal` |
| Format | `array` |
| Enabled | yes |

Keep a single enabled OneBot v11 connection in AstrBot on `127.0.0.1:6199`. Once saved, SnowLuma should show the client as connected and AstrBot should report the reverse connection as connected.

If GitHub is unreachable from the phone, import `SnowLuma-v2.2.1-linux-arm64.tar.gz` through the app's file exchange into `/android-share`, copy it to `/manager/extensions/snowluma/package.tar.gz` from the Ubuntu terminal, and tap install again.

SnowLuma v2.3.x replaced the native hook and makes Linux QQ 3.2.34-53644 exit after injection, so this project stays on v2.2.1. Use `tools/snowluma-switch-2.2.1.sh` to switch versions while preserving configuration.

## Building from source

Requirements: JDK 17, Android SDK 35, NDK 27.0.12077973 and Python 3.11+.

```bash
./gradlew :core:compileKotlin :app:compileBootstrapReleaseKotlin
```

A complete APK also needs the runtime image, native libraries and distribution assets configured in `local.properties`, plus a PKCS12 signing identity in `.local/signing/`:

```bash
./gradlew -PbundledRuntime=true :app:assembleBundledRelease
```

Verify the result with `apksigner verify --min-sdk-version 21`, `aapt2 dump badging`, and by comparing the SHA-256 of `assets/bootstrap/arm64-v8a/ubuntu.tar.bin` with its manifest.

## Troubleshooting

- **Port 6185 already in use**: an AstrBot process survived a system kill. The current build reaps leftovers before start and after stop; also check for a manually started instance.
- **Services stop after a while (exit code 137)**: Android 12+ limits background child processes (about 32 by default) or memory is low. Raise the child-process limit with Shizuku under **Settings → Permissions**, whitelist the app for battery, and close unused sessions.
- **QQ exits after SnowLuma starts**: make sure SnowLuma is v2.2.1; v2.3.x is known to crash Linux QQ 3.2.34-53644 on injection.
- **Forgot the SnowLuma WebUI password**: delete `program/config/webui.json`, start SnowLuma manually with `SNOWLUMA_WEBUI_BOOTSTRAP_PASSWORD`, set a new password, then start it from the app.

## Contributors

- **HZP-BT** — maintainer: SnowLuma integration, home service cards, build and release
- JunWan666 — upstream author of the base project

## License and credits

Original code in this project is licensed under the [MIT License](../LICENSE). The base project is [JunWan666/astrbot-for-android](https://github.com/JunWan666/astrbot-for-android); thanks to the original author and all contributors.

This project is not affiliated with Tencent, QQ or SnowLuma. SnowLuma is source-available under a non-commercial license and its native components are bound by its EULA; third-party packaging, automated deployment and commercial use require prior written authorization. QQ and related components remain the property of Tencent. Use this project for personal study and research only, follow the QQ User Agreement and applicable laws, and evaluate account risk before using non-official interoperability tools.

Hide account names, QR codes, passwords, tokens and API keys before sharing logs or screenshots.
