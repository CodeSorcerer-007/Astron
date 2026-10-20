# 📡 Astron — Protocol Specifications

This document defines the wire formats, framing models, and handshake sequences used by Astron's transfer mechanisms: **Astron LAN (Multi-Stream)**, **Zero-Router Direct Hotspot**, **Web Drop**, and **Light Drop (Adaptive Optical)**.

---

## 1. Astron LAN Protocol (Multi-Stream TCP Socket Transport)

### 1.1 Discovery via NSD (mDNS) & Direct QR Pairing
- **Service Type**: `_astron._tcp.`
- **Default Port**: `52737` (configurable / auto-fallback to `8989`)
- **TXT Record Attributes**:
  - `name`: Human-readable device name (e.g., `Astron Galaxy S24`)
  - `model`: Device hardware model string
  - `version`: Protocol version integer (`1`)
  - `token`: Ephemeral session pairing token

#### Pairing URI Format
```
astron://pair?name={deviceName}&ip={ipAddress}&port={port}&h_ssid={optionalHotspotSsid}&h_pass={optionalHotspotPass}
```

When `h_ssid` and `h_pass` are present, the initiating device recognizes that the receiver is operating in **Offline Zero-Router Hotspot Mode** and connects directly using `WifiNetworkSpecifier` before initiating the socket handshake.

---

### 1.2 Handshake & Session Authentication
All multi-byte integers are encoded in **Big-Endian (Network Byte Order)**.

```
Client (Sender)                               Server (Receiver)
       |                                              |
       | ------------ TCP Connect (Port 52737) ------> |
       |                                              |
       | ------------ [Magic: 0x41535452] ----------> |  ("ASTR" = 0x41535452)
       | ------------ [Opcode: 0x01 (HANDSHAKE)] ---> |
       | ------------ [Session ID UTF-8] -----------> |
       | ------------ [Sender Name UTF-8] ----------> |
       | ------------ [Session Token UTF-8] --------> |
       |                                              |
       | <----------- [Opcode: 0x02 (HANDSHAKE_RESP)] |  (Receiver validates or prompts)
       |                                              |
```

### 1.3 Multi-Stream Parallel Socket Acceleration
When `TransferPriority.MAX_SPEED` is configured and multiple files are queued:
- The sender spawns up to **3 parallel TCP socket streams** to the receiver.
- Each parallel stream conducts the `OP_HANDSHAKE_REQ` with the same `sessionId`.
- The receiver maintains an in-memory `approvedSessions` concurrent set; secondary parallel streams with an already-approved `sessionId` bypass repetitive user acceptance prompts and begin immediate high-throughput streaming.
- Sockets configure `tcpNoDelay = true` and `sendBufferSize`/`receiveBufferSize` up to **256 KB** to fully saturate gigabit 5 GHz / 6 GHz Wi-Fi links.

#### Manifest JSON Structure
```json
{
  "sessionId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "senderDeviceName": "Astron Pixel 9 Pro",
  "timestamp": 1727960000000,
  "totalBytes": 104857600,
  "items": [
    {
      "id": "item-001",
      "name": "cosmic_voyage.mp4",
      "size": 104857600,
      "mimeType": "video/mp4",
      "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
      "startOffset": 0
    }
  ]
}
```

### 1.4 Binary Chunk Framing & Resume Offsets
For each file item in the manifest:

```
┌───────────────────────────┬──────────────────────────────────────────┐
│ Offset Marker (8 bytes)   │ Int64 BE start byte offset (for resume) │
├───────────────────────────┼──────────────────────────────────────────┤
│ Stream Payload (N bytes)  │ Raw binary chunks (64 KB - 256 KB each) │
├───────────────────────────┼──────────────────────────────────────────┤
│ SHA-256 Digest (32 bytes) │ Hardware SHA-256 validation digest       │
└───────────────────────────┴──────────────────────────────────────────┘
```

When all file bytes are streamed, the receiver checks the calculated SHA-256 hash against the manifest. If valid, the file is atomically committed to `Downloads/Astron/`.

---

## 2. Zero-Router Direct Hotspot Architecture

