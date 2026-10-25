# 📐 Astron — Architecture Documentation

This document describes the architectural principles, module boundaries, data flow pipelines, and threading model implemented in **Astron**.

---

## 1. System Architecture Overview

Astron adheres to clean architectural separation with distinct layers:

```
┌────────────────────────────────────────────────────────┐
│                   UI & Presentation                     │
│   (Jetpack Compose, Material 3, Navigation Compose)    │
│   - CosmicStarfield Canvas Background                  │
│   - Network & Storage Diagnostics Modal Sheet          │
└───────────────────────────▲────────────────────────────┘
                            │ StateFlow / Events
┌───────────────────────────┴────────────────────────────┐
│                    Transfer Engine                     │
│    (State Machine, Progress EMA, Foreground Service)   │
│    (SoundFeedbackManager Synthesized Audio Feedback)   │
└───────────────▲────────────────────────▲───────────────┘
                │                        │
┌───────────────┴───────────────┐ ┌──────┴───────────────┐
│       Transport Layer         │ │     Storage & SAF    │
│  - LanTransport (Multi-Stream)│ │  - Downloads/Astron  │
│  - HotspotManager (Zero-Router│ │  - Streaming I/O     │
│  - WebDropServer (HTTP Micro) │ │  - Chunked Resume    │
│  - LightDropManager (Optical) │ │                      │
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
- **`model/Models.kt`**: Domain contracts. Defines `TransferItem`, `TransferManifest`, `ManifestItem`, `PeerDevice` (with optional hotspot credentials), `TransportType`, `TransferSession`, `TransferPriority`, and `TransferHistoryRecord`.
- **`security/CryptoManager.kt`**:
  - Authenticated AES-256-GCM symmetric encryption and decryption.
  - Hardware-accelerated SHA-256 streaming hashing via `MessageDigest`.
  - `sanitizeFilename(name)` path traversal defense stripping directory jumps (`../`), null bytes, control chars, and reserved OS symbols.
  - Ephemeral 32-character high-entropy pairing token generation.
- **`storage/StorageManager.kt`**:
  - Android Storage Access Framework (`ContentResolver`) URI resolution.
  - Target directory management in `Downloads/Astron`.
  - `RandomAccessFile` chunked I/O supporting seamless resume from arbitrary byte offsets.
  - Atomic rename of temporary files (`.astron_tmp`) only after SHA-256 checksum verification succeeds.
- **`util/`**:
  - `NetworkUtils.kt`: Discovers local active IPv4 addresses across Wi-Fi and SoftAP hotspot interfaces.
  - `PairingUtils.kt`: Generates and parses unified `astron://pair` QR codes and standard `WIFI:S:..;P:..;;` format.
  - `SoundFeedbackManager.kt`: Hardware-synthesized tone chimes for Transfer Start, Incoming Alerts, and Verified Completion with global one-tap mute state.
  - `QrCodeGenerator.kt`: High-contrast QR bitmaps via ZXing.
- **`nfc/NfcManager.kt`**:
  - NDEF tap-to-pair with automated multi-radio handshake negotiation (Wi-Fi Direct, Bluetooth, LAN, Hotspot) when devices are held back-to-back.

---

### 2.2 Transport Layer (`com.astron.app.transport`)

- **`lan/LanTransport.kt` (Multi-Stream TCP & Dynamic RTT Scaling)**:
  - **Discovery**: Zero-configuration discovery using `NsdManager` advertising `_astron._tcp.` on port `52737`.
  - **Multi-Stream Socket Acceleration**: When `TransferPriority.MAX_SPEED` is enabled and multiple files are queued, spawns up to **3 parallel TCP sockets** with `sessionToken` authorization, saturating high-bandwidth 5 GHz/6 GHz channels up to 80+ MB/s.
  - **Dynamic Chunk RTT Adaptation**: Automatically measures handshake and streaming round-trip time (RTT), dynamically scaling chunk buffer sizes from 32 KB (on high-jitter or congested links) up to 512 KB (on ultra-low latency Wi-Fi 7 / MLO connections).
  - **Session Approval Cache**: Maintains an in-memory `approvedSessions` concurrent set so parallel streams for the same transfer batch bypass duplicate authorization dialogs.
  - **Socket Tuning**: Enforces `tcpNoDelay = true` and dynamic `sendBufferSize`/`receiveBufferSize` scaling from 64 KB up to 256 KB.

- **`hotspot/HotspotManager.kt` (Offline Zero-Router Hotspot)**:
  - **SoftAP Hotspot**: Initiates Android `LocalOnlyHotspot` (Android 8.0+, API 26+) when no external Wi-Fi router is available.
  - **Dynamic Credentials**: Extracts system-assigned SoftAP SSID and WPA2/WPA3 passphrase, packaging them into the receiver's pairing QR code.
  - **Client Auto-Connect**: Uses `WifiNetworkSpecifier` (API 29+) to automatically bind network traffic directly to the receiver's hotspot without affecting the device's internet default routing.

