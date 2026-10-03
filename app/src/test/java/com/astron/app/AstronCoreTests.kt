package com.astron.app

import com.astron.app.core.model.ManifestItem
import com.astron.app.core.model.TransferManifest
import com.astron.app.core.security.CryptoManager
import com.astron.app.core.util.NetworkUtils
import com.astron.app.transport.lightdrop.LightDropManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class AstronCoreTests {

    // --- SECURITY & CRYPTO TESTS ---

    @Test
    fun testFilenameSanitizationPathTraversal() {
        val malicious1 = "../../system/etc/passwd"
        val clean1 = CryptoManager.sanitizeFilename(malicious1)
        assertFalse(clean1.contains(".."))
        assertFalse(clean1.contains("/"))
        assertTrue(clean1.contains("passwd"))

        val malicious2 = "..\\..\\Windows\\System32\\cmd.exe"
        val clean2 = CryptoManager.sanitizeFilename(malicious2)
        assertFalse(clean2.contains(".."))
        assertFalse(clean2.contains("\\"))
        assertTrue(clean2.contains("cmd.exe"))
    }

    @Test
    fun testFilenameSanitizationIllegalCharacters() {
        val raw = "vacation:2026*photo?name<1>.jpg"
        val clean = CryptoManager.sanitizeFilename(raw)
        assertFalse(clean.contains(":"))
        assertFalse(clean.contains("*"))
        assertFalse(clean.contains("?"))
        assertFalse(clean.contains("<"))
        assertFalse(clean.contains(">"))
    }

    @Test
    fun testAesGcmEncryptionDecryptionRoundtrip() {
        val token = CryptoManager.generateSessionToken(16)
        val key = CryptoManager.deriveKeyFromToken(token)

        val plaintext = "God-Tier Android File Transfer App Astron - Confidential".toByteArray(Charsets.UTF_8)
        val encrypted = CryptoManager.encryptAesGcm(plaintext, key)

        assertTrue(encrypted.size > plaintext.size)

        val decrypted = CryptoManager.decryptAesGcm(encrypted, key)
        assertArrayEquals(plaintext, decrypted)
        assertEquals(String(plaintext), String(decrypted))
    }

    @Test
    fun testSha256Checksum() {
        val input = "Astron File Transfer Protocol".toByteArray(Charsets.UTF_8)
        val sha = CryptoManager.computeSha256(ByteArrayInputStream(input))
        assertNotNull(sha)
        assertEquals(64, sha.length) // 64 hex characters
    }

    // --- OPTICAL / LIGHT DROP PROTOCOL TESTS ---

    @Test
    fun testLightDropFrameEncodingAndDecoding() {
        val lightDrop = LightDropManager()
        val originalData = "Zero-Radio Optical Transfer Test Payload for Astron App - 2026".toByteArray(Charsets.UTF_8)
        val sessionId = 42

        val frames = lightDrop.encodeBytesToFrames(originalData, sessionId)
        assertTrue(frames.isNotEmpty())

        val receiverSession = LightDropManager.ReceiverSession(sessionId, frames.size)

        for (frame in frames) {
            val serialized = lightDrop.serializeFrame(frame)
            val parsed = lightDrop.parseFrame(serialized)
            assertNotNull(parsed)
            assertEquals(frame.frameIndex, parsed!!.frameIndex)
            assertEquals(frame.totalFrames, parsed.totalFrames)
            assertEquals(frame.checksumCrc32, parsed.checksumCrc32)
            receiverSession.addFrame(parsed)
        }

        assertTrue(receiverSession.isComplete())
        val reconstructed = receiverSession.reconstructBytes()
        assertArrayEquals(originalData, reconstructed)
        assertEquals(String(originalData), String(reconstructed))
    }

    // --- NETWORK UTILITIES & FORMATTING TESTS ---

    @Test
    fun testFormatBytes() {
        assertEquals("0 B", NetworkUtils.formatBytes(0))
        assertEquals("500 B", NetworkUtils.formatBytes(500))
        assertEquals("1.0 KB", NetworkUtils.formatBytes(1024))
        assertEquals("10.0 MB", NetworkUtils.formatBytes(10L * 1024L * 1024L))
        assertEquals("18.4 GB", NetworkUtils.formatBytes(19756849561L))
    }

    @Test
    fun testFormatSpeed() {
        assertEquals("0 MB/s", NetworkUtils.formatSpeed(0))
        assertEquals("384.0 MB/s", NetworkUtils.formatSpeed(384L * 1024L * 1024L))
    }

    @Test
    fun testFormatEta() {
        assertEquals("~19 sec remaining", NetworkUtils.formatEta(19))
        assertEquals("~2 min 5s remaining", NetworkUtils.formatEta(125))
    }

    // --- MANIFEST SERIALIZATION TESTS ---

    @Test
    fun testManifestJsonRoundtrip() {
        val json = Json { ignoreUnknownKeys = true }
        val manifest = TransferManifest(
            sessionId = "session-1234",
            senderDeviceId = "device-5678",
            senderDeviceName = "Pixel 8 Pro",
            totalFiles = 2,
            totalBytes = 2048,
            items = listOf(
                ManifestItem(id = "1", name = "photo.jpg", size = 1024, mimeType = "image/jpeg"),
                ManifestItem(id = "2", name = "doc.pdf", size = 1024, mimeType = "application/pdf")
            )
        )

        val serialized = json.encodeToString(manifest)
        val decoded = json.decodeFromString<TransferManifest>(serialized)

        assertEquals(manifest.sessionId, decoded.sessionId)
        assertEquals(manifest.senderDeviceName, decoded.senderDeviceName)
        assertEquals(2, decoded.items.size)
        assertEquals("photo.jpg", decoded.items[0].name)
    }
}
