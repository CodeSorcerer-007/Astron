package com.astron.app

import com.astron.app.core.model.PeerDevice
import com.astron.app.core.model.TransferItem
import com.astron.app.core.model.TransportType
import com.astron.app.transfer.TransferQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TransferQueueTest {

    private lateinit var queue: TransferQueue
    private val peerA = PeerDevice(id = "peer-1", name = "Pixel 9 Pro", hostAddress = "192.168.1.10", port = 8080, transportType = TransportType.LOCAL_WIFI)
    private val peerB = PeerDevice(id = "peer-2", name = "Galaxy S24", hostAddress = "192.168.1.20", port = 8080, transportType = TransportType.WIFI_DIRECT)

    @Before
    fun setUp() {
        queue = TransferQueue()
    }

    @Test
    fun testInitialQueueIsEmpty() {
        assertTrue(queue.queue.value.isEmpty())
        assertNull(queue.peek())
        assertNull(queue.dequeue())
    }

    @Test
    fun testEnqueueAndTotalBytesCalculation() {
        val items = listOf(
            TransferItem(id = "1", name = "video.mp4", size = 1000L, mimeType = "video/mp4"),
            TransferItem(id = "2", name = "doc.pdf", size = 500L, mimeType = "application/pdf")
        )

        val batchId = queue.enqueue(peerA, items)
        assertNotNull(batchId)

        val batches = queue.queue.value
        assertEquals(1, batches.size)
        assertEquals(batchId, batches.first().id)
        assertEquals(peerA, batches.first().targetPeer)
        assertEquals(2, batches.first().items.size)
        assertEquals(1500L, batches.first().totalBytes)
    }

    @Test
    fun testPeekDoesNotRemoveHead() {
        val items = listOf(TransferItem(id = "1", name = "file.txt", size = 100L, mimeType = "text/plain"))
        val batchId = queue.enqueue(peerA, items)

        val peeked = queue.peek()
        assertNotNull(peeked)
        assertEquals(batchId, peeked?.id)
        assertEquals(1, queue.queue.value.size)
    }

    @Test
    fun testFifoDequeue() {
        val itemsA = listOf(TransferItem(id = "1", name = "a.txt", size = 10L, mimeType = "text/plain"))
        val itemsB = listOf(TransferItem(id = "2", name = "b.txt", size = 20L, mimeType = "text/plain"))

        val idA = queue.enqueue(peerA, itemsA)
        val idB = queue.enqueue(peerB, itemsB)

        assertEquals(2, queue.queue.value.size)

        val dequeuedFirst = queue.dequeue()
        assertEquals(idA, dequeuedFirst?.id)
        assertEquals(1, queue.queue.value.size)

        val dequeuedSecond = queue.dequeue()
        assertEquals(idB, dequeuedSecond?.id)
        assertEquals(0, queue.queue.value.size)

        assertNull(queue.dequeue())
    }

    @Test
    fun testRemoveBatchById() {
        val id1 = queue.enqueue(peerA, listOf(TransferItem(id = "1", name = "1.txt", size = 10L, mimeType = "text/plain")))
        val id2 = queue.enqueue(peerB, listOf(TransferItem(id = "2", name = "2.txt", size = 20L, mimeType = "text/plain")))
        val id3 = queue.enqueue(peerA, listOf(TransferItem(id = "3", name = "3.txt", size = 30L, mimeType = "text/plain")))

        queue.remove(id2)
        val remaining = queue.queue.value
        assertEquals(2, remaining.size)
        assertEquals(id1, remaining[0].id)
        assertEquals(id3, remaining[1].id)
    }

    @Test
    fun testClearEmptiesQueue() {
        queue.enqueue(peerA, listOf(TransferItem(id = "1", name = "1.txt", size = 10L, mimeType = "text/plain")))
        queue.enqueue(peerB, listOf(TransferItem(id = "2", name = "2.txt", size = 20L, mimeType = "text/plain")))
        assertEquals(2, queue.queue.value.size)

        queue.clear()
        assertTrue(queue.queue.value.isEmpty())
        assertNull(queue.peek())
    }

    @Test
    fun testMoveUpReordersBatches() {
        val id1 = queue.enqueue(peerA, listOf(TransferItem(id = "1", name = "1.txt", size = 10L, mimeType = "text/plain")))
        val id2 = queue.enqueue(peerB, listOf(TransferItem(id = "2", name = "2.txt", size = 20L, mimeType = "text/plain")))

        // Move first item up — should do nothing
        queue.moveUp(id1)
        assertEquals(id1, queue.queue.value[0].id)
        assertEquals(id2, queue.queue.value[1].id)

        // Move second item up — should swap with first
        queue.moveUp(id2)
        assertEquals(id2, queue.queue.value[0].id)
        assertEquals(id1, queue.queue.value[1].id)
    }
}
