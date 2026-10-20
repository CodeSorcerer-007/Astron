package com.astron.app

import com.astron.app.core.model.ManifestItem
import com.astron.app.core.model.TransferManifest
import com.astron.app.core.security.CryptoManager
import com.astron.app.core.storage.StorageManager
import com.astron.app.transport.lan.LanTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.ServerSocket
import java.net.Socket

class LanTransportIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testFileWriteSessionSequentialAndSeek() {
        val targetFile = tempFolder.newFile("write_session_test.dat")
        val session = StorageManager.FileWriteSession(targetFile)

        val chunk1 = "Hello ".toByteArray(Charsets.UTF_8)
        val chunk2 = "Astron ".toByteArray(Charsets.UTF_8)
        val chunk3 = "2026!".toByteArray(Charsets.UTF_8)

        session.writeChunk(chunk1, chunk1.size)
        session.writeChunk(chunk2, chunk2.size)
        session.writeChunk(chunk3, chunk3.size)
        session.close()

        val expected = "Hello Astron 2026!"
        assertEquals(expected, targetFile.readText(Charsets.UTF_8))

        // Re-open and seek to test offset resume
        val resumeSession = StorageManager.FileWriteSession(targetFile)
        resumeSession.seekTo(6L) // seek right after "Hello "
        val overwriteChunk = "Galaxy 2026!".toByteArray(Charsets.UTF_8)
        resumeSession.writeChunk(overwriteChunk, overwriteChunk.size)
        resumeSession.close()

