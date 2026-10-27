package com.astron.app.transport.stun

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.SecureRandom

/**
 * Result of a STUN discovery query containing public IP, reflexive port, and link metrics.
 */
data class StunResult(
    val isSuccess: Boolean,
    val publicIp: String?,
    val publicPort: Int,
    val localPort: Int,
    val latencyMs: Long,
    val serverUsed: String,
    val message: String
)

/**
 * Lightweight, high-performance RFC 5389 / RFC 8489 STUN (Session Traversal Utilities for NAT)
 * client implemented in pure Kotlin with zero external dependencies.
 *
 * Allows Astron to determine its reflexive public WAN endpoint and NAT characteristics
 * even when residential or mobile cellular routers have UPnP/NAT-PMP disabled.
 */
object StunClient {
    private const val TAG = "StunClient"

    // STUN Message Types
    const val BINDING_REQUEST: Short = 0x0001
    const val BINDING_RESPONSE: Short = 0x0101
    const val BINDING_ERROR_RESPONSE: Short = 0x0111

    // STUN Magic Cookie (RFC 5389, fixed 0x2112A442)
    const val MAGIC_COOKIE: Int = 0x2112A442

    // STUN Attribute Types
    const val ATTR_MAPPED_ADDRESS: Short = 0x0001
    const val ATTR_XOR_MAPPED_ADDRESS: Short = 0x0020

    // Standard public STUN servers for reliable, zero-cost WAN resolution
    val DEFAULT_STUN_SERVERS = listOf(
        "stun.l.google.com" to 19302,
        "stun1.l.google.com" to 19302,
        "stun.cloudflare.com" to 3478
    )

    private val secureRandom = SecureRandom()

    /**
     * Builds a 20-byte RFC 5389 STUN Binding Request packet.
     *
     * Format:
     * - 2 bytes: Message Type (0x0001 = Binding Request)
     * - 2 bytes: Message Length (0x0000 = no attributes in basic request)
     * - 4 bytes: Magic Cookie (0x2112A442)
     * - 12 bytes: Cryptographically random Transaction ID
     */
    fun buildBindingRequest(transactionId: ByteArray? = null): Pair<ByteArray, ByteArray> {
        val txId = transactionId ?: ByteArray(12).also { secureRandom.nextBytes(it) }
        require(txId.size == 12) { "Transaction ID must be exactly 12 bytes" }

        val packet = ByteArray(20)
        // Message Type: 0x0001 (Binding Request)
        packet[0] = 0x00
        packet[1] = 0x01
        // Message Length: 0x0000
        packet[2] = 0x00
        packet[3] = 0x00
        // Magic Cookie: 0x2112A442
        packet[4] = 0x21
        packet[5] = 0x12
        packet[6] = 0xA4.toByte()
        packet[7] = 0x42
        // Transaction ID: 12 bytes
        System.arraycopy(txId, 0, packet, 8, 12)

        return Pair(packet, txId)
    }

    /**
     * Parses an incoming STUN Binding Response packet and extracts the XOR-MAPPED-ADDRESS
     * (RFC 5389) or fallback MAPPED-ADDRESS (RFC 3489).
     */
    fun parseBindingResponse(
        response: ByteArray,
        length: Int,
        expectedTxId: ByteArray
    ): Pair<String, Int>? {
        if (length < 20) {
            Log.w(TAG, "STUN response too short: $length bytes")
            return null
        }

        // Verify Message Type: 0x0101 (Binding Response)
        val msgType = ((response[0].toInt() and 0xFF) shl 8) or (response[1].toInt() and 0xFF)
        if (msgType != BINDING_RESPONSE.toInt()) {
            Log.w(TAG, "Unexpected STUN message type: 0x${msgType.toString(16)}")
            return null
        }

        // Verify Magic Cookie
        val magicCookie = ((response[4].toInt() and 0xFF) shl 24) or
                ((response[5].toInt() and 0xFF) shl 16) or
                ((response[6].toInt() and 0xFF) shl 8) or
                (response[7].toInt() and 0xFF)

        if (magicCookie != MAGIC_COOKIE) {
            Log.w(TAG, "Invalid STUN magic cookie: 0x${magicCookie.toString(16)}")
            return null
        }

        // Verify Transaction ID
        for (i in 0 until 12) {
            if (response[8 + i] != expectedTxId[i]) {
                Log.w(TAG, "STUN Transaction ID mismatch at byte $i")
                return null
            }
        }

        val messageLength = ((response[2].toInt() and 0xFF) shl 8) or (response[3].toInt() and 0xFF)
        val endPos = minOf(length, 20 + messageLength)

        var offset = 20
        var fallbackMappedAddress: Pair<String, Int>? = null

        while (offset + 4 <= endPos) {
            val attrType = ((response[offset].toInt() and 0xFF) shl 8) or (response[offset + 1].toInt() and 0xFF)
            val attrLength = ((response[offset + 2].toInt() and 0xFF) shl 8) or (response[offset + 3].toInt() and 0xFF)
            offset += 4

            if (offset + attrLength > endPos) break

            when (attrType.toShort()) {
                ATTR_XOR_MAPPED_ADDRESS -> {
                    val parsed = parseXorMappedAddress(response, offset, attrLength)
                    if (parsed != null) return parsed
                }
                ATTR_MAPPED_ADDRESS -> {
                    val parsed = parseMappedAddress(response, offset, attrLength)
                    if (parsed != null) fallbackMappedAddress = parsed
                }
            }

            // STUN attributes are padded to 4-byte boundaries
            val padding = (4 - (attrLength % 4)) % 4
            offset += attrLength + padding
        }

        return fallbackMappedAddress
    }

