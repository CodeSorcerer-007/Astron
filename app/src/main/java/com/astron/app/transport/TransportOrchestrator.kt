package com.astron.app.transport

import android.content.Context
import android.util.Log
import com.astron.app.core.model.PeerDevice
import com.astron.app.core.model.TransportType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * Scoring weight assigned to each transport based on available hardware and network conditions.
 * Higher score = preferred choice. Score 0 means transport is unavailable.
 */
data class TransportScore(
    val type: TransportType,
    val score: Int,
    val estimatedSpeedMbps: Float,
    val reason: String
)

/**
 * TransportOrchestrator — The decision brain for Astron.
 *
 * Scores all available transports and selects the optimal one automatically.
 * Handles transparent fallback chaining when a transport fails mid-transfer.
 *
 * Scoring matrix (max 100 per transport):
 *   LOCAL_WIFI   : up to 95  (fastest, reliable, LAN always preferred)
 *   WIFI_DIRECT  : up to 90  (no AP needed, high bandwidth)
 *   HOTSPOT      : up to 70  (AP mode, works when both devices nearby)
 *   BLUETOOTH    : up to 35  (slow but universally available fallback)
 *   INTERNET     : up to 25  (remote transfers, penalized for metered/slow)
 *   OPTICAL      : up to 10  (completely offline, very limited bandwidth)
 *   WEB_DROP     : up to 15  (browser compat, no app needed on receiver)
 */
