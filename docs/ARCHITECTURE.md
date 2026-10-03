# 📐 Astron — Architecture Documentation

This document describes the architectural principles, module boundaries, data flow pipelines, and threading model implemented in **Astron**.

---

## 1. System Architecture Overview

Astron adheres to clean architectural separation with distinct layers:

```
┌────────────────────────────────────────────────────────┐
│                   UI & Presentation                     │
│   (Jetpack Compose, Material 3, Navigation Compose)    │
└───────────────────────────▲────────────────────────────┘
                            │ StateFlow / Events
┌───────────────────────────┴────────────────────────────┐
│                    Transfer Engine                     │
│    (State Machine, Progress EMA, Foreground Service)   │
└───────────────▲────────────────────────▲───────────────┘
                │                        │
┌───────────────┴───────────────┐ ┌──────┴───────────────┐
│       Transport Layer         │ │     Storage & SAF    │
│  - LanTransport (NSD + TCP)   │ │  - Downloads/Astron  │
│  - WebDropServer (HTTP)       │ │  - Streaming I/O     │
│  - LightDropManager (Optical) │ │  - Chunked Resume    │
└───────────────▲───────────────┘ └──────▲───────────────┘
                │                        │
┌───────────────┴────────────────────────┴───────────────┐
│               Core Security & Utilities                │
│  - AES-256-GCM Encryption / SHA-256 Checksums         │
│  - Path Traversal Shield & Ephemeral Pairing Tokens    │
└────────────────────────────────────────────────────────┘
```

---

## 2. Module Boundaries & Responsibilities

### 2.1 Core Layer (`com.astron.app.core`)
- **`model/Models.kt`**: Domain contracts. Defines `TransferItem`, `TransferManifest`, `ManifestItem`, `PeerDevice`, `TransportType`, `TransferSession`, `TransferPriority`, and `TransferHistoryRecord`.
- **`security/CryptoManager.kt`**:
  - Provides authenticated AES-256-GCM symmetric encryption and decryption.
  - Implements bounded hardware-accelerated SHA-256 streaming hashing via `MessageDigest`.
  - Enforces `sanitizeFilename(name)` to strip directory traversal sequences (`../`, null bytes, control chars, and reserved OS characters).
  - Generates secure ephemeral pairing tokens and one-time transfer keys.
- **`storage/StorageManager.kt`**:
  - Resolves URIs via Android Storage Access Framework (`ContentResolver`).
  - Manages target directory resolution in `Downloads/Astron`.
  - Implements `RandomAccessFile` chunked I/O allowing seamless resume from arbitrary byte offsets.
  - Safely renames temp files (`.astron_tmp`) only after cryptographic validation passes.
- **`util/NetworkUtils.kt` & `QrCodeGenerator.kt`**:
  - Discovers local active IPv4 addresses across Wi-Fi and hotspot interfaces.
  - Generates crisp QR code bitmaps via ZXing for pairing and Web Drop URLs.
  - Formats bytes, ETA, and transfer speeds with human readability.

### 2.2 Transport Layer (`com.astron.app.transport`)
- **`lan/LanTransport.kt`**:
  - **Discovery**: Zero-configuration discovery using `NsdManager` advertising and discovering service `_astron._tcp.` on port 8989.
  - **Socket Server/Client**: Low-overhead TCP streaming socket communication. Reads and writes length-prefixed manifest JSON followed by chunked binary file streams.
  - **Progress Callbacks**: Emits byte counters to transfer engine every chunk.
- **`webdrop/WebDropServer.kt`**:
  - Micro HTTP server running on port 8080.
  - Serves an embedded cosmic dark-mode single-page application (SPA) with responsive drag-and-drop file upload and download list.
  - Streams files using chunked HTTP transfer encoding, enabling zero-app downloads and uploads from any browser.
- **`lightdrop/LightDropManager.kt`**:
  - Air-gapped, zero-radio transfer pipeline.
  - Segments data into discrete frames (`ASTL:1:<sessionId>:<index>:<total>:<crc32>:<base64>`).
  - Transmits visual frames at configurable framerates (10-25 FPS) for optical reception via camera preview scanning.

### 2.3 Transfer Management Layer (`com.astron.app.transfer` & `service`)
- **`TransferEngine.kt`**:
  - Central orchestrator coordinating all active sessions.
  - Manages `StateFlow<TransferSession?>` observed by UI and services.
  - Calculates Exponential Moving Average (EMA) transfer speeds (`α = 0.25`) for jitter-free rate gauges.
  - Dispatches transfer records to `HistoryRepository`.
- **`TransferForegroundService.kt`**:
  - Runs with `FOREGROUND_SERVICE_TYPE_DATA_SYNC`.
  - Maintains a CPU wake-lock and Wi-Fi lock during active transfers to prevent OS throttling.
  - Renders a real-time progress notification with cancellation intent.

### 2.4 Data Persistence Layer (`com.astron.app.data`)
- **`PreferencesRepository.kt`**: Jetpack DataStore managing user preferences (Device Name, Transfer Priority, Auto-Accept, Haptic Feedback).
- **`HistoryRepository.kt`**: Jetpack DataStore storing timestamped transfer history records with search and clearing capabilities.

### 2.5 UI Presentation Layer (`com.astron.app.ui`)
- **`theme/`**: Theme tokens, typography (Inter/Space Grotesk style), and cosmic dark palette.
- **`components/`**:
  - `AstronLogoHeader`: Interactive planetary ring animation with logo.
  - `ProximityRadarSheet`: Fullscreen or modal radar with dynamic distance rings and discovered peers.
  - `IncomingTransferDialog`: Clear peer confirmation prompt with file count, size, and accept/reject actions.
- **`screens/`**:
  - `HomeScreen`: Central dashboard with large Send/Receive hero controls, Web Drop & Light Drop buttons, and recent activity.
  - `SendScreen`: File picker, selection tray, peer discovery radar.
  - `ReceiveScreen`: Passive receiver beacon, pairing QR code, incoming authorization dialog.
  - `TransferScreen`: Active transfer cockpit with orbital progress meter, live speed gauge, time remaining, pause/cancel controls, and verification confirmation.
  - `WebDropScreen`: Browser sharing portal, live connection logs, IP/port display, and QR code.
  - `LightDropScreen`: Dual-mode screen for optical transmitter and camera receiver.
  - `HistoryScreen`: Detailed logs of previous transfers with status chips and file sizes.
  - `SettingsScreen`: Device name customization, priority presets, and diagnostics dialog.

---

## 3. Memory & Threading Model

- **Constant Memory Footprint**: Bounded buffer streaming (`64 KB` for TCP, `1 MB` for Web Drop). Memory usage stays under 35 MB regardless of whether a 5 MB photo or a 50 GB 4K video is transferred.
- **Dispatchers**:
  - `Dispatchers.IO`: File reads/writes, TCP socket connections, HTTP server handling.
  - `Dispatchers.Default`: Cryptographic operations, frame serialization, CRC32 calculations.
  - `Dispatchers.Main`: UI composition and state rendering.