    /**
     * Parses RFC 5389 XOR-MAPPED-ADDRESS.
     * Port is XOR-ed with high 16 bits of MAGIC_COOKIE (0x2112).
     * IPv4 address is XOR-ed with full 32-bit MAGIC_COOKIE (0x2112A442).
     */
    fun parseXorMappedAddress(data: ByteArray, offset: Int, length: Int): Pair<String, Int>? {
        if (length < 8) return null
        val family = data[offset + 1].toInt() and 0xFF
        if (family != 0x01) { // 0x01 = IPv4, 0x02 = IPv6
            return null // Focus on IPv4 NAT traversal
        }

        // De-XOR port with 0x2112
        val rawPort = ((data[offset + 2].toInt() and 0xFF) shl 8) or (data[offset + 3].toInt() and 0xFF)
        val port = rawPort xor (MAGIC_COOKIE ushr 16)

        // De-XOR IPv4 bytes with Magic Cookie 0x2112A442
        val ip0 = (data[offset + 4].toInt() and 0xFF) xor 0x21
        val ip1 = (data[offset + 5].toInt() and 0xFF) xor 0x12
        val ip2 = (data[offset + 6].toInt() and 0xFF) xor 0xA4
        val ip3 = (data[offset + 7].toInt() and 0xFF) xor 0x42

        val ip = "$ip0.$ip1.$ip2.$ip3"
        return Pair(ip, port)
    }

    /**
     * Parses legacy RFC 3489 MAPPED-ADDRESS (non-XOR).
     */
    fun parseMappedAddress(data: ByteArray, offset: Int, length: Int): Pair<String, Int>? {
        if (length < 8) return null
        val family = data[offset + 1].toInt() and 0xFF
        if (family != 0x01) return null

        val port = ((data[offset + 2].toInt() and 0xFF) shl 8) or (data[offset + 3].toInt() and 0xFF)
        val ip0 = data[offset + 4].toInt() and 0xFF
        val ip1 = data[offset + 5].toInt() and 0xFF
        val ip2 = data[offset + 6].toInt() and 0xFF
        val ip3 = data[offset + 7].toInt() and 0xFF

        return Pair("$ip0.$ip1.$ip2.$ip3", port)
    }

    /**
     * Queries STUN servers over UDP to discover the public WAN IP and reflexive port.
     */
    suspend fun discoverPublicEndpoint(
        localPort: Int = 0,
        timeoutMs: Int = 3000,
        customServer: Pair<String, Int>? = null
    ): StunResult = withContext(Dispatchers.IO) {
        val serversToTry = if (customServer != null) {
            listOf(customServer) + DEFAULT_STUN_SERVERS
        } else {
            DEFAULT_STUN_SERVERS
        }

        var lastError = "No STUN server responded"

        for ((host, port) in serversToTry) {
            val startTime = System.currentTimeMillis()
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(localPort).apply {
                    soTimeout = timeoutMs
                }
                val boundPort = socket.localPort

                val serverAddress = InetAddress.getByName(host)
                val (requestBytes, txId) = buildBindingRequest()
                val requestPacket = DatagramPacket(requestBytes, requestBytes.size, serverAddress, port)

                socket.send(requestPacket)

                val responseBuffer = ByteArray(512)
                val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
                socket.receive(responsePacket)

                val elapsed = System.currentTimeMillis() - startTime
                val endpoint = parseBindingResponse(responsePacket.data, responsePacket.length, txId)

                if (endpoint != null) {
                    val (publicIp, publicPort) = endpoint
                    Log.i(TAG, "STUN discovery success via $host:$port -> $publicIp:$publicPort (RTT: ${elapsed}ms)")
                    return@withContext StunResult(
                        isSuccess = true,
                        publicIp = publicIp,
                        publicPort = publicPort,
                        localPort = boundPort,
                        latencyMs = elapsed,
                        serverUsed = "$host:$port",
                        message = "Public WAN endpoint discovered successfully"
                    )
                } else {
                    lastError = "Invalid or unparseable response from $host:$port"
                }
            } catch (e: Exception) {
                lastError = "${e.javaClass.simpleName}: ${e.message} on $host:$port"
                Log.d(TAG, "STUN check failed on $host:$port: ${e.message}")
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }

        StunResult(
            isSuccess = false,
            publicIp = null,
            publicPort = 0,
            localPort = localPort,
            latencyMs = 0L,
            serverUsed = "none",
            message = lastError
        )
    }
}
