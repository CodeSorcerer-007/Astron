package com.astron.app.core.security

import android.content.Context
import java.io.InputStream
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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

    // Process-restart replay mitigation and memory-bounded nonce tracking
    val serverStartTime: Long = System.currentTimeMillis()
    val seenHandshakeNonces = ConcurrentHashMap<String, Long>()

    @Volatile
    private var cachedIdentityKeyPair: KeyPair? = null

    /**
     * Generates a cryptographically strong random token for session authentication and pairing.
     */
    fun generateSessionToken(byteLength: Int = 16): String {
        val bytes = ByteArray(byteLength)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
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
     * Computes the SHA-256 checksum of a byte array.
     */
    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Asymmetric Cryptography (NIST P-256 ECC, ECDH, ECDSA & HKDF)
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Generates a fresh NIST P-256 (secp256r1) elliptic-curve keypair.
     */
    fun generateEcKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        return kpg.generateKeyPair()
    }

    fun encodePublicKey(publicKey: PublicKey): String {
        return Base64.getEncoder().encodeToString(publicKey.encoded)
    }

    fun decodePublicKey(base64: String): PublicKey {
        val bytes = Base64.getDecoder().decode(base64)
        val kf = KeyFactory.getInstance("EC")
        return kf.generatePublic(X509EncodedKeySpec(bytes))
    }

    /**
     * Returns the full 64-character (256-bit) lowercase hex SHA-256 public key fingerprint.
     */
    fun getPublicKeyFingerprint(publicKey: PublicKey): String {
        return sha256(publicKey.encoded)
    }

    /**
     * Returns the 8-character display peer ID derived from the public key fingerprint.
     */
    fun getDisplayPeerId(publicKey: PublicKey): String {
        return getPublicKeyFingerprint(publicKey).take(8)
    }

    /**
     * Signs data using ECDSA with SHA-256 (SHA256withECDSA). Returns Base64 signature.
     */
    fun sign(privateKey: PrivateKey, data: ByteArray): String {
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(privateKey)
        sig.update(data)
        return Base64.getEncoder().encodeToString(sig.sign())
    }

    /**
     * Verifies an ECDSA-SHA256 signature against data and public key.
     */
    fun verifySignature(publicKey: PublicKey, data: ByteArray, signatureBase64: String): Boolean {
        return try {
            val sigBytes = Base64.getDecoder().decode(signatureBase64)
            val sig = Signature.getInstance("SHA256withECDSA")
            sig.initVerify(publicKey)
            sig.update(data)
            sig.verify(sigBytes)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Computes the Diffie-Hellman shared secret between local private key and remote public key.
     */
    fun computeSharedSecret(privateKey: PrivateKey, peerPublicKey: PublicKey): ByteArray {
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(privateKey)
        ka.doPhase(peerPublicKey, true)
        return ka.generateSecret()
    }

    /**
     * Standard RFC 5869 HKDF-SHA256 key derivation.
     */
    fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int = 32): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        // Extract
        val effectiveSalt = if (salt.isNotEmpty()) salt else ByteArray(32)
        mac.init(SecretKeySpec(effectiveSalt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)

        // Expand
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        var t = ByteArray(0)
        val okm = ByteArray(length)
        var bytesWritten = 0
        var round = 1

        while (bytesWritten < length) {
            mac.update(t)
            mac.update(info)
            mac.update(round.toByte())
            t = mac.doFinal()
            val toCopy = minOf(t.size, length - bytesWritten)
            System.arraycopy(t, 0, okm, bytesWritten, toCopy)
            bytesWritten += toCopy
            round++
        }
        return okm
    }

    /**
     * Derives a transcript-bound AES-256 session key from an ECDH shared secret.
     */
    fun deriveSessionKeyEcdh(
        sharedSecret: ByteArray,
        sessionNonce: String,
        senderFingerprint: String,
        receiverFingerprint: String,
        senderEphemeralPubBase64: String,
        receiverEphemeralPubBase64: String
    ): SecretKey {
        val transcriptInfo = "ASTRON-v2-SESSION:$senderFingerprint:$receiverFingerprint:$sessionNonce:$senderEphemeralPubBase64:$receiverEphemeralPubBase64"
            .toByteArray(Charsets.UTF_8)
        val salt = sessionNonce.toByteArray(Charsets.UTF_8)
        val okm = hkdfSha256(sharedSecret, salt, transcriptInfo, 32)
        return SecretKeySpec(okm, "AES")
    }

    /**
     * Generates a 4-word Short Authentication String (SAS) mnemonic for out-of-band visual verification.
     */
    fun generateSasMnemonic(sessionKeyBytes: ByteArray): String {
        require(sessionKeyBytes.size >= 4) { "Session key must have at least 4 bytes for SAS" }
        val b0 = sessionKeyBytes[0].toInt() and 0xFF
        val b1 = sessionKeyBytes[1].toInt() and 0xFF
        val b2 = sessionKeyBytes[2].toInt() and 0xFF
        val b3 = sessionKeyBytes[3].toInt() and 0xFF
        return "${SAS_DICTIONARY[b0]} ${SAS_DICTIONARY[b1]} ${SAS_DICTIONARY[b2]} ${SAS_DICTIONARY[b3]}"
    }

    /**
     * Validates handshake timestamp freshness (within 5 minutes), rejects handshakes predating
     * current server start time (process-restart replay defense), and tracks seen nonces.
     */
    fun validateHandshakeTimestampAndNonce(peerId: String, timestamp: Long, nonce: String): Boolean {
        if (nonce.isBlank()) return false
        val now = System.currentTimeMillis()
        if (Math.abs(now - timestamp) > 5 * 60 * 1000L) return false
        if (timestamp < serverStartTime - 5_000L) return false

        seenHandshakeNonces.entries.removeIf { now - it.value > 5 * 60 * 1000L }
        if (seenHandshakeNonces.size > 10_000) seenHandshakeNonces.clear()

        val key = "$peerId:$nonce"
        return seenHandshakeNonces.putIfAbsent(key, now) == null
    }

    /**
     * Retrieves or creates a persistent device identity EC keypair.
     */
    fun getOrCreateIdentityKeyPair(context: Context): KeyPair {
        cachedIdentityKeyPair?.let { return it }
        synchronized(this) {
            cachedIdentityKeyPair?.let { return it }
            val prefs = context.getSharedPreferences("astron_crypto_identity", Context.MODE_PRIVATE)
            val privStr = prefs.getString("identity_priv_b64", null)
            val pubStr = prefs.getString("identity_pub_b64", null)
            if (privStr != null && pubStr != null) {
                try {
                    val pubKey = decodePublicKey(pubStr)
                    val privKeyBytes = Base64.getDecoder().decode(privStr)
                    val kf = KeyFactory.getInstance("EC")
                    val privKey = kf.generatePrivate(PKCS8EncodedKeySpec(privKeyBytes))
                    val kp = KeyPair(pubKey, privKey)
                    cachedIdentityKeyPair = kp
                    return kp
                } catch (_: Exception) {}
            }
            val newKp = generateEcKeyPair()
            val privB64 = Base64.getEncoder().encodeToString(newKp.private.encoded)
            val pubB64 = encodePublicKey(newKp.public)
            prefs.edit()
                .putString("identity_priv_b64", privB64)
                .putString("identity_pub_b64", pubB64)
                .apply()
            cachedIdentityKeyPair = newKp
            return newKp
        }
    }

    /**
     * Creates an authenticated ECDH handshake bootstrap token.
     */
    fun createEcdhHandshakeToken(
        sessionId: String,
        senderName: String,
        identityKeyPair: KeyPair,
        ephemeralKeyPair: KeyPair,
        sessionNonce: String = UUID.randomUUID().toString()
    ): String {
        val timestamp = System.currentTimeMillis()
        val identityPubB64 = encodePublicKey(identityKeyPair.public)
        val ephemeralPubB64 = encodePublicKey(ephemeralKeyPair.public)
        val toSign = "$sessionId:$senderName:$timestamp:$sessionNonce:$ephemeralPubB64".toByteArray(Charsets.UTF_8)
        val signature = sign(identityKeyPair.private, toSign)
        return "ASTRON_ECDH:$identityPubB64:$ephemeralPubB64:$timestamp:$sessionNonce:$signature"
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Filesystem Path Traversal Defense
    // ─────────────────────────────────────────────────────────────────────────────

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

    private val SAS_DICTIONARY = arrayOf(
        "aardvark", "absurd", "accrue", "acme", "adrift", "adult", "afflict", "ahead",
        "aimless", "algol", "allow", "alone", "ammo", "ancient", "apple", "artist",
        "assume", "athens", "atlas", "aztec", "baboon", "backfield", "backward", "banjo",
        "beaming", "bedlamp", "beehive", "beeswax", "befriend", "belfast", "berserk", "billiards",
        "bison", "bizarre", "blackout", "blizzard", "blurry", "buzzard", "carbon", "cardiac",
        "carve", "cement", "chair", "chaos", "chariot", "cheetah", "chisel", "circuit",
        "cleric", "clover", "cockpit", "colossus", "comet", "compass", "compound", "concert",
        "concord", "copper", "corridor", "cricket", "crown", "crystal", "cube", "dagger",
        "dancer", "dash", "dawn", "decade", "decimal", "desert", "diploma", "disrupt",
        "diver", "dna", "dolphin", "dragon", "drawer", "drift", "drummer", "dryer",
        "dynamo", "eagle", "earth", "echo", "eclipse", "editor", "elbow", "element",
        "elephant", "elixir", "emerald", "engine", "enigma", "epoch", "eraser", "escape",
        "eureka", "exile", "expert", "falcon", "famous", "feather", "feline", "fiber",
        "firewall", "flagship", "flavor", "flicker", "flight", "flute", "focus", "forest",
        "formula", "fossil", "fractal", "freedom", "friction", "frontier", "galaxy", "galore",
        "gateway", "gear", "gemini", "geology", "glacier", "glider", "globe", "glory",
        "goddess", "gold", "gorilla", "gradient", "gravity", "grove", "guitar", "guru",
        "habitat", "halogen", "hamlet", "harbor", "harmony", "harvest", "haven", "hawk",
        "headway", "helmet", "hero", "hexagon", "highway", "horizon", "hound", "hover",
        "humble", "humor", "hunter", "hydra", "iceberg", "icon", "iguana", "image",
        "impact", "infinite", "insight", "inspire", "integer", "island", "isotope", "ivory",
        "jaguar", "javelin", "jupiter", "karma", "kayak", "keyboard", "kinetic", "kingdom",
        "kitchen", "laser", "launch", "legacy", "legend", "leopard", "level", "liberty",
        "lightning", "linear", "lion", "liquid", "lizard", "logic", "lunar", "magnet",
        "maximum", "melody", "mercury", "meteor", "mind", "mirage", "mirror", "missile",
        "monarch", "mosaic", "mountain", "nebula", "nectar", "nemesis", "neon", "network",
        "neuron", "neutron", "nexus", "ninja", "nitrogen", "nomad", "nova", "nuclear",
        "oasis", "obsidian", "ocean", "octave", "omega", "online", "opera", "optic",
        "oracle", "orbital", "orchid", "origin", "oxygen", "pacific", "palace", "panther",
        "paradox", "parallel", "paramount", "particle", "partner", "pattern", "pegasus", "pendulum",
        "phoenix", "photon", "pioneer", "pixel", "planet", "plasma", "platinum", "polaris",
        "portal", "potion", "prada", "prism", "profile", "proton", "pulse", "pyramid",
        "quantum", "radar", "radiant", "radium", "rainbow", "ranger", "raptor", "reactor",
        "rebel", "relic", "resonance", "retro", "rhythm", "ridge", "rocket", "ruby"
    )
}
