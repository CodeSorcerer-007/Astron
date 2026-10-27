package com.astron.app.transport.p2p

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class WifiDirectState {
    data object Idle : WifiDirectState()
    data object Discovering : WifiDirectState()
    data class GroupCreated(val groupName: String, val passphrase: String, val goIpAddress: String) : WifiDirectState()
    data class Connected(val isGroupOwner: Boolean, val groupOwnerIp: String) : WifiDirectState()
    data class Error(val message: String) : WifiDirectState()
}

/**
 * Native Android Wi-Fi Direct (P2P) Manager.
 * Enables zero-router, autonomous device-to-device high-speed wireless linking.
 */
class WifiDirectManager(private val context: Context) {

    companion object {
        const val TAG = "WifiDirectManager"
        const val DEFAULT_GO_IP = "192.168.49.1"
    }

    private val p2pManager: WifiP2pManager? by lazy {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    }

    private var channel: WifiP2pManager.Channel? = null
    private var isReceiverRegistered = false

    private val _state = MutableStateFlow<WifiDirectState>(WifiDirectState.Idle)
    val state: StateFlow<WifiDirectState> = _state.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<WifiP2pDevice>> = _discoveredDevices.asStateFlow()

    private val p2pReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    if (state != WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                        Log.w(TAG, "Wi-Fi Direct is disabled on device")
                    }
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    requestPeers()
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO, NetworkInfo::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
                    }
                    if (networkInfo?.isConnected == true) {
                        requestConnectionInfo()
                    }
                }
            }
        }
    }

    fun initialize() {
        if (channel != null) return
        try {
            channel = p2pManager?.initialize(context, Looper.getMainLooper(), null)
            registerReceiver()
            Log.d(TAG, "Wi-Fi Direct initialized successfully")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed initializing Wi-Fi Direct", t)
        }
    }

    private fun registerReceiver() {
        if (isReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        }
        try {
            ContextCompat.registerReceiver(
                context,
                p2pReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            isReceiverRegistered = true
        } catch (t: Throwable) {
            Log.w(TAG, "Could not register P2P receiver: ${t.message}")
        }
    }

    fun startDiscovery(onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        initialize()
        val ch = channel ?: run {
            onError("Channel not initialized")
            return
        }

        try {
            p2pManager?.discoverPeers(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    _state.value = WifiDirectState.Discovering
                    onSuccess()
                }

                override fun onFailure(reason: Int) {
                    val err = "Peer discovery failed with code: $reason"
                    _state.value = WifiDirectState.Error(err)
                    onError(err)
                }
            })
        } catch (t: Throwable) {
            onError("Discovery exception: ${t.message}")
        }
    }

    fun createAutonomousGroup(onCreated: (WifiDirectState.GroupCreated) -> Unit = {}, onError: (String) -> Unit = {}) {
        initialize()
        val ch = channel ?: run {
            onError("Channel not initialized")
            return
        }

        try {
            p2pManager?.createGroup(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    requestGroupInfo(onCreated, onError)
                }

                override fun onFailure(reason: Int) {
                    val err = "Group creation failed with code: $reason"
                    _state.value = WifiDirectState.Error(err)
                    onError(err)
                }
            })
        } catch (t: Throwable) {
            onError("Group creation exception: ${t.message}")
        }
    }

    private fun requestGroupInfo(onCreated: (WifiDirectState.GroupCreated) -> Unit, onError: (String) -> Unit) {
        val ch = channel ?: return
        try {
            p2pManager?.requestGroupInfo(ch) { group: WifiP2pGroup? ->
                if (group != null) {
                    val networkName = group.networkName ?: "DIRECT-Astron"
                    val passphrase = group.passphrase ?: ""
                    val createdState = WifiDirectState.GroupCreated(
                        groupName = networkName,
                        passphrase = passphrase,
                        goIpAddress = DEFAULT_GO_IP
                    )
                    _state.value = createdState
                    onCreated(createdState)
                } else {
                    onError("Group information unavailable")
                }
            }
        } catch (t: Throwable) {
            onError("Group info exception: ${t.message}")
        }
    }

    private fun requestPeers() {
        val ch = channel ?: return
        try {
            p2pManager?.requestPeers(ch) { peers: WifiP2pDeviceList? ->
                _discoveredDevices.value = peers?.deviceList?.toList() ?: emptyList()
            }
        } catch (_: Throwable) {}
    }

    private fun requestConnectionInfo() {
        val ch = channel ?: return
        try {
            p2pManager?.requestConnectionInfo(ch) { info: WifiP2pInfo? ->
                if (info != null && info.groupFormed) {
                    val goIp = info.groupOwnerAddress?.hostAddress ?: DEFAULT_GO_IP
                    _state.value = WifiDirectState.Connected(
                        isGroupOwner = info.isGroupOwner,
                        groupOwnerIp = goIp
                    )
                }
            }
        } catch (_: Throwable) {}
    }

    fun teardown() {
        try {
            val ch = channel
            if (ch != null) {
                p2pManager?.removeGroup(ch, null)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    ch.close()
                }
            }
            channel = null
            if (isReceiverRegistered) {
                context.unregisterReceiver(p2pReceiver)
                isReceiverRegistered = false
            }
        } catch (_: Throwable) {}
        _state.value = WifiDirectState.Idle
        _discoveredDevices.value = emptyList()
    }
}