- **`webdrop/WebDropServer.kt` (Zero-App HTTP Micro-Server)**:
  - Embedded micro HTTP server running on port `8080` (auto-falls back across `8080`–`8095`).
  - Serves a self-contained cosmic dark-mode single page web application for cross-platform sharing with iOS, macOS, Windows, and Linux.
  - Chunked `multipart/form-data` uploads and streaming downloads directly into `Downloads/Astron/`.

- **`lightdrop/LightDropManager.kt` (Adaptive Optical Air-Gap)**:
  - Air-gapped visual transfer protocol transmitting sequenced QR frames through display-to-camera optical coupling.
  - **Adaptive Densities**:
    - `RESILIENT` (128 Bytes/frame): Maximum scanning tolerance for low light or rapid movement.
    - `BALANCED` (256 Bytes/frame): Optimal default throughput and camera focus latency.
    - `ULTRA` (512 Bytes/frame): High throughput for 4K / macro smartphone lenses.
  - **Missing Frame Diagnostics**: `ReceiverSession` tracks sparse frame indices (`getMissingFrameIndices()`), allowing real-time UI reporting of dropped frames.

- **`ble/BleBeaconManager.kt` (BLE Background Beaconing)**:
  - Low-power Bluetooth Low Energy advertising (`ASTRON_SERVICE_UUID: 0000a570-0000-1000-8000-00805f9b34fb`) and scanning.
  - Allows instant peer discovery even when one device's screen is locked or Wi-Fi is sleeping.
  - 16-byte packed frame encoding device name, capabilities bitmask, and listening port with calibrated RSSI proximity mapping.

- **`upnp/UpnpPortMapper.kt` (UPnP / NAT-PMP WAN Port Mapping)**:
  - SSDP M-SEARCH discovery of router Internet Gateway Devices (IGD).
  - SOAP RPC control protocol injecting temporary dynamic port mappings (`52737` -> `192.168.1.X:52737`) with automatic lease teardown.
  - Enables direct peer-to-peer socket transfers across the public internet without third-party relay servers.

- **`multicast/MulticastGroupManager.kt` (Wi-Fi Multicast 1-to-Many Group)**:
  - 1-to-many UDP Multicast engine streaming on `239.255.42.99:52738`.
  - Distributes the exact same file batch to dozens of devices simultaneously in classrooms, conferences, or field operations without sequential re-transmissions.
  - **Forward Error Correction (FEC)**: Packs 1 XOR parity packet per 4 data chunks, allowing dropped packets to be mathematically recovered in memory with 0 ms latency.


---

### 2.3 Transfer Management Layer (`com.astron.app.transfer` & `service`)
- **`TransferEngine.kt`**:
  - Central orchestrator coordinating sessions across LAN, Hotspot, Web Drop, and Light Drop.
  - Manages `StateFlow<TransferSession?>` with pause, resume, and cancellation mechanics.
  - Exponential Moving Average (EMA) transfer speed smoothing (`α = 0.25`).
  - Emits sensory audio feedback via `SoundFeedbackManager` on start, alert, and verified completion.
  - Dispatches completed records to `HistoryRepository`.
- **`TransferForegroundService.kt`**:
  - Foreground service with `FOREGROUND_SERVICE_TYPE_DATA_SYNC`.
  - Maintains `PARTIAL_WAKE_LOCK` and Wi-Fi lock during active transfers to prevent Android Doze mode throttling.
  - Interactive notification with live progress bar, pause/resume, cancel, and a quick-action "Mute/Unmute Chimes" toggle for silent background transfers.

---

### 2.4 Presentation Layer (`com.astron.app.ui`)
- **Cosmic Celestial Theme**: Modern OLED-optimized palette featuring Cosmic Cyan, Electric Blue, Stellar Violet, and Surface Elevation tokens.
- **Dynamic Micro-Interactions**:
  - `CosmicStarfield`: Procedurally rendered twinkling canvas background across all primary screens.
  - `DiagnosticsSheet`: Bottom modal displaying live network interfaces, local IPs, active ports, and storage metrics.
  - `TransferScreen`: Holographic speedometer gauge with live MB/s, ETA, and progress arc.

---

## 3. Memory & Threading Model

- **Constant Memory Footprint**: Bounded buffer streaming (`64 KB` – `256 KB` per socket chunk). Memory usage stays under 35 MB regardless of whether a 5 MB photo or a 100 GB file archive is transferred.
- **Coroutines & Thread Safety**:
  - `Dispatchers.IO`: File reads/writes, multi-stream TCP socket handling, HTTP server operations.
  - `Dispatchers.Default`: AES-256-GCM encryption/decryption, SHA-256 streaming hashing, CRC32 checks, and ZXing QR generation.
  - `Dispatchers.Main`: Jetpack Compose UI state binding and interaction dispatch.
