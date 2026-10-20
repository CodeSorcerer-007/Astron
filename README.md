<div align="center">

<img src="Logo.png" alt="Astron Logo" width="180" />

# 🪐 Astron — God-Tier Android Local & Remote File Transfer

> *"Whatever the file. Whatever the network. Whatever the distance. Just send it."*

[![Platform](https://img.shields.io/badge/Platform-Android_7.0+_(API_24+)--36-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Tests](https://img.shields.io/badge/Tests-17_Passing-success?style=for-the-badge&logo=checkmarx&logoColor=white)](docs/TEST_REPORT.md)
[![License](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)](LICENSE)

</div>

---

**Astron** is the definitive, all-in-one native Android file transfer application built with modern Kotlin, Jetpack Compose, Material 3, and Coroutines. Inspired by the deep cosmic aesthetic of its ringed-planet insignia, Astron elevates Android file sharing to the **God-Tier**: combining multi-stream LAN acceleration, Wi-Fi Direct P2P, autonomous hotspot mode, Bluetooth Classic RFCOMM, remote Internet Relay WebSocket streaming, zero-install Web Drop for cross-platform sharing, adaptive optical LightDrop, Room SQLite crash-safe byte-level resumability, a built-in categorized file explorer, and rich multi-format file previews.

---

## 🌟 God-Tier Feature Matrix (10/10 Across All Dimensions)

### 1. 🧠 Adaptive Transport Orchestrator (`TransportOrchestrator`)
- **Capability Detection**: Dynamic runtime scanner probing 5 GHz / 6 GHz Wi-Fi bands, Wi-Fi Direct, Bluetooth Classic & LE, Cellular data, metering status, and battery saver state.
- **Intelligent Scoring Matrix**:
  - `LOCAL_WIFI` (Up to 95 pts): Saturated high-bandwidth LAN socket streaming (80+ MB/s).
  - `WIFI_DIRECT` (Up to 90 pts): Direct device-to-device Wi-Fi link without any local router.
  - `HOTSPOT` (Up to 70 pts): Autonomous local AP mode with credentials encoded into QR/NFC.
  - `BLUETOOTH` (Up to 35 pts): Universal fallback when Wi-Fi is disabled or unavailable.
  - `INTERNET_RELAY` (Up to 25 pts): Remote cross-network transfer with end-to-end encryption.
  - `WEB_DROP` (Up to 15 pts): Browser client mode requiring zero app installation on receiver.
  - `OPTICAL_LIGHT` (Up to 10 pts): Zero-radio air-gapped optical transfer for RF-shielded environments.
- **Transparent Fallback Chaining**: Seamlessly migrates in-flight transfers across transports upon connection interruption without dropping the session.

### 2. ⚡ Multi-Tier Transport Engine
- **🪐 Astron LAN Transport (Multi-Stream Accelerated):** Direct TCP socket streaming with framed binary protocol, SHA-256 streaming verification, chunked resume support, and parallel multi-socket pipelines.
- **📶 Bluetooth Classic Transfer (RFCOMM):** Dedicated SPP UUID socket server/client with AES-256-GCM chunk encryption and 16 KB verified frame streaming.
- **🌐 Internet Relay (Remote NAT Traversal):** Ktor WebSocket client connecting peers across cellular networks and separate Wi-Fi connections via zero-knowledge encrypted payloads (`CryptoManager.encryptPayload`).
- **📡 Offline Zero-Router Hotspot Mode:** Android `LocalOnlyHotspot` with WPA2/WPA3 credentials embedded into pairing QR codes. Enables high-speed P2P Wi-Fi transfer anywhere (flights, subways, remote outdoors).
- **🌍 Web Drop (Cross-Platform Zero-App Mode):** Embedded micro HTTP server serving a cosmic dark-mode web portal. Share files seamlessly with **iPhones, iPads, MacBooks, Windows PCs, Linux laptops, and smart TVs** with PIN/CSRF authentication.
- **✨ Light Drop (Adaptive Optical Air-Gap):** High-density sequenced visual QR frame transmission with dynamic FPS scaling and missing-frame recovery.

### 3. ♻️ Crash-Safe Resumability Across Reboots (Room SQLite WAL)
- **Granular Database Tracking:** `AstronDatabase` with WAL mode tracks `TransferSessionEntity` and `TransferFileEntity`.
- **Byte-Level Checkpoints:** Stores exact `offsetBytes` and chunk hashes after every block write.
- **Instant Crash Recovery:** Automatic scan on application startup detects interrupted sessions and presents a one-tap resume card on the Home Screen.

### 4. 📁 Built-in Categorized File Explorer & App Extractor
- **Smart Categories:**
  - 📸 **Photos**: Image MediaStore with fast asynchronous thumbnail loading.
  - 🎥 **Videos**: Video query with runtime duration and resolution badges.
  - 🎵 **Audio**: Audio metadata extraction (artist, album, bitrate).
  - 📄 **Documents**: PDF, Word, Excel, PowerPoint, Text, Code files.
  - 📦 **Installed APKs**: Direct scan of installed user applications with high-res app icons, package IDs, version codes, and direct APK file extraction for sharing.
  - ⬇️ **Downloads**: Recent downloads folder query.
  - 🐘 **Large Files**: Dedicated filter for files > 100 MB for fast space management.
- **Search & Filter:** Real-time debounced instant search and multidimensional sorting (Date, Size, Name).
- **Multi-Selection Bar:** Long-press multi-select mode with item counter, select-all, and instant batch action bar.

### 5. 🔍 Interactive Multi-Format File Previews (`FilePreviewSheet`)
- **Images:** High-resolution Coil display with interactive pinch-to-zoom and pan gestures.
- **Videos:** Native embedded ExoPlayer (`androidx.media3:media3-exoplayer`) playback inside the preview sheet with transport controls.
- **Audio:** ExoPlayer audio streaming with progress seek bar, duration tracking, and playback controls.
- **PDFs:** Native vector rendering of the first page via Android `PdfRenderer`.
- **APKs:** Package label, icon, package name, and version code inspection.
- **ZIP Archives:** Live hierarchy and entry inspection without extracting to disk.

### 6. 📈 Live Telemetry Sparkline Graph (`SpeedGraphView`)
- Real-time rolling transfer speed (MB/s).
- Session peak speed and average speed metrics.
- Smooth Bézier curved canvas graph with dynamic cosmic glow effects.

### 7. 🛡️ Duplicate Detection & Auto-Zipping
- **Duplicate Policy Engine:** Collision detection by filename and SHA-256 hash, offering `REPLACE`, `KEEP_BOTH` (safe numbering `(1)`), and `SKIP`.
- **In-App Zip Bundler & Zip-Slip Protection:** Streaming ZIP generation for directory transfers and strict canonical path verification against Zip-Slip path traversal exploits during extraction.
- **Multi-Batch Sequential Queue:** FIFO queue with batch reordering (`moveUp`), individual batch cancellation, and total size tracking.

### 8. 📱 Universal Android OS Compatibility & Permission Hardening
- Seamless support from **Android 7.0 (API 24)** up to **Android 16+ (API 35+)**.
- **Scoped Storage Protection:** Resilient fallback to app-specific external storage (`context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)`) if public Downloads access is restricted.
- **Runtime Permissions:** Granular media permissions (`READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO`) on Android 13+, legacy storage permissions on Android 9/10, and runtime Bluetooth permissions (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE` with `neverForLocation`).
- **Foreground Service:** Connected device and data sync foreground service types keeping transfers alive when the app is backgrounded or screen locked.
- **Network Security Config:** Offline LAN socket streaming enabled with cleartext traffic configuration.

---

## 🎨 Cosmic Deep-Space Visual Design

- **Theme:** Deep space dark mode (`#070A12`, `#0E1424`, `#162038`).
- **Accents:** Cosmic Cyan (`#2563EB`), Electric Blue (`#3B82F6`), Stellar Violet (`#7C3AED`), and Radiant Amber (`#D97706`).
- **Celestial Canvas:** Interactive procedural twinkling `CosmicStarfield` background.
- **Orbital Animations:** Custom pulsing radar waves and spinning planetary rings inspired by the Astron insignia (`Logo.png`).
- **Acoustic Feedback:** Hardware-synthesized dual-frequency audio chimes via `SoundFeedbackManager` for transfer start, incoming file approval alerts, and SHA-256 verified completions.

---

## 📱 Project & Architecture Directory

```
e:/Astron/
├── apk/
│   ├── app-debug.apk                   # Latest God-Tier verified debug APK (29.6 MB)
│   └── Astron-v1.0.0-debug.apk         # Verified debug release APK (29.6 MB)
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/astron/app/
│   │   │   │   ├── core/
│   │   │   │   │   ├── archive/        # ArchiveManager (in-app ZIP & Zip-Slip protection)
│   │   │   │   │   ├── model/          # TransferItem, Manifest, PeerDevice, TransferSession
│   │   │   │   │   ├── security/       # AES-256-GCM, SHA-256 streaming, Path sanitization
│   │   │   │   │   ├── storage/        # StorageManager, FileBrowserManager, DuplicateDetector
│   │   │   │   │   └── util/           # NetworkUtils, PairingUtils, SoundFeedbackManager, QrCodeGenerator
│   │   │   │   ├── data/
│   │   │   │   │   ├── db/             # Room Database, DAOs, Session & File Entities
│   │   │   │   │   └── repository/     # PreferencesRepository & HistoryRepository
│   │   │   │   ├── transport/
│   │   │   │   │   ├── CapabilityDetector.kt       # Dynamic hardware & network capability scanner
│   │   │   │   │   ├── TransportOrchestrator.kt     # Multi-transport intelligent scoring & fallback brain
│   │   │   │   │   ├── bluetooth/                  # BluetoothClassicTransport (RFCOMM + AES-GCM)
│   │   │   │   │   ├── internet/                   # InternetRelayTransport (WebSocket + E2EE)
│   │   │   │   │   ├── lan/                        # NSD discovery + Multi-stream TCP socket engine
│   │   │   │   │   ├── hotspot/                    # LocalOnlyHotspot + WifiNetworkSpecifier auto-connect
│   │   │   │   │   ├── webdrop/                    # Embedded dark-mode HTTP web server & upload handler
│   │   │   │   │   └── lightdrop/                  # Adaptive optical frame generator & frame tracker
│   │   │   │   ├── transfer/           # TransferEngine, TransferQueue, TransferPersistenceManager
│   │   │   │   ├── service/            # TransferForegroundService (notification & wakelock)
│   │   │   │   └── ui/
│   │   │   │       ├── components/     # SpeedGraphView, FilePreviewSheet, CosmicStarfield, Orbital radar
│   │   │   │       ├── screens/        # Home, Send, Receive, Transfer, FileBrowser, WebDrop, LightDrop
│   │   │   │       ├── navigation/     # NavHost and screen routes
│   │   │   │       └── theme/          # Cosmic theme, typography, color tokens
│   │   │   └── res/                    # Adaptive icons (mdpi to xxxhdpi), colors, network security config
│   │   └── test/                       # 17 comprehensive unit & integration test suites
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

### Pre-Built Debug APK (Direct Testing)
A verified, production-ready debug APK with all God-Tier features is bundled directly in the repository:
- **`apk/app-debug.apk`** (29.6 MB)
- **`apk/Astron-v1.0.0-debug.apk`** (29.6 MB)

To install directly to a connected Android phone or emulator:
```powershell
adb install -r apk/app-debug.apk
```

### Build Debug APK from Source
```powershell
.\gradlew.bat assembleDebug
```
Output APK location:
`app/build/outputs/apk/debug/app-debug.apk`

### Run Unit Tests
```powershell
.\gradlew.bat testDebugUnitTest
```
**17 of 17 test suites execute headless on JVM with 100% pass rate:**
- `TransportOrchestratorTest`: Capability scoring, large-file penalties, battery saver penalties, offline privacy mode, and dynamic fallback.
- `TransferQueueTest`: Multi-batch FIFO queueing, batch reordering (`moveUp`), batch cancellation, and size tracking.
- `DuplicateDetectorTest`: Collision detection, SHA-256 deep hash checks, `SKIP`, `REPLACE`, and `KEEP_BOTH` policies.
- `ArchiveManagerTest`: In-app ZIP bundling, entry hierarchy listing, and Zip-Slip path traversal defense validation.
- `AstronCoreTests`: Cryptographic round-trip tests (AES-256-GCM), filename path-traversal sanitization, streaming SHA-256 verification.
- `TransferEngineTest`: Pause/resume StateFlow toggles, engine lifecycle management.
- `LanTransportIntegrationTest`: Multi-stream socket connections, seek & resume file write sessions.
- `WebDropIntegrationTest`: HTTP server manifest exchange, browser download pipeline.
- `WebDropSecurityTest`: CSRF tokens and PIN security verification.
- `LightDropTest` & `AdaptiveLightDropTest`: High-density optical frame transmission and FPS adaptation.
- `MultiStreamStressTest`: Multi-socket concurrent streaming under load.
- `NfcPairingTest`, `PairingUtilsTest`, `HotspotAndPairingTest`, `WifiDirectStateTest`: Zero-friction device pairing.
- `HapticFeedbackTest`: Rich tactile feedback vibrations.

---

## 🔒 Security Guarantees
1. **End-to-End Cryptography:** AES-256-GCM chunked stream encryption across local and remote transports.
2. **Zero Tracking:** No analytics, no user tracking, no third-party ad SDKs.
3. **Ephemeral Pairing:** One-time pairing tokens and QR code handshakes.
4. **Zip-Slip & Path Traversal Immunity:** Canonical path checks reject arbitrary directory escapes (`../../`).
5. **Sandboxed Output:** Received files are placed in user storage with SHA-256 checksum validation.

---

## 📄 License
Copyright (c) 2026 Astron Core Team. Distributed under the MIT License.
