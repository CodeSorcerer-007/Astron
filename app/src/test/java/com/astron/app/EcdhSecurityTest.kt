package com.astron.app

import com.astron.app.core.security.CryptoManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

class EcdhSecurityTest {

    @Test
    fun testEcdhKeyPairGenerationAndExportImport() {
        val keyPair = CryptoManager.generateEcKeyPair()
        assertNotNull(keyPair.public)
        assertNotNull(keyPair.private)

        val exportedPub = CryptoManager.exportPublicKey(keyPair.public)
        assertTrue(exportedPub.isNotEmpty())

        val importedPub = CryptoManager.importPublicKey(exportedPub)
        assertEquals(keyPair.public, importedPub)
    }

    @Test
    fun testEcdhSharedSecretKeyAgreementRoundtrip() {
        // Alice generates keypair
        val aliceKeyPair = CryptoManager.generateEcKeyPair()
        val alicePubBytes = CryptoManager.exportPublicKey(aliceKeyPair.public)

        // Bob generates keypair
        val bobKeyPair = CryptoManager.generateEcKeyPair()
        val bobPubBytes = CryptoManager.exportPublicKey(bobKeyPair.public)

        // Alice derives shared key using her private key and Bob's public key
        val aliceDerivedSecret = CryptoManager.deriveSharedSecretEcdh(aliceKeyPair.private, bobPubBytes)

        // Bob derives shared key using his private key and Alice's public key
        val bobDerivedSecret = CryptoManager.deriveSharedSecretEcdh(bobKeyPair.private, alicePubBytes)

        // Perfect agreement: both derived the exact same 256-bit symmetric key
        assertArrayEquals(aliceDerivedSecret.encoded, bobDerivedSecret.encoded)
        assertEquals("AES", aliceDerivedSecret.algorithm)
        assertEquals(32, aliceDerivedSecret.encoded.size) // 256-bit

        // Test AES-GCM encryption/decryption using the ECDH derived key
        val originalMessage = "Cosmic Astral Secret Transmission 2026".toByteArray(Charsets.UTF_8)
        val encrypted = CryptoManager.encryptAesGcm(originalMessage, aliceDerivedSecret)
        val decrypted = CryptoManager.decryptAesGcm(encrypted, bobDerivedSecret)

        assertArrayEquals(originalMessage, decrypted)
    }

    @Test
    fun testConstantTimeEqualsByteArray() {
        val a = "SuperSecretToken123456789".toByteArray(Charsets.UTF_8)
        val b = "SuperSecretToken123456789".toByteArray(Charsets.UTF_8)
        val c = "SuperSecretToken123456780".toByteArray(Charsets.UTF_8) // last char differs
        val d = "ShortToken".toByteArray(Charsets.UTF_8)

        assertTrue(CryptoManager.constantTimeEquals(a, b))
        assertFalse(CryptoManager.constantTimeEquals(a, c))
        assertFalse(CryptoManager.constantTimeEquals(a, d))
    }

    @Test
    fun testConstantTimeEqualsString() {
        val tokenA = "a1b2c3d4e5f6789012345678abcdef01"
        val tokenB = "a1b2c3d4e5f6789012345678abcdef01"
        val tokenC = "a1b2c3d4e5f6789012345678abcdef02"

        assertTrue(CryptoManager.constantTimeEquals(tokenA, tokenB))
        assertFalse(CryptoManager.constantTimeEquals(tokenA, tokenC))
    }

    @Test
    fun testHmacSha256ComputeAndVerify() {
        val key = SecretKeySpec(ByteArray(32) { (it + 1).toByte() }, "HmacSHA256")
        val data = "Astron High Throughput Packet 2026".toByteArray(Charsets.UTF_8)

        val hmac = CryptoManager.computeHmac(key, data)
        assertEquals(32, hmac.size) // SHA-256 HMAC is 32 bytes

        // Constant time verification succeeds
        assertTrue(CryptoManager.verifyHmac(key, data, hmac))

        // Tampered data fails verification
        val tamperedData = "Astron High Throughput Packet 2027".toByteArray(Charsets.UTF_8)
        assertFalse(CryptoManager.verifyHmac(key, tamperedData, hmac))

        // Tampered HMAC tag fails verification
        val tamperedHmac = hmac.clone()
        tamperedHmac[0] = (tamperedHmac[0].toInt() xor 0xFF).toByte()
        assertFalse(CryptoManager.verifyHmac(key, data, tamperedHmac))
    }
}
