package com.astron.app

import com.astron.app.transport.multicast.MulticastGroupManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class MulticastGroupTest {

    @Test
    fun testComputeCrc32() {
        val testData = "Astron Multicast 1-to-Many Group Stream".toByteArray(Charsets.UTF_8)
        val crc1 = MulticastGroupManager.computeCrc32(testData)
        val crc2 = MulticastGroupManager.computeCrc32(testData)
        assertEquals(crc1, crc2)
        assertTrue(crc1 != 0L)
    }

    @Test
    fun testXorParitySingleChunk() {
        val chunk = byteArrayOf(1, 2, 3, 4, 5)
        val parity = MulticastGroupManager.xorParity(listOf(chunk))
        assertArrayEquals(chunk, parity)
    }

    @Test
    fun testXorParityEmpty() {
        val parity = MulticastGroupManager.xorParity(emptyList())
        assertEquals(0, parity.size)
    }

    @Test
    fun testFecRecoveryAllPositions() {
        val random = Random(42)
        val chunkSize = 1400

        val c0 = ByteArray(chunkSize).also { random.nextBytes(it) }
        val c1 = ByteArray(chunkSize).also { random.nextBytes(it) }
        val c2 = ByteArray(chunkSize).also { random.nextBytes(it) }
        val c3 = ByteArray(chunkSize).also { random.nextBytes(it) }
        val allChunks = listOf(c0, c1, c2, c3)

        val parity = MulticastGroupManager.xorParity(allChunks)
        assertEquals(chunkSize, parity.size)

        // Test losing c0
        val recovered0 = MulticastGroupManager.recoverMissingChunk(listOf(c1, c2, c3), parity)
        assertArrayEquals(c0, recovered0)

        // Test losing c1
        val recovered1 = MulticastGroupManager.recoverMissingChunk(listOf(c0, c2, c3), parity)
        assertArrayEquals(c1, recovered1)

        // Test losing c2
        val recovered2 = MulticastGroupManager.recoverMissingChunk(listOf(c0, c1, c3), parity)
        assertArrayEquals(c2, recovered2)

        // Test losing c3
        val recovered3 = MulticastGroupManager.recoverMissingChunk(listOf(c0, c1, c2), parity)
        assertArrayEquals(c3, recovered3)
    }

    @Test
    fun testFecRecoveryVariableLengthChunks() {
        // Even if chunks have variable sizes (e.g. final block of a file)
        val c0 = byteArrayOf(10, 20, 30, 40, 50)
        val c1 = byteArrayOf(1, 2, 3)
        val c2 = byteArrayOf(99, 88, 77, 66)

        val parity = MulticastGroupManager.xorParity(listOf(c0, c1, c2))
        val recovered1 = MulticastGroupManager.recoverMissingChunk(listOf(c0, c2), parity)

        // c1 was length 3, recovered array is length of max chunk (5), prefix matches c1
        for (i in c1.indices) {
            assertEquals(c1[i], recovered1[i])
        }
    }
}
