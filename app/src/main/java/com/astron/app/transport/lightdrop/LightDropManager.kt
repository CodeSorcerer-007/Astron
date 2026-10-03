package com.astron.app.transport.lightdrop

import android.graphics.Bitmap
import com.astron.app.core.util.QrCodeGenerator
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32

internal object Base64Compat {
    fun encodeToString(bytes: ByteArray): String {
        return try {
            java.util.Base64.getEncoder().encodeToString(bytes)
        } catch (_: Throwable) {
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        }
    }

    fun decode(str: String): ByteArray {
        return try {
            java.util.Base64.getDecoder().decode(str)
        } catch (_: Throwable) {
            android.util.Base64.decode(str, android.util.Base64.NO_WRAP)
        }
    }
}

data class OpticalFrame(
    val sessionId: Int,
    val frameIndex: Int,
    val totalFrames: Int,
    val payload: ByteArray,
    val checksumCrc32: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OpticalFrame) return false
        return sessionId == other.sessionId && frameIndex == other.frameIndex
    }

    override fun hashCode(): Int = 31 * sessionId + frameIndex
}

class LightDropManager {

    companion object {
        const val TAG = "LightDropManager"
        const val MAGIC = 0x4153544C // 'ASTL' in ASCII
        const val PROTOCOL_VERSION: Byte = 1
        const val CHUNK_PAYLOAD_SIZE = 256 // 256 bytes per frame for ultra-fast, robust camera recognition
        const val MAX_OPTICAL_FILE_SIZE = 5L * 1024 * 1024 // 5 MB max for optical transfer
    }

    /**
     * SENDER: Streams a file into sequenced OpticalFrames with CRC32 checksums without buffering full file into memory.
     */
    fun encodeFileToFrames(file: File, sessionId: Int = (System.currentTimeMillis() and 0xFFFF).toInt()): List<OpticalFrame> {
        require(file.length() <= MAX_OPTICAL_FILE_SIZE) {
            "File size (${file.length()} bytes) exceeds optical transfer limit (${MAX_OPTICAL_FILE_SIZE / (1024 * 1024)} MB)"
        }
        val fileSize = file.length()
        val totalFrames = ((fileSize + CHUNK_PAYLOAD_SIZE - 1) / CHUNK_PAYLOAD_SIZE).toInt().coerceAtLeast(1)
        val frames = mutableListOf<OpticalFrame>()

        file.inputStream().buffered().use { stream ->
            val buffer = ByteArray(CHUNK_PAYLOAD_SIZE)
            var frameIndex = 0
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                val chunk = if (bytesRead == buffer.size) buffer.copyOf() else buffer.copyOfRange(0, bytesRead)
                val crc = CRC32().also { it.update(chunk) }
                frames.add(
                    OpticalFrame(
                        sessionId = sessionId,
                        frameIndex = frameIndex++,
                        totalFrames = totalFrames,
                        payload = chunk,
                        checksumCrc32 = crc.value
                    )
                )
            }
        }
        if (frames.isEmpty()) {
            frames.add(
                OpticalFrame(
                    sessionId = sessionId,
                    frameIndex = 0,
                    totalFrames = 1,
                    payload = ByteArray(0),
                    checksumCrc32 = 0L
                )
            )
        }
        return frames
    }

    fun encodeBytesToFrames(bytes: ByteArray, sessionId: Int = (System.currentTimeMillis() and 0xFFFF).toInt()): List<OpticalFrame> {
        val totalFrames = ((bytes.size + CHUNK_PAYLOAD_SIZE - 1) / CHUNK_PAYLOAD_SIZE).coerceAtLeast(1)
        val frames = mutableListOf<OpticalFrame>()

        for (i in 0 until totalFrames) {
            val start = i * CHUNK_PAYLOAD_SIZE
            val end = minOf(start + CHUNK_PAYLOAD_SIZE, bytes.size)
            val chunkPayload = bytes.copyOfRange(start, end)

            val crc = CRC32()
            crc.update(chunkPayload)

            frames.add(
                OpticalFrame(
                    sessionId = sessionId,
                    frameIndex = i,
                    totalFrames = totalFrames,
                    payload = chunkPayload,
                    checksumCrc32 = crc.value
                )
            )
        }
        return frames
    }

    /**
     * Formats an OpticalFrame into a compact visual transmission string.
     * Format: ASTL:version:sessionId:frameIdx:totalFrames:crc32:base64Payload
     */
    fun serializeFrame(frame: OpticalFrame): String {
        val b64 = Base64Compat.encodeToString(frame.payload)
        return "ASTL:$PROTOCOL_VERSION:${frame.sessionId}:${frame.frameIndex}:${frame.totalFrames}:${frame.checksumCrc32}:$b64"
    }

    /**
     * SENDER: Renders an optical frame into a high-contrast QR code bitmap for screen presentation.
     */
    fun renderFrameBitmap(frame: OpticalFrame, size: Int = 512): Bitmap {
        val payloadStr = serializeFrame(frame)
        return QrCodeGenerator.generateQrBitmap(payloadStr, size)
    }

    /**
     * RECEIVER: Parses a raw QR code string into a validated OpticalFrame.
     */
    fun parseFrame(rawString: String): OpticalFrame? {
        try {
            if (!rawString.startsWith("ASTL:")) return null
            val parts = rawString.split(":")
            if (parts.size < 7) return null

            val version = parts[1].toByteOrNull() ?: return null
            if (version != PROTOCOL_VERSION) return null

            val sessionId = parts[2].toIntOrNull() ?: return null
            val frameIndex = parts[3].toIntOrNull() ?: return null
            val totalFrames = parts[4].toIntOrNull() ?: return null
            val expectedCrc = parts[5].toLongOrNull() ?: return null
            val b64Payload = parts[6]

            val payload = Base64Compat.decode(b64Payload)

            // Validate CRC32
            val crc = CRC32()
            crc.update(payload)
            if (crc.value != expectedCrc) {
                return null
            }

            return OpticalFrame(sessionId, frameIndex, totalFrames, payload, expectedCrc)
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * RECEIVER: Accumulator session that collects incoming frames, deduplicates, and reconstructs the completed file.
     */
    class ReceiverSession(val sessionId: Int, val totalFrames: Int) {
        private val collectedFrames = mutableMapOf<Int, ByteArray>()

        fun addFrame(frame: OpticalFrame): Boolean {
            if (frame.sessionId != sessionId) return false
            collectedFrames[frame.frameIndex] = frame.payload
            return isComplete()
        }

        fun isComplete(): Boolean = collectedFrames.size == totalFrames

        val progress: Float
            get() = if (totalFrames > 0) collectedFrames.size.toFloat() / totalFrames else 0f

        val receivedCount: Int
            get() = collectedFrames.size

        fun reconstructBytes(): ByteArray {
            val bos = ByteArrayOutputStream()
            for (i in 0 until totalFrames) {
                val chunk = collectedFrames[i] ?: error("Missing frame $i")
                bos.write(chunk)
            }
            return bos.toByteArray()
        }
    }
}