@Singleton
class TransportOrchestrator @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "TransportOrchestrator"
    }

    private val _selectedTransport = MutableStateFlow<TransportType?>(null)
    val selectedTransport: StateFlow<TransportType?> = _selectedTransport.asStateFlow()

    private val _allScores = MutableStateFlow<List<TransportScore>>(emptyList())
    val allScores: StateFlow<List<TransportScore>> = _allScores.asStateFlow()

    /**
     * Evaluates all transports against current device capabilities and returns
     * the ranked list (best first). Also updates [selectedTransport].
     *
     * @param fileSizeBytes File(s) total size — used to prefer faster transports for large files.
     * @param preferOffline If true, penalizes internet-based transports (privacy mode).
     * @param forceType Force a specific transport type (user override).
     */
    fun evaluate(
        fileSizeBytes: Long = 0L,
        preferOffline: Boolean = false,
        forceType: TransportType? = null,
        capabilitiesOverride: DeviceCapabilities? = null
    ): List<TransportScore> {
        val caps = capabilitiesOverride ?: CapabilityDetector.refresh(context)
        Log.d(TAG, "Evaluating transports. Caps: $caps | fileSizeBytes=$fileSizeBytes")

        val scores = buildList {
            // ── LOCAL_WIFI (LAN) ──────────────────────────────────────────────────
            if (caps.hasLocalNetwork && caps.isWifiConnected) {
                val isWifi7 = caps.isWifi7Supported || caps.hasWifiMlo
                val baseScore = if (isWifi7) 98 else 95
                val speed = if (isWifi7) 250f else 80f
                val reason = if (isWifi7) {
                    "Wi-Fi 7 MLO (Multi-Link Operation) channel bonding active — Ultra High Throughput (up to 320 MHz / 5.8 Gbps)"
                } else {
                    "Same Wi-Fi network detected — highest speed available"
                }
                val batteryPenalty = if (caps.isBatterySaverOn) -5 else 0
                add(TransportScore(
                    type = TransportType.LOCAL_WIFI,
                    score = (baseScore + batteryPenalty).coerceIn(0, 100),
                    estimatedSpeedMbps = speed,
                    reason = reason
                ))
            } else {
                add(TransportScore(
                    type = TransportType.LOCAL_WIFI,
                    score = 0,
                    estimatedSpeedMbps = 0f,
                    reason = "Not on a shared local Wi-Fi network"
                ))
            }

            // ── WIFI_DIRECT ───────────────────────────────────────────────────────
            if (caps.hasWifiDirect && caps.isWifiEnabled) {
                val isWifi7 = caps.isWifi7Supported || caps.hasWifiMlo
                val baseScore = if (isWifi7) 92 else 90
                val speed = if (isWifi7) 120f else 60f
                val reason = if (isWifi7) {
                    "Wi-Fi Direct with MLO multi-channel aggregation — high-speed peer-to-peer"
                } else {
                    "Wi-Fi Direct available — high-speed peer-to-peer"
                }
                val batteryPenalty = if (caps.isBatterySaverOn) -10 else 0
                add(TransportScore(
                    type = TransportType.WIFI_DIRECT,
                    score = (baseScore + batteryPenalty).coerceIn(0, 100),
                    estimatedSpeedMbps = speed,
                    reason = reason
                ))
            } else {
                add(TransportScore(
                    type = TransportType.WIFI_DIRECT,
                    score = 0,
                    estimatedSpeedMbps = 0f,
                    reason = "Wi-Fi Direct not available or Wi-Fi disabled"
                ))
            }

            // ── WIFI_AWARE (NAN) ──────────────────────────────────────────────────
            if (caps.hasWifiAware && caps.isWifiEnabled) {
                val baseScore = 88
                val batteryPenalty = if (caps.isBatterySaverOn) -10 else 0
                add(TransportScore(
                    type = TransportType.WIFI_AWARE,
                    score = (baseScore + batteryPenalty).coerceIn(0, 100),
                    estimatedSpeedMbps = 45f,
                    reason = "Wi-Fi Aware (NAN) available — zero-router peer-to-peer data link"
                ))
            } else {
                add(TransportScore(
                    type = TransportType.WIFI_AWARE,
                    score = 0,
                    estimatedSpeedMbps = 0f,
                    reason = "Wi-Fi Aware (NAN) not supported by hardware or disabled"
                ))
            }

            // ── HOTSPOT ───────────────────────────────────────────────────────────
            if (caps.hasWifi && caps.isWifiEnabled) {
                val baseScore = 70
                val batteryPenalty = if (caps.isBatterySaverOn) -15 else 0
                add(TransportScore(
                    type = TransportType.HOTSPOT,
                    score = (baseScore + batteryPenalty).coerceIn(0, 100),
                    estimatedSpeedMbps = 30f,
                    reason = "Hotspot mode possible — creates local AP"
                ))
            } else {
                add(TransportScore(
                    type = TransportType.HOTSPOT,
                    score = 0,
                    estimatedSpeedMbps = 0f,
                    reason = "Wi-Fi not available for hotspot"
                ))
            }

            // ── BLUETOOTH (Classic RFCOMM) ────────────────────────────────────────
            if (caps.hasBluetoothClassic && caps.isBluetoothEnabled) {
                val baseScore = 35
                // Penalize Bluetooth for large transfers (> 100 MB) — it's just too slow
                val sizePenalty = if (fileSizeBytes > 100 * 1024 * 1024L) -15 else 0
                val batteryPenalty = if (caps.isBatterySaverOn) -5 else 0
                add(TransportScore(
                    type = TransportType.BLUETOOTH,
                    score = (baseScore + sizePenalty + batteryPenalty).coerceIn(0, 100),
                    estimatedSpeedMbps = 2f,
                    reason = "Bluetooth Classic — universal fallback, slower"
                ))
            } else {
                add(TransportScore(
                    type = TransportType.BLUETOOTH,
                    score = 0,
                    estimatedSpeedMbps = 0f,
                    reason = "Bluetooth not available or disabled"
                ))
            }

            // ── INTERNET RELAY ────────────────────────────────────────────────────
            if (caps.hasInternet && !preferOffline) {
                val baseScore = 25
                val meteredPenalty = if (caps.isInternetMetered) -10 else 0
                val batteryPenalty = if (caps.isBatterySaverOn) -5 else 0
                // Large files over internet are fine if not metered
                val sizeBonus = if (fileSizeBytes > 500 * 1024 * 1024L && !caps.isInternetMetered) 5 else 0
                add(TransportScore(
                    type = TransportType.INTERNET_RELAY,
                    score = (baseScore + meteredPenalty + batteryPenalty + sizeBonus).coerceIn(0, 100),
                    estimatedSpeedMbps = if (caps.networkType == NetworkType.WIFI) 20f else 5f,
                    reason = if (caps.isInternetMetered) "Internet relay — metered connection" else "Internet relay — remote transfer"
                ))
            } else {
                add(TransportScore(
                    type = TransportType.INTERNET_RELAY,
                    score = 0,
                    estimatedSpeedMbps = 0f,
                    reason = if (preferOffline) "Internet relay disabled (privacy mode)" else "No internet connection"
                ))
            }

            // ── WEB_DROP ──────────────────────────────────────────────────────────
            if (caps.hasLocalNetwork || caps.hasInternet) {
                add(TransportScore(
                    type = TransportType.WEB_DROP,
                    score = 15,
                    estimatedSpeedMbps = 15f,
                    reason = "Browser-based — receiver needs no app installed"
                ))
            } else {
                add(TransportScore(
                    type = TransportType.WEB_DROP,
                    score = 0,
                    estimatedSpeedMbps = 0f,
                    reason = "No network for WebDrop"
                ))
            }

            // ── OPTICAL LIGHT DROP ────────────────────────────────────────────────
            if (caps.hasCamera) {
                add(TransportScore(
                    type = TransportType.OPTICAL_LIGHT,
                    score = 10,
                    estimatedSpeedMbps = 0.1f,
                    reason = "Optical — zero radio, works in RF-blocked environments"
                ))
            } else {
                add(TransportScore(
                    type = TransportType.OPTICAL_LIGHT,
                    score = 0,
                    estimatedSpeedMbps = 0f,
                    reason = "No camera for optical transfer"
                ))
            }

            // ── MULTICAST GROUP ──────────────────────────────────────────────────
            if (caps.hasLocalNetwork && caps.isWifiConnected) {
                add(TransportScore(
                    type = TransportType.MULTICAST_GROUP,
                    score = 85,
                    estimatedSpeedMbps = 50f,
                    reason = "1-to-many UDP multicast broadcast with FEC error recovery"
                ))
            }

            // ── DIRECT WAN (UPnP) ────────────────────────────────────────────────
            if (caps.hasLocalNetwork && caps.hasInternet) {
                add(TransportScore(
                    type = TransportType.UPNP_DIRECT,
                    score = 80,
                    estimatedSpeedMbps = 40f,
                    reason = "Direct router WAN port mapping — bypasses internet relay"
                ))
            }

            // ── BLE BEACON ───────────────────────────────────────────────────────
            if (caps.hasBle && caps.isBluetoothEnabled) {
                add(TransportScore(
                    type = TransportType.BLE_BEACON,
                    score = 30,
                    estimatedSpeedMbps = 0.5f,
                    reason = "Low-power BLE background beaconing and proximity discovery"
                ))
            }
        }

        val ranked = scores.sortedByDescending { it.score }
        _allScores.value = ranked

        // Apply force override or pick best available
        val best = if (forceType != null) {
            ranked.find { it.type == forceType && it.score > 0 }
                ?: ranked.firstOrNull { it.score > 0 }
        } else {
            ranked.firstOrNull { it.score > 0 }
        }

        _selectedTransport.value = best?.type
        Log.d(TAG, "Selected transport: ${best?.type} (score=${best?.score}) | ${best?.reason}")
        return ranked
    }

    /**
     * Returns the recommended fallback transport excluding the given [currentType].
     * Called when a transfer fails mid-session and a transparent switch is needed.
     */
    fun getFallback(
        currentType: TransportType,
        capabilitiesOverride: DeviceCapabilities? = null
    ): TransportType? {
        val scores = _allScores.value.ifEmpty {
            evaluate(capabilitiesOverride = capabilitiesOverride)
        }
        return scores
            .filter { it.type != currentType && it.score > 0 }
            .maxByOrNull { it.score }
            ?.type
    }

    /**
     * Human-readable badge for the given transport type, e.g. "⚡ High-Speed LAN"
     */
    fun getBadgeFor(type: TransportType): String = type.badge

    /**
     * Returns a localized description of why the current transport was selected.
     */
    fun getSelectionReason(): String {
        val selected = _selectedTransport.value ?: return "No transport available"
        return _allScores.value.find { it.t == selected }?.reason
            ?: selected.displayName
    }

    private val TransportScore.t get() = this.type
}
