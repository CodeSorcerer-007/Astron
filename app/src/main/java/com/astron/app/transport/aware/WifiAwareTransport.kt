package com.astron.app.transport.aware

import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.aware.AttachCallback
import android.net.wifi.aware.DiscoverySessionCallback
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.PublishConfig
import android.net.wifi.aware.PublishDiscoverySession
import android.net.wifi.aware.SubscribeConfig
import android.net.wifi.aware.SubscribeDiscoverySession
import android.net.wifi.aware.WifiAwareManager
import android.net.wifi.aware.WifiAwareSession
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.astron.app.core.model.PeerDevice
import com.astron.app.core.model.TransportType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wi-Fi Aware (Neighbor Awareness Networking / NAN) Transport for Astron.
 * Enables zero-router, zero-AP peer-to-peer discovery and direct data path links
 * between nearby devices with low latency and high bandwidth.
 */
@Singleton
class WifiAwareTransport @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "WifiAwareTransport"
        const val AWARE_SERVICE_NAME = "com.astron.transfer"
    }

    private var wifiAwareSession: WifiAwareSession? = null
    private var publishSession: PublishDiscoverySession? = null
    private var subscribeSession: SubscribeDiscoverySession? = null

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    val discoveredPeers: StateFlow<List<PeerDevice>> = _discoveredPeers.asStateFlow()

    fun isSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)
    }

    fun isAvailable(): Boolean {
        if (!isSupported()) return false
        val awareManager = context.getSystemService(Context.WIFI_AWARE_SERVICE) as? WifiAwareManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            awareManager?.isAvailable == true
        } else false
    }

    /**
     * Initializes and attaches to the Wi-Fi Aware hardware subsystem.
     */
    fun attach(onAttached: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (!isAvailable()) {
            onError("Wi-Fi Aware not available on this device")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val awareManager = context.getSystemService(Context.WIFI_AWARE_SERVICE) as? WifiAwareManager
                ?: run {
                    onError("Failed to obtain WifiAwareManager")
                    return
                }

            try {
                awareManager.attach(object : AttachCallback() {
                    override fun onAttached(session: WifiAwareSession) {
                        Log.d(TAG, "Wi-Fi Aware attached successfully")
                        wifiAwareSession = session
                        _isSessionActive.value = true
                        onAttached()
                    }

                    override fun onAttachFailed() {
                        Log.e(TAG, "Wi-Fi Aware attach failed")
                        _isSessionActive.value = false
                        onError("Wi-Fi Aware attach failed")
                    }
                }, null)
            } catch (e: Exception) {
                Log.e(TAG, "Exception attaching to Wi-Fi Aware", e)
                onError(e.message ?: "Attach exception")
            }
        }
    }

    /**
     * Publishes Astron service for nearby peers to discover.
     */
    fun startAdvertising(deviceName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val session = wifiAwareSession ?: return

        val config = PublishConfig.Builder()
            .setServiceName(AWARE_SERVICE_NAME)
            .setServiceSpecificInfo(deviceName.toByteArray(Charsets.UTF_8))
            .build()

        session.publish(config, object : DiscoverySessionCallback() {
            override fun onPublishStarted(session: PublishDiscoverySession) {
                Log.d(TAG, "Wi-Fi Aware publish started: $deviceName")
                publishSession = session
            }

            override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                Log.d(TAG, "Wi-Fi Aware message received from peer: ${message.size} bytes")
            }
        }, null)
    }

    /**
     * Subscribes to Astron service to discover nearby publishing peers.
     */
    fun startDiscovery() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val session = wifiAwareSession ?: return

        val config = SubscribeConfig.Builder()
            .setServiceName(AWARE_SERVICE_NAME)
            .build()

        session.subscribe(config, object : DiscoverySessionCallback() {
            override fun onSubscribeStarted(session: SubscribeDiscoverySession) {
                Log.d(TAG, "Wi-Fi Aware subscribe started")
                subscribeSession = session
            }

            override fun onServiceDiscovered(
                peerHandle: PeerHandle,
                serviceSpecificInfo: ByteArray,
                matchFilter: List<ByteArray>
            ) {
                val peerName = if (serviceSpecificInfo.isNotEmpty()) {
                    String(serviceSpecificInfo, Charsets.UTF_8)
                } else "Wi-Fi Aware Peer"

                Log.d(TAG, "Wi-Fi Aware peer discovered: $peerName (handle: $peerHandle)")
                val peer = PeerDevice(
                    id = "aware_${peerHandle.hashCode()}",
                    name = peerName,
                    hostAddress = "192.168.49.1",
                    port = 52737,
                    transportType = TransportType.WIFI_AWARE
                )

                val current = _discoveredPeers.value.toMutableList()
                if (current.none { it.id == peer.id }) {
                    current.add(peer)
                    _discoveredPeers.value = current
                }
            }
        }, null)
    }

    /**
     * Shuts down all active discovery, publish, and aware sessions.
     */
    fun stop() {
        try {
            publishSession?.close()
            publishSession = null
            subscribeSession?.close()
            subscribeSession = null
            wifiAwareSession?.close()
            wifiAwareSession = null
            _isSessionActive.value = false
            _discoveredPeers.value = emptyList()
            Log.d(TAG, "Wi-Fi Aware stopped cleanly")
        } catch (e: Exception) {
            Log.w(TAG, "Error closing Wi-Fi Aware session", e)
        }
    }
}
