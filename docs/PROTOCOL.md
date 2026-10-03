# 📡 Astron — Protocol Specifications

This document defines the wire formats, framing models, and handshake sequences used by Astron's transfer mechanisms: **Astron LAN**, **Web Drop**, and **Light Drop**.

---

## 1. Astron LAN Protocol (TCP Socket Transport)

### 1.1 Discovery via NSD (mDNS)
- **Service Type**: `_astron._tcp.`
- **Default Port**: `8989`
- **TXT Record Attributes**:
  - `name`: Human-readable device name (e.g., `Astron Galaxy S24`)
  - `model`: Device hardware model string
  - `version`: Protocol version integer (`1`)
  - `token`: Ephemeral session pairing token

### 1.2 Handshake & Manifest Exchange
All multi-byte integers are encoded in **Big-Endian (Network Byte Order)**.

```
Client (Sender)                               Server (Receiver)
       |                                              |
       | ------------ TCP Connect (Port 8989) ------> |
       |                                              |
       | ------------ [Magic: 0x41535452] ----------> |  ("ASTR")
       | ------------ [Version: 0x01] --------------> |  (UInt8)
       | ------------ [Manifest Length: 4 bytes] ---> |  (UInt32 BE)
       | ------------ [Manifest JSON Payload] ------> |  (UTF-8)
       |                                              |
       | <----------- [Response: 0x06 (ACK) / 0x15] - |  (0x06 = Accept, 0x15 = Reject)
       |                                              |
```

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

### 1.3 Binary Streaming Stream Frame
For each file item in the manifest:

```
┌───────────────────────────┬──────────────────────────────────────────┐
│ Offset Marker (8 bytes)   │ Int64 BE start byte offset (for resume) │
├───────────────────────────┼──────────────────────────────────────────┤
│ Stream Payload (N bytes)  │ Raw binary chunks (64 KB each)           │
├───────────────────────────┼──────────────────────────────────────────┤
│ SHA-256 Digest (32 bytes) │ Hardware SHA-256 validation digest       │
└───────────────────────────┴──────────────────────────────────────────┘
```

When file bytes are fully streamed, receiver verifies the calculated SHA-256 hash matches the manifest. If valid, the file is atomically moved to `Downloads/Astron/`.

---

## 2. Web Drop Protocol (HTTP Micro-Server)

Web Drop allows any web browser on iOS, macOS, Windows, Linux, or Android to transfer files without installing an app.

- **Port**: `8080`
- **Endpoints**:
  - `GET /`: Serves the embedded dark-mode HTML5/CSS3/JS Web Drop Client.
  - `GET /api/manifest`: Returns JSON manifest of files shared by the host.
  - `GET /download?id={itemId}`: Streams requested file with `Content-Disposition: attachment` and `Accept-Ranges: bytes` for browser-native parallel downloading.
  - `POST /upload`: Accepts standard `multipart/form-data` uploads directly from browser drag-and-drop into `Downloads/Astron/`.
  - `GET /api/status`: Returns host battery, device name, and active connection metrics.

---

## 3. Light Drop Protocol (Zero-Radio Optical Air-Gap)

Light Drop uses visual high-density QR code sequencing to transmit files through the physical display to a camera sensor. Designed for airplane mode, secure air-gapped zones, or zero-Wi-Fi environments.

### 3.1 Visual Frame Wire Format
Each visual frame is encoded as a compact ASCII string:

```
ASTL:<version>:<sessionId>:<frameIndex>:<totalFrames>:<crc32>:<base64Payload>
```

Where:
- `ASTL`: Protocol magic string identifier.
- `version`: Protocol version integer (`1`).
- `sessionId`: Short 8-character unique hex identifier for the transfer session.
- `frameIndex`: 0-indexed integer of the current packet.
- `totalFrames`: Total number of packets required to reconstruct the file.
- `crc32`: 8-digit hexadecimal CRC32 checksum of the decoded binary payload chunk.
- `base64Payload`: Standard Base64 encoded payload chunk (typically 256–512 bytes per frame).

### 3.2 Reconstruction Pipeline
1. Receiver camera scans QR frame.
2. Receiver validates CRC32 against decoded bytes.
3. Received frame is stored in a sparse byte buffer: `frames[frameIndex] = chunk`.
4. Progress is tracked as `framesReceived / totalFrames`.
5. Missing frames are displayed on receiver's screen as an optical request code so transmitter can re-flash missing frames.
6. Once `framesReceived == totalFrames`, bytes are concatenated and verified with SHA-256.