When operating in locations without an external Wi-Fi router (outdoors, flights, subways, or secure labs):
1. Receiver activates Android `LocalOnlyHotspot` (Android 8.0+, API 26+).
2. The operating system assigns a dedicated SoftAP SSID (e.g. `AndroidShare_8492`) and WPA2/WPA3 passphrase.
3. Receiver encodes these credentials directly into the unified pairing QR code.
4. Sender scans the QR code; if not already on the same subnet, sender's `HotspotManager` utilizes Android `WifiNetworkSpecifier` (API 29+) to automatically bind to the receiver's local hotspot without touching cellular or system routing.
5. Standard high-speed TCP socket streaming begins over direct 5 GHz Wi-Fi at up to **80+ MB/s**.

---

## 3. Web Drop Protocol (Zero-App HTTP Micro-Server)

Web Drop allows any modern browser on iOS, macOS, Windows, Linux, or Android to transfer files without installing an app.

- **Port**: `8080` (auto-selected `8080` – `8095`)
- **Endpoints**:
  - `GET /`: Serves the embedded dark-mode HTML5/CSS3/JS Web Drop Client.
  - `GET /api/manifest`: Returns JSON manifest of files shared by the host.
  - `GET /download?id={itemId}`: Streams requested file with `Content-Disposition: attachment` and `Accept-Ranges: bytes` for browser-native parallel downloading.
  - `POST /upload`: Accepts standard `multipart/form-data` uploads directly from browser drag-and-drop into `Downloads/Astron/`.
  - `GET /api/status`: Returns host battery, device name, and active connection metrics.

---

## 4. Light Drop Protocol (Adaptive Zero-Radio Optical Air-Gap)

Light Drop uses visual high-density QR code sequencing to transmit files through screen-to-camera optical coupling. Designed for airplane mode, secure air-gapped facilities, or high-EMF radio silence.

### 4.1 Adaptive Optical Densities
| Density Profile | Chunk Size | Recommended Use Case |
| :--- | :--- | :--- |
| **Resilient** | 128 Bytes | Low-light, high-motion, or older camera sensors |
| **Balanced** | 256 Bytes | Default optimal speed and scanning reliability |
| **Ultra-Dense** | 512 Bytes | Maximum throughput for high-resolution 4K/macro cameras |

### 4.2 Visual Frame Wire Format
Each visual frame is encoded as a compact ASCII string:

```
ASTL:<version>:<sessionId>:<frameIndex>:<totalFrames>:<crc32>:<base64Payload>
```

Where:
- `ASTL`: Protocol magic string identifier (`0x4153544C`).
- `version`: Protocol version integer (`1`).
- `sessionId`: Short unique hex integer for the transfer session.
- `frameIndex`: 0-indexed integer of the current packet.
- `totalFrames`: Total number of packets required to reconstruct the file.
- `crc32`: Long decimal CRC32 checksum of the decoded binary payload chunk.
- `base64Payload`: Standard Base64 encoded payload chunk.

### 4.3 Sparse Reconstruction & Missing Frame Diagnostics
1. Receiver camera scans QR frames at 12–25 FPS.
2. Receiver verifies CRC32 checksum against decoded payload bytes.
3. Received frames are indexed into a sparse map: `frames[frameIndex] = chunk`.
4. `getMissingFrameIndices()` tracks non-consecutive missed frames due to camera shake or frame drops.
5. Missing frame counts and indices (e.g. `Missing 2 frames: #3, #7`) are reported in real-time in the UI.
6. Sender loops continuously until all frames are acknowledged or captured.
7. Upon full reassembly, entire stream is verified with SHA-256 before disk persistence.

---

## 5. Acoustic Sensory Feedback Protocol

Astron utilizes hardware-synthesized audio cues via `SoundFeedbackManager` to provide instantaneous feedback without external audio asset bloat:
- **Transfer Start**: 80 ms resonant beep (`TONE_PROP_BEEP`) confirming link establishment.
- **Incoming Approval Alert**: Dual 100 ms alert chimes (`TONE_PROP_PROMPT`) notifying receiver of inbound file authorization.
- **Transfer Complete**: Two-stage harmonious confirmation chime (`TONE_PROP_ACK` -> 140 ms delay -> `TONE_PROP_PROMPT`) signalling SHA-256 verification success.
