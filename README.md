<div align="center">

<img src="Logo.png" alt="Astron Logo" width="140" />

# 🪐 Astron

**High-Performance Local & Remote File Transfer for Android**

[![Platform](https://img.shields.io/badge/Platform-Android_7.0+_(API_24+)--36-3DDC84?style=flat-square&logo=android&logoColor=white)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Tests](https://img.shields.io/badge/Tests-105_Passing_(100%25)-success?style=flat-square&logo=checkmarx&logoColor=white)](docs/TEST_REPORT.md)
[![License](https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square)](LICENSE)
[![Download APK](https://img.shields.io/badge/Download-APK_(30.6_MB)-orange?style=flat-square&logo=googleplay&logoColor=white)](apk/app-debug.apk)

<br/>

<a href="brag-output/astron-showcase.gif">
  <img src="brag-output/astron-showcase.gif" alt="Astron 20-Second Cinematic Showcase" width="100%" style="border-radius: 12px; box-shadow: 0 8px 30px rgba(37, 99, 235, 0.3);" />
</a>

<p><em>🎬 <b>20-Second Showcase:</b> Wi-Fi 7 MLO, 12 Dynamic Transports, and Crash-Proof Resume</em></p>

</div>

---

### ⚡ Why Astron?

Astron is built for uncompromising transfer speed, offline resilience, and cross-platform flexibility with zero cloud intermediaries.

| Feature | 🪐 Astron | 🍏 Apple AirDrop | 🤖 Google Quick Share | 🌐 LocalSend |
| :--- | :---: | :---: | :---: | :---: |
| **Peak Throughput** | **⚡ 250 MB/s** (Wi-Fi 7 MLO) | ~40–60 MB/s | ~50–80 MB/s | ~40–70 MB/s |
| **Transports** | **🚀 12 Dynamic Engines** | 2 (Wi-Fi + BLE) | 3 (Direct, WebRTC, BT) | 1 (LAN only) |
| **Zero-App Web Sharing** | **✅ Web Drop** (Browser E2EE) | ❌ Apple only | ❌ App required | ❌ App required |
| **Group Transfer** | **✅ 1-to-Many Multicast + FEC** | ❌ 1-to-1 only | ❌ 1-to-1 only | ❌ 1-to-1 only |
| **Air-Gapped Mode** | **✅ Light Drop** (Optical QR stream) | ❌ Radio only | ❌ Radio only | ❌ Radio only |
| **Crash Resumability** | **✅ Byte-Level Seek** (Room WAL) | ❌ Restarts at 0% | ❌ Partial | ❌ Restarts at 0% |
| **Direct Internet P2P** | **✅ Auto UPnP / NAT-PMP** | ❌ Local only | ❌ Cloud relay | ❌ Subnet only |
| **Privacy & Telemetry** | **🔒 100% Private, Zero Tracking** | Apple ID telemetry | Google telemetry | Open source |

---

### 🌐 12 Intelligent Transports

Astron dynamically benchmarks link quality and switches to the fastest available route:

| Category | Transports | Key Capability |
| :--- | :--- | :--- |
| **High Speed** | • Wi-Fi 7 MLO LAN<br/>• Wi-Fi Direct (P2P)<br/>• Offline SoftAP Hotspot | Multi-socket TCP up to 250 MB/s with dynamic 32–512 KB chunk scaling. |
| **Cross-Platform & Broadcast** | • Web Drop (Browser HTTP)<br/>• Wi-Fi Multicast (1-to-Many) | Zero-install downloads for iOS/PC/Mac; XOR FEC for zero-drop group broadcasts. |
| **Air-Gap & Proximity** | • Light Drop (Visual QR)<br/>• BLE Beacons<br/>• Bluetooth Classic | Fully air-gapped optical transfer; background peer discovery with RSSI. |
| **Remote & Hardware** | • UPnP / NAT-PMP Direct WAN<br/>• Internet Relay (Ktor WebSocket)<br/>• Wi-Fi Aware & USB OTG | Seamless router port traversal; physical flash drive transfers. |

---

### 🛡️ Security & Reliability

- **End-to-End Cryptography:** Authenticated AES-256-GCM chunk encryption with HKDF-SHA256 key derivation.
- **Interruption Proof:** Byte offsets saved per block to Room SQLite WAL; broken transfers resume at the exact byte (`OP_FILE_RESUME`).
- **Sandboxed Storage:** Strict canonical path validation prevents Zip-Slip and directory traversal (`../../`).
- **Constant Memory:** Streaming buffer architecture guarantees `< 35 MB` JVM heap usage even on 100+ GB transfers.

---

### 🚀 Quick Start

#### Install Pre-Built APK
Direct download: [`apk/app-debug.apk`](apk/app-debug.apk) (30.6 MB, Android 7.0+ / API 24–36)
```bash
adb install -r apk/app-debug.apk
```

#### Build & Test from Source
```bash
# Build Debug APK
.\gradlew.bat assembleDebug

# Run JVM Unit & Integration Tests (105 tests passing, 0 failures)
.\gradlew.bat testDebugUnitTest
```

---

### 📚 Documentation

- 📐 **[Architecture Guide](docs/ARCHITECTURE.md):** Modular design, threading, and MVI state machine.
- 📡 **[Protocol Specification](docs/PROTOCOL.md):** Binary frame formats, opcodes, and FEC repair math.
- 🧪 **[Test & Verification Report](docs/TEST_REPORT.md):** 105 unit, integration, and security test suites.

---

### 📄 License
Released under the [MIT License](LICENSE). Copyright © 2026 Astron Core Team.
