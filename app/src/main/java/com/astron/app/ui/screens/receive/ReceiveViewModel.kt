package com.astron.app.ui.screens.receive

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astron.app.core.model.TransferManifest
import com.astron.app.core.model.TransferSession
import com.astron.app.core.util.NetworkUtils
import com.astron.app.core.util.QrCodeGenerator
import com.astron.app.data.PreferencesRepository
import com.astron.app.transfer.TransferEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URLEncoder
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

    val localIp: String?
        get() = NetworkUtils.getLocalIpAddress()

    init {
        generateQr()
        viewModelScope.launch {
            deviceName.collect {
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

    fun generateQr() {
        val name = deviceName.value.ifBlank { NetworkUtils.getDefaultDeviceName() }
        val ip = localIp ?: "127.0.0.1"
        val encodedName = try { URLEncoder.encode(name, "UTF-8") } catch (_: Exception) { name }
        val payload = "astron://pair?name=$encodedName&ip=$ip&port=52737"
        _qrBitmap.value = QrCodeGenerator.generateQrBitmap(payload, size = 320)
    }

    fun acceptIncomingTransfer() {
        approvalDeferred?.complete(true)
    }

    fun rejectIncomingTransfer() {
        approvalDeferred?.complete(false)
    }

    fun stopReceiver() {
        transferEngine.onIncomingTransferPrompt = null
        transferEngine.lanTransport.stopAdvertising()
        transferEngine.lanTransport.stopReceiverServer()
    }

    override fun onCleared() {
        super.onCleared()
        stopReceiver()
    }
}
