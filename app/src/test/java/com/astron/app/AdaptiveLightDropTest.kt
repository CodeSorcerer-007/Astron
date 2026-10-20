package com.astron.app

import com.astron.app.transport.lightdrop.LightDropManager
import com.astron.app.transport.lightdrop.OpticalDensity
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLightDropTest {

    private val lightDropManager = LightDropManager()

    @Test
    fun testResilientDensityChunkSize() {
        val payload = ByteArray(500) { (it % 100).toByte() }
        val frames = lightDropManager.encodeBytesToFrames(
            bytes = payload,
            sessionId = 111,
            density = OpticalDensity.RESILIENT
        )

        // 500 bytes / 128 = 4 frames (128 + 128 + 128 + 116)
        assertEquals(4, frames.size)
        assertEquals(128, frames[0].payload.size)
        assertEquals(128, frames[1].payload.size)
        assertEquals(128, frames[2].payload.size)
        assertEquals(116, frames[3].payload.size)
    }

    @Test
    fun testBalancedDensityChunkSize() {
        val payload = ByteArray(600) { (it % 100).toByte() }
        val frames = lightDropManager.encodeBytesToFrames(
            bytes = payload,
            sessionId = 222,
            density = OpticalDensity.BALANCED
        )

        // 600 bytes / 256 = 3 frames (256 + 256 + 88)
        assertEquals(3, frames.size)
        assertEquals(256, frames[0].payload.size)
        assertEquals(256, frames[1].payload.size)
        assertEquals(88, frames[2].payload.size)
    }

    @Test
    fun testUltraDensityChunkSize() {
        val payload = ByteArray(1200) { (it % 100).toByte() }
        val frames = lightDropManager.encodeBytesToFrames(
            bytes = payload,
            sessionId = 333,
            density = OpticalDensity.ULTRA
        )

        // 1200 bytes / 512 = 3 frames (512 + 512 + 176)
        assertEquals(3, frames.size)
        assertEquals(512, frames[0].payload.size)
        assertEquals(512, frames[1].payload.size)
        assertEquals(176, frames[2].payload.size)
    }

    @Test
    fun testMissingFramesTracking() {
        val payload = ByteArray(1024) { (it % 50).toByte() }
        // 1024 / 256 = 4 frames: [0, 1, 2, 3]
        val frames = lightDropManager.encodeBytesToFrames(payload, sessionId = 444, density = OpticalDensity.BALANCED)
        assertEquals(4, frames.size)

        val session = LightDropManager.ReceiverSession(444, frames.size)

        // Only add frame 0 and frame 2
        session.addFrame(frames[0])
        session.addFrame(frames[2])

        assertFalse(session.isComplete())
        assertEquals(2, session.receivedCount)
        assertEquals(setOf(0, 2), session.getReceivedFrameIndices())
        assertEquals(listOf(1, 3), session.getMissingFrameIndices())

        // Add frame 1
        session.addFrame(frames[1])
        assertEquals(listOf(3), session.getMissingFrameIndices())

        // Add remaining frame 3
        session.addFrame(frames[3])
        assertTrue(session.isComplete())
        assertTrue(session.getMissingFrameIndices().isEmpty())
        assertArrayEquals(payload, session.reconstructBytes())
    }
}
