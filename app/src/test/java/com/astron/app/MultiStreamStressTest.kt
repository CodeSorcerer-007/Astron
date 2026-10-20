package com.astron.app

import com.astron.app.core.security.CryptoManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.Random

class MultiStreamStressTest {

    @Test
    fun testMultiStreamChunkReassemblyAndOrdering() {
        val totalChunks = 64
        val chunkSize = 16 * 1024 // 16 KB chunks = 1 MB total
        val totalBytes = totalChunks * chunkSize
        val expectedBuffer = ByteArray(totalBytes)
        val random = Random(42L)
        random.nextBytes(expectedBuffer)

        // Split into chunks
        data class StreamChunk(val index: Int, val offset: Long, val data: ByteArray)
        val chunks = (0 until totalChunks).map { i ->
            val start = i * chunkSize
            val slice = expectedBuffer.copyOfRange(start, start + chunkSize)
            StreamChunk(i, start.toLong(), slice)
        }

        // Shuffle chunks to simulate multi-stream parallel socket arrival
        val shuffledChunks = chunks.shuffled(Random(1337L))

        // Target reassembly file buffer
        val reassembledBuffer = ByteArray(totalBytes)
        val chunksReceived = BooleanArray(totalChunks)

        shuffledChunks.forEach { chunk ->
            System.arraycopy(chunk.data, 0, reassembledBuffer, chunk.offset.toInt(), chunk.data.size)
            chunksReceived[chunk.index] = true
        }

        // Verify 100% of chunks received
        assertTrue(chunksReceived.all { it })
        // Verify perfect byte-for-byte fidelity with original
        assertTrue(expectedBuffer.contentEquals(reassembledBuffer))
    }

    @Test
    fun testStreamingSha256ChecksumMatchesFullBuffer() {
        val dataSize = 512 * 1024 // 512 KB
        val rawData = ByteArray(dataSize)
        Random(999L).nextBytes(rawData)

        // 1. Direct one-shot SHA-256
        val digest = MessageDigest.getInstance("SHA-256")
        val expectedHash = digest.digest(rawData).joinToString("") { "%02x".format(it) }

        // 2. Incremental streaming SHA-256 across simulated 32KB socket buffers
        val stream = ByteArrayInputStream(rawData)
        val calculatedStreamHash = CryptoManager.computeSha256(stream)

        assertEquals(expectedHash, calculatedStreamHash)
    }

    @Test
    fun testStreamingSha256DetectsSingleBitCorruption() {
        val dataSize = 256 * 1024
        val rawData = ByteArray(dataSize)
        Random(777L).nextBytes(rawData)

        val cleanHash = CryptoManager.computeSha256(ByteArrayInputStream(rawData))

        // Flip 1 bit in the middle of the payload
        val corruptedData = rawData.copyOf()
        corruptedData[128 * 1024] = (corruptedData[128 * 1024].toInt() xor 0x01).toByte()

        val corruptedHash = CryptoManager.computeSha256(ByteArrayInputStream(corruptedData))

        assertNotEquals(cleanHash, corruptedHash)
    }

    @Test
    fun testMultiStreamResumeFromInterruptedOffset() {
        val totalFileSize = 10_000_000L // 10 MB
        val chunkSize = 512 * 1024L // 512 KB
        val totalChunks = ((totalFileSize + chunkSize - 1) / chunkSize).toInt() // 20 chunks

        // Simulate interruption at 65% (13 chunks received)
        val completedChunksCount = 13
        val existingBytesOnDisk = completedChunksCount * chunkSize

        val remainingChunksToFetch = (completedChunksCount until totalChunks).map { chunkIndex ->
            val offset = chunkIndex * chunkSize
            val length = minOf(chunkSize, totalFileSize - offset)
            chunkIndex to (offset to length)
        }

        assertEquals(7, remainingChunksToFetch.size)
        assertEquals(13, remainingChunksToFetch.first().first)
        assertEquals(existingBytesOnDisk, remainingChunksToFetch.first().second.first)
        assertEquals(19, remainingChunksToFetch.last().first)

        val totalRemainingBytes = remainingChunksToFetch.sumOf { it.second.second }
        assertEquals(totalFileSize - existingBytesOnDisk, totalRemainingBytes)
    }

    @Test
    fun testExponentialMovingAverageSpeedSmoothing() {
        var smoothedSpeedBps = 0L
        val alpha = 0.25f // 25% new sample, 75% historical

        val sampleSpeeds = listOf(
            20_000_000L, // 20 MB/s
            85_000_000L, // 85 MB/s burst
            95_000_000L, // 95 MB/s
            80_000_000L  // 80 MB/s steady
        )

        sampleSpeeds.forEach { instantSpeed ->
            smoothedSpeedBps = if (smoothedSpeedBps == 0L) {
                instantSpeed
            } else {
                ((alpha * instantSpeed) + ((1f - alpha) * smoothedSpeedBps)).toLong()
            }
        }

        // Exponential smoothing dampens the instantaneous burst (95 MB/s) to ~58.2 MB/s
        assertTrue(smoothedSpeedBps in 58_000_000L..59_000_000L)
        assertTrue(smoothedSpeedBps < 95_000_000L)
        assertTrue(smoothedSpeedBps > 20_000_000L)
    }
}