        assertEquals("Hello Galaxy 2026!", targetFile.readText(Charsets.UTF_8))
    }

    @Test
    fun testSocketLoopbackTransferWithPauseResumeAndSha256() = runBlocking {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort
        val transferredFile = tempFolder.newFile("received_astron.bin")

        // 128 KB test file payload
        val originalPayload = ByteArray(128 * 1024) { (it and 0x7F).toByte() }
        val originalSha256 = CryptoManager.computeSha256(originalPayload.inputStream())

        val pauseFlow = MutableStateFlow(false)
        val transferStarted = CompletableDeferred<Unit>()

        // Server coroutine (Receiver)
        val serverJob = async(Dispatchers.IO) {
            val client = serverSocket.accept()
            val inStream = DataInputStream(BufferedInputStream(client.getInputStream()))
            val outStream = DataOutputStream(BufferedOutputStream(client.getOutputStream()))

            // 1. Handshake with session token
            val handshakeOp = inStream.readByte()
            assertEquals(LanTransport.OP_HANDSHAKE_REQ, handshakeOp)
            val sessionId = inStream.readUTF()
            val senderName = inStream.readUTF()
            val sessionToken = inStream.readUTF()
            assertEquals("test-session-99", sessionId)
            assertEquals("Sender-Pixel", senderName)
            val sessionKey = CryptoManager.deriveKeyFromToken(sessionToken)

            outStream.writeByte(LanTransport.OP_HANDSHAKE_RESP.toInt())
            outStream.flush()

            // 2. Manifest
            val manifestOp = inStream.readByte()
            assertEquals(LanTransport.OP_MANIFEST, manifestOp)
            val manifestLen = inStream.readInt()
            val manifestBytes = ByteArray(manifestLen)
            inStream.readFully(manifestBytes)
            val manifest = json.decodeFromString<TransferManifest>(String(manifestBytes, Charsets.UTF_8))
            assertEquals(1, manifest.items.size)

            // 3. Receive File Chunks with AES-GCM decryption
            val writeSession = StorageManager.FileWriteSession(transferredFile)
            var totalReceived = 0L

            while (true) {
                val op = inStream.readByte()
                if (op == LanTransport.OP_FILE_START) {
                    val fileId = inStream.readUTF()
                    val fileName = inStream.readUTF()
                    val fileSize = inStream.readLong()
                    assertEquals("item-1", fileId)
                    assertEquals("payload.bin", fileName)
                    assertEquals(originalPayload.size.toLong(), fileSize)
                } else if (op == LanTransport.OP_CHUNK) {
                    val chunkLen = inStream.readInt()
                    val encChunk = ByteArray(chunkLen)
                    inStream.readFully(encChunk)
                    val plainChunk = CryptoManager.decryptAesGcm(encChunk, sessionKey)
                    writeSession.writeChunk(plainChunk, plainChunk.size)
                    totalReceived += plainChunk.size
                } else if (op == LanTransport.OP_FILE_END) {
                    val endFileId = inStream.readUTF()
                    assertEquals("item-1", endFileId)
                } else if (op == LanTransport.OP_TRANSFER_COMPLETE) {
                    break
                }
            }
            writeSession.close()
            client.close()
            serverSocket.close()
            totalReceived
        }

        // Client coroutine (Sender)
        val clientJob = async(Dispatchers.IO) {
            val socket = Socket("127.0.0.1", port)
            val outStream = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            val inStream = DataInputStream(BufferedInputStream(socket.getInputStream()))

            val sessionToken = CryptoManager.generateSessionToken(16)
            val sessionKey = CryptoManager.deriveKeyFromToken(sessionToken)

            // 1. Handshake with session token
            outStream.writeByte(LanTransport.OP_HANDSHAKE_REQ.toInt())
            outStream.writeUTF("test-session-99")
            outStream.writeUTF("Sender-Pixel")
            outStream.writeUTF(sessionToken)
            outStream.flush()

            val resp = inStream.readByte()
            assertEquals(LanTransport.OP_HANDSHAKE_RESP, resp)

            // 2. Manifest
            val manifest = TransferManifest(
                sessionId = "test-session-99",
                senderDeviceId = "sender-id-1",
                senderDeviceName = "Sender-Pixel",
                totalFiles = 1,
                totalBytes = originalPayload.size.toLong(),
                items = listOf(
                    ManifestItem(id = "item-1", name = "payload.bin", size = originalPayload.size.toLong(), mimeType = "application/octet-stream")
                )
            )
            val manifestBytes = json.encodeToString(manifest).toByteArray(Charsets.UTF_8)
            outStream.writeByte(LanTransport.OP_MANIFEST.toInt())
            outStream.writeInt(manifestBytes.size)
            outStream.write(manifestBytes)
            outStream.flush()

            // 3. Stream File with Pause and Resume and AES-GCM chunk encryption
            outStream.writeByte(LanTransport.OP_FILE_START.toInt())
            outStream.writeUTF("item-1")
            outStream.writeUTF("payload.bin")
            outStream.writeLong(originalPayload.size.toLong())
            outStream.flush()

            val chunkSize = 16 * 1024
            var offset = 0

            while (offset < originalPayload.size) {
                // Cooperative pause simulation:
                if (pauseFlow.value) {
                    pauseFlow.first { !it } // wait for unpause
                }

                // Trigger pause at 50%
                if (offset == 64 * 1024) {
                    pauseFlow.value = true
                }

                val currentChunkSize = minOf(chunkSize, originalPayload.size - offset)
                val plainChunk = originalPayload.copyOfRange(offset, offset + currentChunkSize)
                val encChunk = CryptoManager.encryptAesGcm(plainChunk, sessionKey)

                outStream.writeByte(LanTransport.OP_CHUNK.toInt())
                outStream.writeInt(encChunk.size)
                outStream.write(encChunk)
                outStream.flush()

                offset += currentChunkSize
            }

            outStream.writeByte(LanTransport.OP_FILE_END.toInt())
            outStream.writeUTF("item-1")

            outStream.writeByte(LanTransport.OP_TRANSFER_COMPLETE.toInt())
            outStream.flush()

            socket.close()
        }

        // Resume transmission after short delay
        val monitorJob = async(Dispatchers.IO) {
            pauseFlow.first { it } // wait until paused
            assertTrue("Should be paused mid-stream", pauseFlow.value)
            kotlinx.coroutines.delay(50)
            pauseFlow.value = false // unpause!
        }

        withTimeout(10_000) {
            val totalBytesReceived = serverJob.await()
            clientJob.await()
            monitorJob.await()

            assertEquals(originalPayload.size.toLong(), totalBytesReceived)
        }

        // Verify SHA-256 checksum and exact bytes
        val receivedSha256 = CryptoManager.computeSha256(transferredFile.inputStream())
        assertEquals(originalSha256, receivedSha256)
        assertArrayEquals(originalPayload, transferredFile.readBytes())
    }

    @Test
    fun testSocketCancellationAbortsSession() = runBlocking {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort

        val serverJob = async(Dispatchers.IO) {
            val client = serverSocket.accept()
            val inStream = DataInputStream(BufferedInputStream(client.getInputStream()))
            val outStream = DataOutputStream(BufferedOutputStream(client.getOutputStream()))

            inStream.readByte() // OP_HANDSHAKE_REQ
            inStream.readUTF()
            inStream.readUTF()
            inStream.readUTF() // sessionToken
            outStream.writeByte(LanTransport.OP_HANDSHAKE_RESP.toInt())
            outStream.flush()

            // Next opcode from sender is cancel
            val cancelOp = inStream.readByte()
            assertEquals(LanTransport.OP_CANCEL, cancelOp)

            client.close()
            serverSocket.close()
            cancelOp
        }

        val clientJob = async(Dispatchers.IO) {
            val socket = Socket("127.0.0.1", port)
            val outStream = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            val inStream = DataInputStream(BufferedInputStream(socket.getInputStream()))

            outStream.writeByte(LanTransport.OP_HANDSHAKE_REQ.toInt())
            outStream.writeUTF("session-cancel")
            outStream.writeUTF("Device-Cancel")
            outStream.writeUTF("dummy-token-9999")
            outStream.flush()

            val resp = inStream.readByte()
            assertEquals(LanTransport.OP_HANDSHAKE_RESP, resp)

            // Send Cancel
            outStream.writeByte(LanTransport.OP_CANCEL.toInt())
            outStream.flush()
            socket.close()
        }

        withTimeout(5_000) {
            val result = serverJob.await()
            clientJob.await()
            assertEquals(LanTransport.OP_CANCEL, result)
        }
    }

    @Test
    fun testOpFileResumeWireTransmission() = runBlocking {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort

        val serverJob = async(Dispatchers.IO) {
            val client = serverSocket.accept()
            val inStream = DataInputStream(BufferedInputStream(client.getInputStream()))
            val outStream = DataOutputStream(BufferedOutputStream(client.getOutputStream()))

            val opHandshake = inStream.readByte()
            assertEquals(LanTransport.OP_HANDSHAKE_REQ, opHandshake)
            val sessionId = inStream.readUTF()
            val deviceName = inStream.readUTF()
            val token = inStream.readUTF()
            outStream.writeByte(LanTransport.OP_HANDSHAKE_RESP.toInt())
            outStream.flush()

            // Next opcode must be OP_FILE_RESUME
            val resumeOp = inStream.readByte()
            assertEquals(LanTransport.OP_FILE_RESUME, resumeOp)
            val itemId = inStream.readUTF()
            val resumeOffset = inStream.readLong()

            client.close()
            serverSocket.close()

            Triple(resumeOp, itemId, resumeOffset)
        }

        val clientJob = async(Dispatchers.IO) {
            val socket = Socket("127.0.0.1", port)
            val outStream = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            val inStream = DataInputStream(BufferedInputStream(socket.getInputStream()))

            outStream.writeByte(LanTransport.OP_HANDSHAKE_REQ.toInt())
            outStream.writeUTF("session-resume")
            outStream.writeUTF("Device-Resume")
            outStream.writeUTF("token-resume-1234")
            outStream.flush()

            val resp = inStream.readByte()
            assertEquals(LanTransport.OP_HANDSHAKE_RESP, resp)

            // Send OP_FILE_RESUME
            outStream.writeByte(LanTransport.OP_FILE_RESUME.toInt())
            outStream.writeUTF("item-file-resume-1")
            outStream.writeLong(1048576L) // 1 MB offset
            outStream.flush()

            socket.close()
        }

        withTimeout(5_000) {
            val (op, id, offset) = serverJob.await()
            clientJob.await()
            assertEquals(LanTransport.OP_FILE_RESUME, op)
            assertEquals("item-file-resume-1", id)
            assertEquals(1048576L, offset)
        }
    }
}

