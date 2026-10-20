package com.astron.app.transfer

import com.astron.app.core.model.PeerDevice
import com.astron.app.core.model.TransferItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class QueuedTransferBatch(
    val id: String = UUID.randomUUID().toString(),
    val targetPeer: PeerDevice,
    val items: List<TransferItem>,
    val addedAt: Long = System.currentTimeMillis(),
    val totalBytes: Long = items.sumOf { it.size }
)

@Singleton
class TransferQueue @Inject constructor() {

    private val _queue = MutableStateFlow<List<QueuedTransferBatch>>(emptyList())
    val queue: StateFlow<List<QueuedTransferBatch>> = _queue.asStateFlow()

    fun enqueue(peer: PeerDevice, items: List<TransferItem>): String {
        val batch = QueuedTransferBatch(targetPeer = peer, items = items)
        _queue.update { it + batch }
        return batch.id
    }

    fun dequeue(): QueuedTransferBatch? {
        val current = _queue.value
        if (current.isEmpty()) return null
        val next = current.first()
        _queue.value = current.drop(1)
        return next
    }

    fun peek(): QueuedTransferBatch? = _queue.value.firstOrNull()

    fun remove(batchId: String) {
        _queue.update { list -> list.filter { it.id != batchId } }
    }

    fun clear() {
        _queue.value = emptyList()
    }

    fun moveUp(batchId: String) {
        _queue.update { list ->
            val index = list.indexOfFirst { it.id == batchId }
            if (index > 0) {
                val mutable = list.toMutableList()
                val item = mutable.removeAt(index)
                mutable.add(index - 1, item)
                mutable
            } else list
        }
    }
}
