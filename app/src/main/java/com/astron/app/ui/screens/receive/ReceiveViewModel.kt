package com.astron.app.ui.screens.receive

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astron.app.core.model.TransferManifest
import com.astron.app.core.model.TransferSession
import com.astron.app.core.util.NetworkUtils
import com.astron.app.core.util.PairingUtils
import com.astron.app.core.util.QrCodeGenerator
import com.astron.app.data.PreferencesRepository
import com.astron.app.transfer.TransferEngine
import com.astron.app.transport.hotspot.HotspotConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReceiveViewModel @Inject constructor(
    private val transferEngine: TransferEngine,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    val deviceName: StateFlow<String> = preferencesRepository.deviceNameFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NetworkUtils.getDefaultDeviceName()
        )

    val session: StateFlow<TransferSession?> = transferEngine.currentSession

    private val _pendingManifest = MutableStateFlow<TransferManifest?>(null)
    val pendingManifest: StateFlow<TransferManifest?> = _pendingManifest.asStateFlow()

    private var approvalDeferred: CompletableDeferred<Boolean>? = null

    private val _qrBitmap = MutableStateFlow<Bitmap?>(null)
    val qrBitmap: StateFlow<Bitmap?> = _qrBitmap.asStateFlow()

    private val _isHotspotMode = MutableStateFlow(false)
    val isHotspotMode: StateFlow<Boolean> = _isHotspotMode.asStateFlow()

    val hotspotConfig: StateFlow<HotspotConfig?> = transferEngine.hotspotManager.hotspotConfig

    val localIp: String?
        get() = if (_isHotspotMode.value) {
            hotspotConfig.value?.ipAddress ?: "192.168.43.1"
        } else {
            NetworkUtils.getLocalIpAddress()
        }

    init {
        generateQr()
        viewModelScope.launch {
            deviceName.collect {
                generateQr()
            }
        }
        viewModelScope.launch {
            hotspotConfig.collect {
                generateQr()
            }
        }
    }

    fun startReceiver() {
        transferEngine.onIncomingTransferPrompt = { manifest ->
            val deferred = CompletableDeferred<Boolean>()
            _pendingManifest.value = manifest
            approvalDeferred = deferred
            val approved = deferred.await()
            _pendingManifest.value = null
            approvalDeferred = null
            approved
        }
        transferEngine.startReceiverMode()
        generateQr()
    }

    fun toggleHotspotMode() {
        val next = !_isHotspotMode.value
        _isHotspotMode.value = next
        if (next) {
            transferEngine.hotspotManager.startLocalHotspot(
                onStarted = {
                    generateQr()
                },
                onFailed = {
                    _isHotspotMode.value = false
                    generateQr()
                }
            )
        } else {
            transferEngine.hotspotManager.stopLocalHotspot()
            generateQr()
        }
    }

    fun generateQr() {
        val name = deviceName.value.ifBlank { NetworkUtils.getDefaultDeviceName() }
        val ip = localIp ?: "127.0.0.1"
        val config = hotspotConfig.value

        val payload = if (_isHotspotMode.value && config != null) {
            PairingUtils.createPairingQr(
                name = name,
                ip = config.ipAddress,
                port = PairingUtils.DEFAULT_PORT,
                hotspotSsid = config.ssid,
                hotspotPass = config.passphrase
            )
        } else {
            PairingUtils.createPairingQr(
                name = name,
                ip = ip,
                port = PairingUtils.DEFAULT_PORT
            )
        }
        _qrBitmap.value = QrCodeGenerator.generateQrBitmap(payload, size = 320)
    }

    fun acceptIncomingTransfer() {
        approvalDeferred?.complete(true)
    }

    fun rejectIncomingTransfer() {
        approvalDeferred?.complete(false)
    }

    fun stopReceiver() {
        approvalDeferred?.complete(false)
        approvalDeferred = null
        _pendingManifest.value = null
        transferEngine.onIncomingTransferPrompt = null
        transferEngine.cancelTransfer()
        transferEngine.lanTransport.stopAdvertising()
        transferEngine.lanTransport.stopReceiverServer()
        if (_isHotspotMode.value) {
            transferEngine.hotspotManager.stopLocalHotspot()
            _isHotspotMode.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopReceiver()
    }
}
