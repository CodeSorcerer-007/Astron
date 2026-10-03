package com.astron.app.ui.screens.send

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astron.app.core.model.PeerDevice
import com.astron.app.core.model.TransferItem
import com.astron.app.core.storage.StorageManager
import com.astron.app.transfer.TransferEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SendViewModel @Inject constructor(
    private val transferEngine: TransferEngine,
    private val storageManager: StorageManager
) : ViewModel() {

    private val _selectedItems = MutableStateFlow<List<TransferItem>>(emptyList())
    val selectedItems: StateFlow<List<TransferItem>> = _selectedItems.asStateFlow()

    val discoveredPeers: StateFlow<List<PeerDevice>> = transferEngine.discoveredPeers

    fun initializeWithUris(uris: List<Uri>) {
        if (uris.isEmpty() || _selectedItems.value.isNotEmpty()) return
        addUris(uris)
    }

    fun addUris(uris: List<Uri>) {
        viewModelScope.launch {
            val newItems = uris.map { storageManager.queryTransferItem(it) }
            _selectedItems.update { current ->
                val existingIds = current.map { it.id }.toSet()
                current + newItems.filter { it.id !in existingIds }
            }
        }
    }

    fun addTransferItems(items: List<TransferItem>) {
        _selectedItems.update { current ->
            val existingIds = current.map { it.id }.toSet()
            current + items.filter { it.id !in existingIds }
        }
    }

    fun removeItem(item: TransferItem) {
        _selectedItems.update { current -> current.filter { it.id != item.id } }
    }

    fun clearItems() {
        _selectedItems.value = emptyList()
    }

    fun startDiscovery() {
        transferEngine.startPeerDiscovery()
    }

    fun stopDiscovery() {
        transferEngine.stopPeerDiscovery()
    }

    fun sendToPeer(peer: PeerDevice, onTransferStarted: () -> Unit) {
        val items = _selectedItems.value
        if (items.isEmpty()) return
        transferEngine.startSendTransfer(peer, items)
        onTransferStarted()
    }
}
