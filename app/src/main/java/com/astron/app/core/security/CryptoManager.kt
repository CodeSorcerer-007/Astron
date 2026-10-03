package com.astron.app.core.security

import java.io.InputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
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
     * Derives an AES-256 secret key from an ephemeral session token.
     */
    fun deriveKeyFromToken(token: String): SecretKey {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(token.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
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
