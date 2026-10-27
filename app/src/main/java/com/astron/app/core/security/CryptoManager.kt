package com.astron.app.core.security

import java.io.InputStream
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {

    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val AES_KEY_SIZE_BYTES = 32

    private val secureRandom = SecureRandom()

    /**
     * Generates a cryptographically strong random token for session authentication and pairing.
     */
    fun generateSessionToken(byteLength: Int = 16): String {
        val bytes = ByteArray(byteLength)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Constant-time comparison between two byte arrays to eliminate timing side-channel attacks.
     */
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        return MessageDigest.isEqual(a, b)
    }

    /**
     * Constant-time comparison between two strings (tokens, hashes) to eliminate timing attacks.
     */
    fun constantTimeEquals(a: String, b: String): Boolean {
        return constantTimeEquals(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
    }

    /**
     * Generates an ephemeral NIST P-256 (secp256r1) Elliptic Curve KeyPair for Diffie-Hellman
     * key exchange, enabling Perfect Forward Secrecy (PFS).
     */
    fun generateEcKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        val ecSpec = ECGenParameterSpec("secp256r1")
        kpg.initialize(ecSpec, secureRandom)
        return kpg.generateKeyPair()
    }

    /**
     * Exports a public key into standard X.509 DER encoded bytes for wire transmission.
     */
    fun exportPublicKey(publicKey: PublicKey): ByteArray {
        return publicKey.encoded
    }

    /**
     * Imports an X.509 DER encoded public key from wire bytes.
     */
    fun importPublicKey(encoded: ByteArray): PublicKey {
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePublic(X509EncodedKeySpec(encoded))
    }

    /**
     * Derives a 256-bit AES symmetric SecretKey using Elliptic Curve Diffie-Hellman (ECDH)
     * and HKDF-SHA256 expansion, guaranteeing Perfect Forward Secrecy across sessions.
     */
    fun deriveSharedSecretEcdh(
        privateKey: PrivateKey,
        peerPublicKeyBytes: ByteArray,
        salt: ByteArray = "Astron-ECDH-Salt-v1".toByteArray(Charsets.UTF_8),
        info: String = "Astron-PFS-Session-Key"
    ): SecretKey {
        val peerPublicKey = importPublicKey(peerPublicKeyBytes)
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(peerPublicKey, true)
        val rawSharedSecret = keyAgreement.generateSecret()

        // Pass raw ECDH shared secret through HKDF-SHA256 for uniform entropy distribution
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(rawSharedSecret)

        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        mac.update(info.toByteArray(Charsets.UTF_8))
        mac.update(1.toByte())
        val okm = mac.doFinal() // 32 bytes (256-bit key)
        return SecretKeySpec(okm, "AES")
    }

    /**
     * Computes an HMAC-SHA256 tag over arbitrary data using a SecretKey.
     */
    fun computeHmac(key: SecretKey, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.encoded, "HmacSHA256"))
        return mac.doFinal(data)
    }

    /**
     * Constant-time HMAC-SHA256 verification.
     */
    fun verifyHmac(key: SecretKey, data: ByteArray, expectedHmac: ByteArray): Boolean {
        val actual = computeHmac(key, data)
        return constantTimeEquals(actual, expectedHmac)
    }

    /**
     * Derives an AES-256 secret key from an ephemeral session token using RFC 5869 HKDF-SHA256.
     */
    fun deriveKeyFromToken(
        token: String,
        salt: ByteArray = "Astron-Wire-Salt-v1".toByteArray(Charsets.UTF_8),
        info: String = "Astron-v1-Transport"
    ): SecretKey {
        val mac = Mac.getInstance("HmacSHA256")
        // HKDF-Extract: PRK = HMAC-Hash(salt, IKM)
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(token.toByteArray(Charsets.UTF_8))

        // HKDF-Expand: OKM = HMAC-Hash(PRK, info || 0x01)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        mac.update(info.toByteArray(Charsets.UTF_8))
        mac.update(1.toByte())
        val okm = mac.doFinal() // 32 bytes (256-bit key)
        return SecretKeySpec(okm, "AES")
    }

    /**
     * Encrypts plaintext bytes using AES-256-GCM with a fresh random 12-byte IV.
     * Returns: [12 bytes IV] + [Ciphertext with 16 bytes GCM Auth Tag]
     */
    fun encryptAesGcm(plainBytes: ByteArray, secretKey: SecretKey): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val cipherBytes = cipher.doFinal(plainBytes)
        val result = ByteArray(iv.size + cipherBytes.size)
        System.arraycopy(iv, 0, result, 0, iv.size)
        System.arraycopy(cipherBytes, 0, result, iv.size, cipherBytes.size)
        return result
    }

    /**
     * Decrypts [12 bytes IV] + [Ciphertext + Auth Tag] using AES-256-GCM.
     */
    fun decryptAesGcm(encryptedBytes: ByteArray, secretKey: SecretKey): ByteArray {
        require(encryptedBytes.size >= GCM_IV_LENGTH) { "Encrypted payload too short for GCM IV" }
        val iv = ByteArray(GCM_IV_LENGTH)
        System.arraycopy(encryptedBytes, 0, iv, 0, GCM_IV_LENGTH)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        return cipher.doFinal(encryptedBytes, GCM_IV_LENGTH, encryptedBytes.size - GCM_IV_LENGTH)
    }

    fun deriveKeyFromSecret(secret: String): SecretKey = deriveKeyFromToken(secret)
    fun encryptPayload(plainBytes: ByteArray, secretKey: SecretKey): ByteArray = encryptAesGcm(plainBytes, secretKey)
    fun decryptPayload(encryptedBytes: ByteArray, secretKey: SecretKey): ByteArray = decryptAesGcm(encryptedBytes, secretKey)

    /**
     * Computes the SHA-256 checksum of an InputStream in a streaming fashion.
     * Memory bounded to 64KB buffer. Stream is cleanly closed to prevent file descriptor leaks.
     */
    fun computeSha256(inputStream: InputStream, onProgress: ((Long) -> Unit)? = null): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var bytesRead: Int
        var totalRead = 0L

        inputStream.use { stream ->
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
                totalRead += bytesRead
                onProgress?.invoke(totalRead)
            }
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Sanitizes untrusted filenames from remote peers to prevent path traversal
     * attacks (e.g. `../../etc/passwd`, Windows drive prefixes, hidden control characters).
     */
    fun sanitizeFilename(rawName: String): String {
        var clean = rawName.trim()
        // Remove path traversal sequences
        clean = clean.replace("..", "_")
        clean = clean.replace("/", "_")
        clean = clean.replace("\\", "_")
        clean = clean.replace("\u0000", "")

        // Remove illegal filesystem characters on Android/FAT32/NTFS
        clean = clean.replace(Regex("[:*?\"<>|]"), "_")

        // Avoid blank or dot-only filenames
        if (clean.isBlank() || clean.all { it == '.' || it == '_' }) {
            clean = "astron_file_${System.currentTimeMillis()}"
        }

        // Limit maximum filename length
        if (clean.length > 200) {
            val extension = clean.substringAfterLast('.', "")
            val base = clean.substringBeforeLast('.')
            clean = if (extension.isNotBlank()) {
                "${base.take(190)}.$extension"
            } else {
                clean.take(200)
            }
        }
        return clean
    }
}

