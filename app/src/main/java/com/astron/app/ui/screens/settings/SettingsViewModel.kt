package com.astron.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astron.app.core.model.TransferPriority
import com.astron.app.core.util.NetworkUtils
import com.astron.app.data.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    val deviceName: StateFlow<String> = preferencesRepository.deviceNameFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NetworkUtils.getDefaultDeviceName()
        )

    val transferPriority: StateFlow<TransferPriority> = preferencesRepository.transferPriorityFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TransferPriority.BALANCED
        )

    val autoAcceptTrusted: StateFlow<Boolean> = preferencesRepository.autoAcceptTrustedFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    val hapticsEnabled: StateFlow<Boolean> = preferencesRepository.hapticsEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true
        )

    val darkMode: StateFlow<Boolean> = preferencesRepository.darkModeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true
        )

    fun updateDeviceName(name: String) {
        viewModelScope.launch {
            preferencesRepository.setDeviceName(name)
        }
    }

    fun updateTransferPriority(priority: TransferPriority) {
        viewModelScope.launch {
            preferencesRepository.setTransferPriority(priority)
        }
    }

    fun setAutoAcceptTrusted(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAutoAcceptTrusted(enabled)
        }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setHapticsEnabled(enabled)
        }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setDarkMode(enabled)
        }
    }
}
