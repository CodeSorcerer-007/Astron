# 🪐 Astron — God-Tier Android Local File Transfer

> *"Whatever the file. Whatever the situation. Just send it."*

**Astron** is a native Android file transfer application built with modern Kotlin, Jetpack Compose, and Material 3. Inspired by the deep cosmic aesthetic of its ringed-planet logo, Astron delivers high-performance, zero-friction, offline-first peer-to-peer file sharing with bounded memory streaming, end-to-end cryptographic verification, cross-platform browser support, and a zero-radio optical fall-back.

---

## 🌟 Key Pillars & Features

### 1. Ruthless Simplicity
- **Two-Tap Flow:** Open app → Tap **SEND** or **RECEIVE**. No accounts, no sign-ups, no internet connection required.
- **Proximity Radar:** Pulsing orbital radar that automatically discovers nearby peers using zero-configuration Network Service Discovery (NSD / mDNS).
- **System Share Sheet Integration:** Send any file or photo directly from any app via standard Android `ACTION_SEND` and `ACTION_SEND_MULTIPLE`.

### 2. Multi-Tier Transport Engine
- **🪐 Astron LAN Transport (P2P High Speed):** Direct TCP socket streaming with framed binary protocol, SHA-256 streaming verification, chunked resume support, and full-speed local Wi-Fi throughput.
- **🌐 Web Drop (Cross-Platform Zero-App Mode):** Built-in micro HTTP server serving a cosmic dark-mode web portal. Share files seamlessly with **iPhones, iPads, MacBooks, Windows PCs, Linux laptops, and smart TVs** without installing any app on the receiver's device.
- **⚡ Light Drop (Zero-Radio Optical Air-Gap):** High-density sequenced visual QR frame transmission. Works in airplane mode, Faraday cages, or zero-connectivity environments by flashing sequenced base64 data packets with CRC32 checksums directly to the receiver's camera.

### 3. Bulletproof Engineering
- **Constant Memory Footprint:** Bounded 64 KB / 1 MB streaming buffers. Handles 100 GB+ files with zero risk of `OutOfMemoryError`.
- **Integrity Verified:** Real-time hardware-accelerated SHA-256 checksum calculation verified after every transfer before moving files to user storage.
- **Path Traversal Defense:** Strict filename sanitization preventing directory escape exploits.
- **Persistent Transfer Service:** Android Foreground Service with continuous notification keeping transfers alive when the app is backgrounded or the screen is locked.
- **Priority Engine:** Configurable throughput modes — *Maximum Speed*, *Balanced*, and *Battery Saver*.

---

## 🎨 Cosmic Deep-Space Visual Design
- **Theme:** Deep space dark mode (`#070A12`, `#0E1424`, `#162038`).
- **Accents:** Cosmic Cyan (`#00E5FF`) and Electric Blue (`#3B82F6`).
- **Orbital Animations:** Custom pulsing radar waves and spinning planetary rings inspired by the Astron insignia (`Logo.png`).
- **Dynamic Speedometer:** Live exponential-moving-average (EMA) transfer speed display with ETA calculation.

---

## 📱 Project & Architecture Overview

```
e:/Astron/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/astron/app/
│   │   │   │   ├── core/
│   │   │   │   │   ├── model/          # TransferItem, Manifest, PeerDevice, TransferSession
│   │   │   │   │   ├── security/       # AES-256-GCM, SHA-256 streaming, Path sanitization
│   │   │   │   │   ├── storage/        # SAF, Downloads/Astron, chunked resume file I/O
│   │   │   │   │   └── util/           # NetworkUtils, QrCodeGenerator, Formatters
│   │   │   │   ├── transport/
│   │   │   │   │   ├── lan/            # NSD discovery + TCP binary streaming engine
│   │   │   │   │   ├── webdrop/        # Embedded dark-mode HTTP web server & upload handler
│   │   │   │   │   └── lightdrop/      # Zero-radio sequenced optical frame generator & parser
│   │   │   │   ├── transfer/           # TransferEngine (state machine, speed EMA, orchestration)
│   │   │   │   ├── service/            # TransferForegroundService (notification & wakelock)
│   │   │   │   ├── data/               # PreferencesRepository & HistoryRepository (DataStore)
│   │   │   │   └── ui/
│   │   │   │       ├── components/     # Orbital radar, logo header, incoming dialog
│   │   │   │       ├── screens/        # Home, Send, Receive, Transfer, WebDrop, LightDrop, History, Settings
│   │   │   │       ├── navigation/     # NavHost and screen routes
│   │   │   │       └── theme/          # Cosmic theme, typography, color tokens
│   │   │   └── res/                    # Icons (mdpi to xxxhdpi), colors, manifests
│   │   └── test/                       # Unit test suite (AstronCoreTests)
│   └── build.gradle.kts
├── docs/
│   ├── ARCHITECTURE.md                 # Deep architectural dive
│   ├── PROTOCOL.md                     # Wire format specifications
│   └── TEST_REPORT.md                  # Test results and security audits
└── gradle/
```

---

## 🛠️ Building & Running

### Prerequisites
- **JDK:** Java 17 or Java 21
- **Android SDK:** Platform 36 (targetSdk 36, minSdk 24)
- **Build System:** Gradle 9.0.1 / AGP 9.0.1

### Build Debug APK
```powershell
.\gradlew.bat assembleDebug
```
Output APK location:
`app/build/outputs/apk/debug/app-debug.apk`

### Run Unit Tests
```powershell
.\gradlew.bat test
```
All unit tests execute headless on JVM:
- Cryptographic round-trip tests (AES-256-GCM)
- Filename path-traversal sanitization
- Streaming SHA-256 hash validation
- Light Drop optical framing, sequence tracking, and CRC32 verification
- Manifest serialization and deserialization
- Transfer speed and human-readable byte formatters

### Build Release APK
```powershell
.\gradlew.bat assembleRelease
```

---

## 🔒 Security Guarantees
1. **Zero Tracking:** No telemetry, analytics, crash trackers, or third-party ad networks.
2. **Ephemeral Pairing:** One-time pairing tokens and QR code handshakes.
3. **No Cloud Relay:** All transfers stay strictly within the local physical environment (LAN, Wi-Fi Direct, or Optical line-of-sight).
4. **Sandboxed Output:** Received files are placed cleanly in the user's `Downloads/Astron` directory with cryptographic checksum verification.

---

## 📄 License
Copyright (c) 2026 Astron Core Team. Distributed under the MIT License.
