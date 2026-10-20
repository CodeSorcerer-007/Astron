package com.astron.app.transport.hotspot

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.Closeable

data class HotspotConfig(
    val ssid: String,
    val passphrase: String,
    val ipAddress: String = "192.168.43.1",
    val port: Int = 52737,
    val isActive: Boolean = true
)

class HotspotManager(private val context: Context) : Closeable {

    companion object {
        const val TAG = "HotspotManager"
        const val DEFAULT_HOTSPOT_IP = "192.168.43.1"
    }

    private val wifiManager: WifiManager? = try {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    } catch (_: Throwable) {
        null
    }

    private val connectivityManager: ConnectivityManager? = try {
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    } catch (_: Throwable) {
        null
    }

    private var reservation: Any? = null // WifiManager.LocalOnlyHotspotReservation
    private var activeNetworkCallback: ConnectivityManager.NetworkCallback? = null

    private val _hotspotConfig = MutableStateFlow<HotspotConfig?>(null)
    val hotspotConfig: StateFlow<HotspotConfig?> = _hotspotConfig.asStateFlow()

    private val _isHotspotRunning = MutableStateFlow(false)
    val isHotspotRunning: StateFlow<Boolean> = _isHotspotRunning.asStateFlow()

    private val _connectionStatus = MutableStateFlow<String?>(null)
    val connectionStatus: StateFlow<String?> = _connectionStatus.asStateFlow()

    /**
     * Starts a Local-Only Wi-Fi Hotspot on Android 8.0+ (Oreo, API 26+).
     * This provides direct peer-to-peer Wi-Fi capability when no external Wi-Fi router is present.
     */
    fun startLocalHotspot(
        onStarted: (config: HotspotConfig) -> Unit = {},
        onFailed: (reason: String) -> Unit = {}
    ) {
        if (_isHotspotRunning.value) {
            _hotspotConfig.value?.let { onStarted(it) }
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                wifiManager?.startLocalOnlyHotspot(
                    object : WifiManager.LocalOnlyHotspotCallback() {
                        override fun onStarted(res: WifiManager.LocalOnlyHotspotReservation) {
                            super.onStarted(res)
                            reservation = res
                            val config = res.wifiConfiguration
                            val ssid: String
                            val pass: String

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val softApConfig = res.softApConfiguration
                                ssid = softApConfig.ssid ?: "Astron-Hotspot"
                                pass = softApConfig.passphrase ?: "astron2026"
                            } else {
                                @Suppress("DEPRECATION")
                                ssid = config?.SSID ?: "Astron-Hotspot"
                                @Suppress("DEPRECATION")
                                pass = config?.preSharedKey ?: "astron2026"
                            }

                            val cleanSsid = ssid.removeSurrounding("\"")
                            val cleanPass = pass.removeSurrounding("\"")

                            val activeConfig = HotspotConfig(
                                ssid = cleanSsid,
                                passphrase = cleanPass,
                                ipAddress = DEFAULT_HOTSPOT_IP,
                                isActive = true
                            )
                            _hotspotConfig.value = activeConfig
                            _isHotspotRunning.value = true
                            Log.d(TAG, "Local-Only Hotspot started: $cleanSsid")
                            onStarted(activeConfig)
                        }

                        override fun onStopped() {
                            super.onStopped()
                            _isHotspotRunning.value = false
                            _hotspotConfig.value = null
                            reservation = null
                            Log.d(TAG, "Local-Only Hotspot stopped")
                        }

                        override fun onFailed(reason: Int) {
                            super.onFailed(reason)
                            _isHotspotRunning.value = false
                            _hotspotConfig.value = null
                            val reasonMsg = "Hotspot start failed with code: $reason"
                            Log.e(TAG, reasonMsg)
                            onFailed(reasonMsg)
                        }
                    },
                    null
                )
            } catch (e: Throwable) {
                Log.e(TAG, "Exception starting local hotspot", e)
                onFailed(e.message ?: "Failed to start hotspot")
            }
        } else {
            onFailed("Local-only hotspot requires Android 8.0+ (API 26)")
        }
    }

    /**
     * Stops the running local-only hotspot and frees network reservation.
     */
    fun stopLocalHotspot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                (reservation as? WifiManager.LocalOnlyHotspotReservation)?.close()
            } catch (e: Throwable) {
                Log.w(TAG, "Error closing hotspot reservation", e)
            }
        }
        reservation = null
        _isHotspotRunning.value = false
        _hotspotConfig.value = null
    }

    /**
     * SENDER: Connects to a receiver's hotspot credentials directly on Android 10+ (Q, API 29+).
     */
    fun connectToHotspot(
        ssid: String,
        passphrase: String,
        onConnected: () -> Unit = {},
        onFailed: (String) -> Unit = {}
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                disconnectFromHotspot()

                val specifier = WifiNetworkSpecifier.Builder()
                    .setSsid(ssid)
                    .setWpa2Passphrase(passphrase)
                    .build()

                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .setNetworkSpecifier(specifier)
                    .build()

                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        super.onAvailable(network)
                        connectivityManager?.bindProcessToNetwork(network)
                        _connectionStatus.value = "Connected to $ssid"
                        Log.d(TAG, "Bound to hotspot network: $ssid")
                        onConnected()
                    }

                    override fun onUnavailable() {
                        super.onUnavailable()
                        _connectionStatus.value = "Failed to connect to $ssid"
                        Log.e(TAG, "Hotspot unavailable: $ssid")
                        onFailed("Could not connect to hotspot $ssid")
                    }

                    override fun onLost(network: Network) {
                        super.onLost(network)
                        _connectionStatus.value = "Hotspot disconnected"
                        Log.w(TAG, "Hotspot connection lost: $ssid")
                    }
                }

                activeNetworkCallback = callback
                connectivityManager?.requestNetwork(request, callback)
                _connectionStatus.value = "Connecting to $ssid..."
            } catch (e: Throwable) {
                Log.e(TAG, "Error requesting hotspot network connection", e)
                onFailed(e.message ?: "Failed to connect")
            }
        } else {
            // Android 9 or below
            _connectionStatus.value = "Please connect to Wi-Fi '$ssid' in Settings"
            onFailed("Manual connection needed on Android 9 or lower")
        }
    }

    /**
     * Unbinds process from hotspot network and releases connection request.
     */
    fun disconnectFromHotspot() {
        try {
            activeNetworkCallback?.let {
                connectivityManager?.unregisterNetworkCallback(it)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                connectivityManager?.bindProcessToNetwork(null)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error unregistering network callback", e)
        }
        activeNetworkCallback = null
        _connectionStatus.value = null
    }

    override fun close() {
        stopLocalHotspot()
        disconnectFromHotspot()
    }
}
