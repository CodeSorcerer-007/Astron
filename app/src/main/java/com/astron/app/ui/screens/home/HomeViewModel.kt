package com.astron.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astron.app.core.model.TransferHistoryRecord
import com.astron.app.core.util.AppFileManager
import com.astron.app.core.util.NetworkUtils
import com.astron.app.data.HistoryRepository
import com.astron.app.data.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import java.io.File
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val historyRepository: HistoryRepository
) : ViewModel() {

    val deviceName: StateFlow<String> = preferencesRepository.deviceNameFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NetworkUtils.getDefaultDeviceName()
        )

    val recentTransfers: StateFlow<List<TransferHistoryRecord>> = historyRepository.historyFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _localIpAddress = MutableStateFlow<String?>(NetworkUtils.getLocalIpAddress())
    val localIpAddress: StateFlow<String?> = _localIpAddress.asStateFlow()

    private val _storageInfo = MutableStateFlow(AppFileManager.getStorageInfo())
    val storageInfo: StateFlow<Pair<Long, Long>> = _storageInfo.asStateFlow()

    private val _latestReceivedFile = MutableStateFlow<File?>(AppFileManager.getLatestReceivedFile())
    val latestReceivedFile: StateFlow<File?> = _latestReceivedFile.asStateFlow()

    fun refreshState() {
        _localIpAddress.value = NetworkUtils.getLocalIpAddress()
        _storageInfo.value = AppFileManager.getStorageInfo()
        _latestReceivedFile.value = AppFileManager.getLatestReceivedFile()
    }
}
