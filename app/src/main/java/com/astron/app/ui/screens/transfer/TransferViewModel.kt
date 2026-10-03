package com.astron.app.ui.screens.transfer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astron.app.core.model.TransferSession
import com.astron.app.data.PreferencesRepository
import com.astron.app.transfer.TransferEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class TransferViewModel @Inject constructor(
    private val transferEngine: TransferEngine,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    val session: StateFlow<TransferSession?> = transferEngine.currentSession
    val isPaused: StateFlow<Boolean> = transferEngine.isPaused

    val hapticsEnabled: StateFlow<Boolean> = preferencesRepository.hapticsEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true
        )

    fun togglePause() {
        transferEngine.togglePause()
    }

    fun cancelTransfer() {
        transferEngine.cancelTransfer()
    }

    fun dismissSession() {
        transferEngine.dismissSession()
    }
}
