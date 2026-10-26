package com.astron.app

import com.astron.app.transport.lightdrop.LightDropManager
import com.astron.app.transport.lightdrop.OpticalFrame
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile
import java.util.Base64
import java.util.zip.CRC32

class LightDropTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val lightDropManager = LightDropManager()

    @Test
    fun testEncodeAndDecodeRoundtrip() {
        val payload = "Astron Optical LightDrop Protocol Test Payload 2026".toByteArray(Charsets.UTF_8)
        val frames = lightDropManager.encodeBytesToFrames(payload, sessionId = 101)

        assertTrue(frames.isNotEmpty())
        val session = LightDropManager.ReceiverSession(101, frames.size)

        for (frame in frames) {
            val serialized = lightDropManager.serializeFrame(frame)
            val parsed = lightDropManager.parseFrame(serialized)
            assertNotNull(parsed)
            assertEquals(frame.sessionId, parsed!!.sessionId)
            assertEquals(frame.frameIndex, parsed.frameIndex)
            assertEquals(frame.totalFrames, parsed.totalFrames)
            assertEquals(frame.checksumCrc32, parsed.checksumCrc32)
            session.addFrame(parsed)
        }

        assertTrue(session.isComplete())
        assertEquals(1.0f, session.progress, 0.001f)
        assertArrayEquals(payload, session.reconstructBytes())
    }

    @Test
    fun testCorruptedCrc32FrameRejected() {
        val payload = "Important Financial Transaction Confirmation Data".toByteArray(Charsets.UTF_8)
        val frames = lightDropManager.encodeBytesToFrames(payload, sessionId = 202)
        val frame = frames.first()

        val validSerialized = lightDropManager.serializeFrame(frame)
        // Corrupt the CRC by appending 999 to CRC segment
        val parts = validSerialized.split(":").toMutableList()
        parts[5] = (parts[5].toLong() + 1).toString()
        val corruptedSerialized = parts.joinToString(":")

        val parsed = lightDropManager.parseFrame(corruptedSerialized)
        assertNull("Corrupted frame CRC must return null", parsed)
    }

    @Test
    fun testShuffledOutOfOrderFramesReconstructsCorrectly() {
        // Create 2 KB of pseudo-random data to span multiple frames
        val originalBytes = ByteArray(2048) { (it % 256).toByte() }
        val frames = lightDropManager.encodeBytesToFrames(originalBytes, sessionId = 303)
        assertTrue(frames.size > 1)

        val session = LightDropManager.ReceiverSession(303, frames.size)
        val shuffledFrames = frames.shuffled()

        for (frame in shuffledFrames) {
            session.addFrame(frame)
        }

        assertTrue(session.isComplete())
        assertArrayEquals(originalBytes, session.reconstructBytes())
    }

    @Test
    fun testDuplicateFramesIgnoredWithoutCorruptingData() {
        val payload = "Deduplication Verification".toByteArray(Charsets.UTF_8)
        val frames = lightDropManager.encodeBytesToFrames(payload, sessionId = 404)
        val session = LightDropManager.ReceiverSession(404, frames.size)

        for (frame in frames) {
            session.addFrame(frame)
            // Send again immediately (duplicate)
            session.addFrame(frame)
        }

        assertEquals(frames.size, session.receivedCount)
        assertTrue(session.isComplete())
        assertArrayEquals(payload, session.reconstructBytes())
    }

    @Test
    fun testDifferentSessionIdRejected() {
        val payload = "Session isolation test".toByteArray(Charsets.UTF_8)
        val frames = lightDropManager.encodeBytesToFrames(payload, sessionId = 505)
        val session = LightDropManager.ReceiverSession(999, frames.size) // Different session

        for (frame in frames) {
            val completed = session.addFrame(frame)
            assertFalse(completed)
        }

        assertEquals(0, session.receivedCount)
        assertFalse(session.isComplete())
    }

    @Test
    fun testFileStreamingEncoderAndReassembly() {
        val tempFile = tempFolder.newFile("optical_test.bin")
        val testData = ByteArray(1024) { (it and 0xFF).toByte() }
        tempFile.writeBytes(testData)

        val frames = lightDropManager.encodeFileToFrames(tempFile, sessionId = 707)
        assertTrue(frames.isNotEmpty())

        val session = LightDropManager.ReceiverSession(707, frames.size)
        for (f in frames) {
            session.addFrame(f)
        }

        assertTrue(session.isComplete())
        assertArrayEquals(testData, session.reconstructBytes())
    }

    @Test
    fun testFileExceeding5MbLimitThrowsException() {
        val oversizedFile = tempFolder.newFile("large_file.dat")
        val raf = RandomAccessFile(oversizedFile, "rw")
        // Set file length to 6 MB (> 5 MB max limit)
        raf.setLength(6L * 1024 * 1024)
        raf.close()

        try {
            lightDropManager.encodeFileToFrames(oversizedFile)
            fail("Expected IllegalArgumentException for optical files over 5 MB")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("exceeds optical transfer limit") == true)
        }
    }

    @Test
    fun testMalformedFrameStringReturnsNull() {
        assertNull(lightDropManager.parseFrame(""))
        assertNull(lightDropManager.parseFrame("INVALID:1:2:3"))
        assertNull(lightDropManager.parseFrame("ASTL:2:1:0:1:100:AQ==")) // wrong version (2 != 1)
        assertNull(lightDropManager.parseFrame("ASTL:1:not_a_number:0:1:100:AQ=="))
    }

    @Test
    fun testEncodeFileWithFileNameMetadataPreserved() {
        val testFile = tempFolder.newFile("document_confidential.pdf")
        val content = "Confidential Report 2026 Content".toByteArray(Charsets.UTF_8)
        testFile.writeBytes(content)

        val frames = lightDropManager.encodeFileToFrames(testFile, sessionId = 808)
        assertTrue(frames.isNotEmpty())
        assertEquals("document_confidential.pdf", frames.first().fileName)

        val serialized = lightDropManager.serializeFrame(frames.first())
        val parsed = lightDropManager.parseFrame(serialized)
        assertNotNull(parsed)
        assertEquals("document_confidential.pdf", parsed?.fileName)

        val session = LightDropManager.ReceiverSession(808, frames.size)
        session.addFrame(parsed!!)
        assertEquals("document_confidential.pdf", session.detectedFileName)
    }
}
