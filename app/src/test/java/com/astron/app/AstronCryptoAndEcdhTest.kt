package com.astron.app

import com.astron.app.core.model.ManifestItem
import com.astron.app.core.model.TransferManifest
import com.astron.app.core.security.CryptoManager
import com.astron.app.core.storage.StorageManager
import com.astron.app.transport.lan.LanTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

class AstronCryptoAndEcdhTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testEcKeyPairEncodingAndFingerprint() {
        val kp = CryptoManager.generateEcKeyPair()
        assertNotNull(kp.public)
        assertNotNull(kp.private)

        val encodedPub = CryptoManager.encodePublicKey(kp.public)
        assertTrue(encodedPub.isNotBlank())

        val decodedPub = CryptoManager.decodePublicKey(encodedPub)
        assertArrayEquals(kp.public.encoded, decodedPub.encoded)

        val fingerprint = CryptoManager.getPublicKeyFingerprint(kp.public)
        assertEquals(64, fingerprint.length) // 256-bit hex
        assertTrue(fingerprint.all { it in '0'..'9' || it in 'a'..'f' })

        val displayId = CryptoManager.getDisplayPeerId(kp.public)
        assertEquals(8, displayId.length)
        assertEquals(fingerprint.take(8), displayId)
    }

    @Test
    fun testEcdsaSigningAndVerification() {
        val kp = CryptoManager.generateEcKeyPair()
        val data = "Astron-2026-Secure-Data-Payload".toByteArray(Charsets.UTF_8)

        val signature = CryptoManager.sign(kp.private, data)
        assertTrue(signature.isNotBlank())

        // 1. Valid signature verifies
        assertTrue(CryptoManager.verifySignature(kp.public, data, signature))

        // 2. Tampered data fails
        val tamperedData = "Astron-2026-Tampered-Data-Payload".toByteArray(Charsets.UTF_8)
        assertFalse(CryptoManager.verifySignature(kp.public, tamperedData, signature))

        // 3. Different key fails
        val otherKp = CryptoManager.generateEcKeyPair()
        assertFalse(CryptoManager.verifySignature(otherKp.public, data, signature))
    }

    @Test
    fun testEcdhKeyAgreementAndTranscriptBinding() {
        val aliceIdentity = CryptoManager.generateEcKeyPair()
        val aliceEphemeral = CryptoManager.generateEcKeyPair()

        val bobIdentity = CryptoManager.generateEcKeyPair()
        val bobEphemeral = CryptoManager.generateEcKeyPair()

        val aliceFingerprint = CryptoManager.getPublicKeyFingerprint(aliceIdentity.public)
        val bobFingerprint = CryptoManager.getPublicKeyFingerprint(bobIdentity.public)

        val aliceEphemeralPubB64 = CryptoManager.encodePublicKey(aliceEphemeral.public)
        val bobEphemeralPubB64 = CryptoManager.encodePublicKey(bobEphemeral.public)

        val sessionNonce = UUID.randomUUID().toString()

        // Alice computes shared secret from Bob's ephemeral public key
        val aliceSharedSecret = CryptoManager.computeSharedSecret(aliceEphemeral.private, bobEphemeral.public)

        // Bob computes shared secret from Alice's ephemeral public key
        val bobSharedSecret = CryptoManager.computeSharedSecret(bobEphemeral.private, aliceEphemeral.public)

        // Both shared secrets must match
        assertArrayEquals(aliceSharedSecret, bobSharedSecret)

        // Alice derives transcript-bound session key
        val aliceSessionKey = CryptoManager.deriveSessionKeyEcdh(
            sharedSecret = aliceSharedSecret,
            sessionNonce = sessionNonce,
            senderFingerprint = aliceFingerprint,
            receiverFingerprint = bobFingerprint,
            senderEphemeralPubBase64 = aliceEphemeralPubB64,
            receiverEphemeralPubBase64 = bobEphemeralPubB64
        )

        // Bob derives transcript-bound session key with identical transcript
        val bobSessionKey = CryptoManager.deriveSessionKeyEcdh(
            sharedSecret = bobSharedSecret,
            sessionNonce = sessionNonce,
            senderFingerprint = aliceFingerprint,
            receiverFingerprint = bobFingerprint,
            senderEphemeralPubBase64 = aliceEphemeralPubB64,
            receiverEphemeralPubBase64 = bobEphemeralPubB64
        )

        assertArrayEquals(aliceSessionKey.encoded, bobSessionKey.encoded)

        // Encrypt with Alice's key, decrypt with Bob's key
        val plaintext = "Ultra-secure end-to-end file chunk".toByteArray(Charsets.UTF_8)
        val ciphertext = CryptoManager.encryptAesGcm(plaintext, aliceSessionKey)
        val decrypted = CryptoManager.decryptAesGcm(ciphertext, bobSessionKey)
        assertArrayEquals(plaintext, decrypted)

        // Tampering with transcript (e.g. nonce or identity) produces non-matching keys
        val attackerSessionKey = CryptoManager.deriveSessionKeyEcdh(
            sharedSecret = aliceSharedSecret,
            sessionNonce = "tampered-nonce-1234",
            senderFingerprint = aliceFingerprint,
            receiverFingerprint = bobFingerprint,
            senderEphemeralPubBase64 = aliceEphemeralPubB64,
            receiverEphemeralPubBase64 = bobEphemeralPubB64
        )
        assertFalse(aliceSessionKey.encoded.contentEquals(attackerSessionKey.encoded))
    }

    @Test
    fun testSasMnemonicDeterministicAndMatching() {
        val kp1 = CryptoManager.generateEcKeyPair()
        val kp2 = CryptoManager.generateEcKeyPair()
        val sharedSecret = CryptoManager.computeSharedSecret(kp1.private, kp2.public)

        val sessionKey = CryptoManager.deriveSessionKeyEcdh(
            sharedSecret = sharedSecret,
            sessionNonce = "sas-nonce-1",
            senderFingerprint = "fp1",
            receiverFingerprint = "fp2",
            senderEphemeralPubBase64 = "eph1",
            receiverEphemeralPubBase64 = "eph2"
        )

        val sas1 = CryptoManager.generateSasMnemonic(sessionKey.encoded)
        val sas2 = CryptoManager.generateSasMnemonic(sessionKey.encoded)

        assertEquals(sas1, sas2)
        val words = sas1.split(" ")
        assertEquals(4, words.size)
        assertTrue(words.all { it.isNotBlank() })
    }

    @Test
    fun testReplayProtectionRejectsDuplicateNonceAndStaleTimestamps() {
        val peerId = "peer-${UUID.randomUUID()}"
        val nonce = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        // 1. Fresh timestamp and new nonce succeeds
        assertTrue(CryptoManager.validateHandshakeTimestampAndNonce(peerId, now, nonce))

        // 2. Replay of same nonce fails
        assertFalse(CryptoManager.validateHandshakeTimestampAndNonce(peerId, now, nonce))

        // 3. Stale timestamp (> 5 minutes in the past) fails
        val staleTimestamp = now - (6 * 60 * 1000L)
        val freshNonce1 = UUID.randomUUID().toString()
        assertFalse(CryptoManager.validateHandshakeTimestampAndNonce(peerId, staleTimestamp, freshNonce1))

        // 4. Stale timestamp in the future (> 5 minutes) fails
        val futureTimestamp = now + (6 * 60 * 1000L)
        val freshNonce2 = UUID.randomUUID().toString()
        assertFalse(CryptoManager.validateHandshakeTimestampAndNonce(peerId, futureTimestamp, freshNonce2))

        // 5. Blank nonce fails
        assertFalse(CryptoManager.validateHandshakeTimestampAndNonce(peerId, now, ""))
    }

    @Test
    fun testPathTraversalSanitization() {
        val sanitizedUnix = CryptoManager.sanitizeFilename("../../etc/passwd")
        assertFalse(sanitizedUnix.contains(".."))
        assertFalse(sanitizedUnix.contains("/"))
        assertEquals("____etc_passwd", sanitizedUnix)

        val sanitizedWin = CryptoManager.sanitizeFilename("..\\windows\\system32")
        assertFalse(sanitizedWin.contains(".."))
        assertFalse(sanitizedWin.contains("\\"))
        assertEquals("__windows_system32", sanitizedWin)

        assertEquals("document.pdf", CryptoManager.sanitizeFilename("document.pdf"))
        assertEquals("file_test_.txt", CryptoManager.sanitizeFilename("file:test?.txt"))
        assertEquals("secret.doc", CryptoManager.sanitizeFilename("secret\u0000.doc"))
    }

    @Test
    fun testFullEcdhSocketLoopbackTransferWithSasAndSha256() = runBlocking {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort
        val transferredFile = tempFolder.newFile("ecdh_received.bin")

        val payload = ByteArray(64 * 1024) { (it and 0xFF).toByte() }
        val payloadSha256 = CryptoManager.computeSha256(payload.inputStream())

        val senderIdentityKp = CryptoManager.generateEcKeyPair()
        val receiverIdentityKp = CryptoManager.generateEcKeyPair()

        val serverSasMnemonic = async(Dispatchers.IO) {
            val client = serverSocket.accept()
            val inStream = DataInputStream(BufferedInputStream(client.getInputStream()))
            val outStream = DataOutputStream(BufferedOutputStream(client.getOutputStream()))

            // 1. Read OP_HANDSHAKE_REQ
            val handshakeOp = inStream.readByte()
            assertEquals(LanTransport.OP_HANDSHAKE_REQ, handshakeOp)
            val sessionId = inStream.readUTF()
            val senderName = inStream.readUTF()
            val token = inStream.readUTF()
            assertTrue(token.startsWith("ASTRON_ECDH:"))

            val parts = token.split(":")
            val senderIdentityPubB64 = parts[1]
            val senderEphemeralPubB64 = parts[2]
            val timestamp = parts[3].toLong()
            val sessionNonce = parts[4]
            val signature = parts[5]

            val senderIdentityPub = CryptoManager.decodePublicKey(senderIdentityPubB64)
            val senderFingerprint = CryptoManager.getPublicKeyFingerprint(senderIdentityPub)
            assertTrue(CryptoManager.validateHandshakeTimestampAndNonce(senderFingerprint, timestamp, sessionNonce))

            val signedData = "$sessionId:$senderName:$timestamp:$sessionNonce:$senderEphemeralPubB64".toByteArray(Charsets.UTF_8)
            assertTrue(CryptoManager.verifySignature(senderIdentityPub, signedData, signature))

            val senderEphemeralPub = CryptoManager.decodePublicKey(senderEphemeralPubB64)
            val receiverEphemeralKp = CryptoManager.generateEcKeyPair()
            val receiverEphemeralPubB64 = CryptoManager.encodePublicKey(receiverEphemeralKp.public)
            val receiverIdentityPubB64 = CryptoManager.encodePublicKey(receiverIdentityKp.public)
            val receiverFingerprint = CryptoManager.getPublicKeyFingerprint(receiverIdentityKp.public)

            val respToSign = "$sessionId:$sessionNonce:$receiverEphemeralPubB64".toByteArray(Charsets.UTF_8)
            val respSig = CryptoManager.sign(receiverIdentityKp.private, respToSign)

            val sharedSecret = CryptoManager.computeSharedSecret(receiverEphemeralKp.private, senderEphemeralPub)
            val sessionKey = CryptoManager.deriveSessionKeyEcdh(
                sharedSecret = sharedSecret,
                sessionNonce = sessionNonce,
                senderFingerprint = senderFingerprint,
                receiverFingerprint = receiverFingerprint,
                senderEphemeralPubBase64 = senderEphemeralPubB64,
                receiverEphemeralPubBase64 = receiverEphemeralPubB64
            )
            val sas = CryptoManager.generateSasMnemonic(sessionKey.encoded)

            // Send ECDH response
            outStream.writeByte(LanTransport.OP_HANDSHAKE_RESP.toInt())
            outStream.writeUTF("ASTRON_ECDH_RESP:$receiverIdentityPubB64:$receiverEphemeralPubB64:$respSig")
            outStream.flush()

            // 2. Read manifest
            assertEquals(LanTransport.OP_MANIFEST, inStream.readByte())
            val manifestLen = inStream.readInt()
            val manifestBytes = ByteArray(manifestLen)
            inStream.readFully(manifestBytes)
            outStream.writeByte(LanTransport.OP_MANIFEST_ACK.toInt())
            outStream.flush()

            // 3. Receive file
            assertEquals(LanTransport.OP_FILE_START, inStream.readByte())
            inStream.readUTF() // id
            inStream.readUTF() // name
            inStream.readLong() // size

            val writeSession = StorageManager.FileWriteSession(transferredFile)
            while (true) {
                val op = inStream.readByte()
                if (op == LanTransport.OP_CHUNK) {
                    val len = inStream.readInt()
                    val enc = ByteArray(len)
                    inStream.readFully(enc)
                    val dec = CryptoManager.decryptAesGcm(enc, sessionKey)
                    writeSession.writeChunk(dec, dec.size)
                } else if (op == LanTransport.OP_FILE_END) {
                    inStream.readUTF()
                } else if (op == LanTransport.OP_TRANSFER_COMPLETE) {
                    outStream.writeByte(LanTransport.OP_TRANSFER_COMPLETE.toInt())
                    outStream.flush()
                    break
                }
            }
            writeSession.close()
            client.close()
            serverSocket.close()
            sas
        }

        val clientSasMnemonic = async(Dispatchers.IO) {
            val socket = Socket("127.0.0.1", port)
            val outStream = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            val inStream = DataInputStream(BufferedInputStream(socket.getInputStream()))

            val sessionId = "session-ecdh-test-1"
            val senderName = "Sender-Pixel"
            val sessionNonce = UUID.randomUUID().toString()
            val senderEphemeralKp = CryptoManager.generateEcKeyPair()
            val senderEphemeralPubB64 = CryptoManager.encodePublicKey(senderEphemeralKp.public)
            val senderFingerprint = CryptoManager.getPublicKeyFingerprint(senderIdentityKp.public)

            val ecdhToken = CryptoManager.createEcdhHandshakeToken(
                sessionId = sessionId,
                senderName = senderName,
                identityKeyPair = senderIdentityKp,
                ephemeralKeyPair = senderEphemeralKp,
                sessionNonce = sessionNonce
            )

            // Send handshake request
            outStream.writeByte(LanTransport.OP_HANDSHAKE_REQ.toInt())
            outStream.writeUTF(sessionId)
            outStream.writeUTF(senderName)
            outStream.writeUTF(ecdhToken)
            outStream.flush()

            val respOp = inStream.readByte()
            assertEquals(LanTransport.OP_HANDSHAKE_RESP, respOp)

            val ecdhResp = inStream.readUTF()
            assertTrue(ecdhResp.startsWith("ASTRON_ECDH_RESP:"))
            val parts = ecdhResp.split(":")
            val receiverIdentityPubB64 = parts[1]
            val receiverEphemeralPubB64 = parts[2]
            val receiverSig = parts[3]

            val receiverIdentityPub = CryptoManager.decodePublicKey(receiverIdentityPubB64)
            val receiverEphemeralPub = CryptoManager.decodePublicKey(receiverEphemeralPubB64)
            val receiverFingerprint = CryptoManager.getPublicKeyFingerprint(receiverIdentityPub)

            val respToVerify = "$sessionId:$sessionNonce:$receiverEphemeralPubB64".toByteArray(Charsets.UTF_8)
            assertTrue(CryptoManager.verifySignature(receiverIdentityPub, respToVerify, receiverSig))

            val sharedSecret = CryptoManager.computeSharedSecret(senderEphemeralKp.private, receiverEphemeralPub)
            val sessionKey = CryptoManager.deriveSessionKeyEcdh(
                sharedSecret = sharedSecret,
                sessionNonce = sessionNonce,
                senderFingerprint = senderFingerprint,
                receiverFingerprint = receiverFingerprint,
                senderEphemeralPubBase64 = senderEphemeralPubB64,
                receiverEphemeralPubBase64 = receiverEphemeralPubB64
            )
            val sas = CryptoManager.generateSasMnemonic(sessionKey.encoded)

            // Send manifest
            val manifest = TransferManifest(
                sessionId = sessionId,
                senderDeviceId = "sender-id",
                senderDeviceName = senderName,
                totalFiles = 1,
                totalBytes = payload.size.toLong(),
                items = listOf(
                    ManifestItem(id = "item-1", name = "ecdh_payload.bin", size = payload.size.toLong(), mimeType = "application/octet-stream")
                )
            )
            val mBytes = json.encodeToString(manifest).toByteArray(Charsets.UTF_8)
            outStream.writeByte(LanTransport.OP_MANIFEST.toInt())
            outStream.writeInt(mBytes.size)
            outStream.write(mBytes)
            outStream.flush()

            assertEquals(LanTransport.OP_MANIFEST_ACK, inStream.readByte())

            // Send file
            outStream.writeByte(LanTransport.OP_FILE_START.toInt())
            outStream.writeUTF("item-1")
            outStream.writeUTF("ecdh_payload.bin")
            outStream.writeLong(payload.size.toLong())
            outStream.flush()

            val enc = CryptoManager.encryptAesGcm(payload, sessionKey)
            outStream.writeByte(LanTransport.OP_CHUNK.toInt())
            outStream.writeInt(enc.size)
            outStream.write(enc)
            outStream.flush()

            outStream.writeByte(LanTransport.OP_FILE_END.toInt())
            outStream.writeUTF("item-1")
            outStream.writeByte(LanTransport.OP_TRANSFER_COMPLETE.toInt())
            outStream.flush()

            assertEquals(LanTransport.OP_TRANSFER_COMPLETE, inStream.readByte())
            socket.close()
            sas
        }

        withTimeout(8_000) {
            val serverSas = serverSasMnemonic.await()
            val clientSas = clientSasMnemonic.await()

            // Both sides computed the exact same SAS 4-word code out-of-band!
            assertEquals(serverSas, clientSas)
            val words = serverSas.split(" ")
            assertEquals(4, words.size)
        }

        // File integrity and SHA-256 match
        val receivedSha256 = CryptoManager.computeSha256(transferredFile.inputStream())
        assertEquals(payloadSha256, receivedSha256)
        assertArrayEquals(payload, transferredFile.readBytes())
    }
}
