# 🪐 Astron — Brag Video & Launch Plan

## Rubric Answers

1. **What is the app?**  
   Astron is the definitive native Android file transfer engine built with Kotlin 2.0, Jetpack Compose, and Material 3, orchestrating 12 diverse transports from Wi-Fi 7 MLO channel bonding (250 MB/s) down to zero-radio optical QR streaming.

2. **What is the funniest or most impressive claim?**  
   *"Whatever the file. Whatever the network. Whatever the distance. Just send it."* — transfers a 100 GB file while consuming under 35 MB of RAM and mathematically reconstructs dropped Wi-Fi packets mid-air at 0 ms latency using XOR Forward Error Correction.

3. **What is the visual hook?**  
   The `HolographicOrbitalSpeedometer`: a sweeping planetary neon plasma arc with an orbiting satellite comet stardust flare tracking the progress tip against a procedurally twinkling `CosmicStarfield` canvas, exploding into supernova shockwave rings upon SHA-256 verified completion.

4. **What should be shown from the actual UI?**  
   The live `TransferScreen` with the holographic orbital gauge roaring at 250 MB/s, the rolling Bézier `SpeedGraphView` sparkline, the dynamic `TransportOrchestrator` real-time capability matrix, and the rich `FilePreviewSheet` playing 4K video instantly via embedded Media3 ExoPlayer.

5. **What is the shortest satisfying video?**  
   **20 seconds** (Pacing: 2.5s Hook → 3.5s Reveal → 10s Rapid-Fire Proofs → 4s Supernova Outro).

6. **What tone fits best?**  
   - **Tone Preset:** `cinematic`
   - **Creative Direction:** *"Sleek sci-fi tech trailer meets relentless raw engineering dominance."*

7. **What should the audio feel like?**  
   Deep space ambient sub-bass swell, exploding into a razor-sharp synthwave pulse; synced to hardware-synthesized dual-frequency acoustic chime alerts (`SoundFeedbackManager`).

8. **What should the share caption say?**  
   *AirDrop and Quick Share walked so Astron could bend space-time at 250 MB/s across 12 transports. 🪐⚡*

9. **What's the user flow worth showing?**  
   Select multi-gigabyte video batch → Instant back-to-back NFC Beam tap → Holographic speedometer rockets to 250 MB/s over Wi-Fi 7 MLO → Router unplugged: zero-drop transparent fallback to Wi-Fi Direct → SHA-256 cryptographic seal with supernova shockwave.

---

## Visual & Design Language
- **Background:** Deep space cosmic dark (`#070A12`, `#0E1424`)
- **Primary Brand Accents:** Cosmic Cyan (`#2563EB`), Electric Blue (`#3B82F6`), Stellar Violet (`#7C3AED`), Radiant Amber (`#D97706`), Emerald Verified (`#10B981`)
- **Typography:** Display: Space Grotesk / Inter Bold; Data/Telemetry: JetBrains Mono
- **Visual Centerpiece:** Real canvas rendering from `HolographicOrbitalSpeedometer.kt` and `SpeedGraphView.kt`

---

## Beat-by-Beat Storyboard (20 Seconds)

### Scene 1: The Hook (0.0s – 2.5s)
- **Visual:** Pitch black space. A faint ringed planet logo glows in the dark. Suddenly, a giant alert slams in with a CRT glitch: *"AIRDROP CANNOT FIND PEER. QUICK SHARE FAILED."*
- **Text on Screen:** `TRANSFER FAILED? NOT IN THIS GALAXY.`
- **Pacing:** 0.6s slam, 1.9s hold.
- **Audio Cue:** Low resonant sub-bass drop, ending in a static record-scratch glitch.

### Scene 2: The Reveal (2.5s – 6.0s)
- **Visual:** The screen ignites into a glowing `CosmicStarfield`. The Astron insignia spins forward into the `HolographicOrbitalSpeedometer`. Neon plasma arcs ignite in electric cyan and violet.
- **Text on Screen:** `MEET ASTRON. 12 TRANSPORTS. ZERO EXCUSES.`
- **Pacing:** 0.8s entrance wipe, 2.7s glorious hold.
- **Audio Cue:** High-energy synth riser hitting a crisp resonant link chime (`TONE_PROP_BEEP`).

### Scene 3: The Raw Speed Flex (6.0s – 9.5s)
- **Visual:** Live transfer begins. The orbital gauge needle swings past 100 MB/s... 180 MB/s... pinning at **250 MB/s**. Below, the `SpeedGraphView` draws an electric blue Bézier curve glowing in real time.
- **Text on Screen:** `WI-FI 7 MLO CHANNEL BONDING • 250 MB/S`
- **Pacing:** Speedometer counts up rapidly (0 → 250 MB/s in 1.2s), then holds.
- **Audio Cue:** Fast rolling percussion synced to speedometer ticks.

### Scene 4: The 12-Transport Cascade (9.5s – 13.5s)
- **Visual:** Rapid cascade of transport badges snapping into view one-by-one:
  1. `⚡ Wi-Fi 7 MLO (250 MB/s)`
  2. `📡 Autonomous SoftAP Hotspot (Zero Router)`
  3. `📢 Wi-Fi Multicast 1-to-Many + XOR FEC`
  4. `🌐 Web Drop (iPhone & Mac Browser Instant)`
  5. `✨ Light Drop (Air-Gapped Optical QR)`
- **Text on Screen:** `WHATEVER THE FILE. WHATEVER THE NETWORK.`
- **Pacing:** Badges snap in every 0.7s, full matrix holds for 1.2s.
- **Audio Cue:** Punchy mechanical UI clicks for each badge arrival.

### Scene 5: Zero-Router & Air-Gap Resilience (13.5s – 16.5s)
- **Visual:** A simulation of an active transfer when Wi-Fi is abruptly disconnected. The `TransportOrchestrator` seamlessly shifts the live socket to Wi-Fi Direct and BLE without dropping a single byte.
- **Text on Screen:** `SEAMLESS FAILOVER. ROOM SQLITE CRASH RESUMABLE.`
- **Pacing:** Transition takes 0.5s, status badge flashes `RECOVERED (0ms)`, holds for 2.5s.
- **Audio Cue:** Bass pulse recovering smoothly without interruption.

### Scene 6: The Supernova Outro (16.5s – 20.0s)
- **Visual:** Transfer hits 100%. The gauge triggers the supernova shockwave celebration: expanding emerald rings, SHA-256 green checkmark badge, and the Astron logo with GitHub badge.
- **Text on Screen:** `ASTRON • 10/10 GOLD MASTER • 100% FREE & OPEN SOURCE`
- **Pacing:** Explosion at 16.5s, logo lockup holds through 20.0s.
- **Audio Cue:** Dual-stage acoustic confirmation chime (`TONE_PROP_ACK` → `TONE_PROP_PROMPT`) followed by a clean sub-bass fadeout.

---

## Music & Audio Cue Guidance
- **Track Style:** Dark modern synthwave / cinematic electronic (120 BPM).
- **Strong Cue Targets:**
  - `t = 2.5s`: Riser drop on Reveal
  - `t = 6.0s`: Kick drum on Speedometer ignition
  - `t = 16.5s`: Tonal resolution on Supernova completion
- **SFX Intent:** All hardware tone chimes correspond exactly to `SoundFeedbackManager.kt`.
