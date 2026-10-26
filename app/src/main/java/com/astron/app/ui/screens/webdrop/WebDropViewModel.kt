package com.astron.app.ui.screens.webdrop

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astron.app.core.model.TransferItem
import com.astron.app.core.util.NetworkUtils
import com.astron.app.core.util.QrCodeGenerator
import com.astron.app.data.PreferencesRepository
import com.astron.app.transfer.TransferEngine
import android.content.Context
import com.astron.app.service.AstronNotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class WebDropViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val transferEngine: TransferEngine,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    val deviceName: StateFlow<String> = preferencesRepository.deviceNameFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NetworkUtils.getDefaultDeviceName()
        )

    private val _uploadedFiles = MutableStateFlow<List<File>>(emptyList())
    val uploadedFiles: StateFlow<List<File>> = _uploadedFiles.asStateFlow()

    private val initialIp = NetworkUtils.getLocalIpAddress() ?: "127.0.0.1"
    private val _serverUrl = MutableStateFlow("http://$initialIp:8080")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _qrBitmap = MutableStateFlow<Bitmap?>(QrCodeGenerator.generateQrBitmap("http://$initialIp:8080", size = 320))
    val qrBitmap: StateFlow<Bitmap?> = _qrBitmap.asStateFlow()

    private val _sessionPin = MutableStateFlow("")
    val sessionPin: StateFlow<String> = _sessionPin.asStateFlow()

    fun startServer(filesToShare: List<TransferItem>) {
        viewModelScope.launch {
            val name = preferencesRepository.deviceNameFlow.first()
            transferEngine.webDropServer.startServer(
                deviceName = name,
                filesToShare = filesToShare,
                onServerReady = {
                    val url = transferEngine.webDropServer.webDropUrl
                        ?: "http://${NetworkUtils.getLocalIpAddress() ?: "127.0.0.1"}:${transferEngine.webDropServer.actualPort}?token=${transferEngine.webDropServer.currentSessionToken}"
                    val pin = transferEngine.webDropServer.currentSessionToken
                    _serverUrl.value = url
                    _sessionPin.value = pin
                    _qrBitmap.value = QrCodeGenerator.generateQrBitmap(url, size = 320)
                    AstronNotificationHelper.showWebDropNotification(context, url)
                },
                onFileUploaded = { file ->
                    _uploadedFiles.update { current -> listOf(file) + current }
                }
            )
        }
    }

    fun stopServer() {
        transferEngine.webDropServer.stopServer()
        AstronNotificationHelper.dismissWebDropNotification(context)
    }

    override fun onCleared() {
        super.onCleared()
        stopServer()
    }
}
