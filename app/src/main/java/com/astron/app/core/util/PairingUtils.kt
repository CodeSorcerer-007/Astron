package com.astron.app.core.util

import com.astron.app.core.model.PeerDevice
import com.astron.app.core.model.TransportType
import java.net.URLDecoder

object PairingUtils {

    const val SCHEME = "astron"
    const val HOST_PAIR = "pair"
    const val DEFAULT_PORT = 52737

    /**
     * Parses an Astron pairing QR string into a PeerDevice.
     * Expected format: astron://pair?name=<name>&ip=<ip>&port=<port>
     */
    fun parsePairingQr(qrText: String): PeerDevice? {
        if (!qrText.startsWith("$SCHEME://$HOST_PAIR?")) return null
        return try {
            val queryString = qrText.substringAfter('?')
            val params = queryString.split('&').mapNotNull { param ->
                val parts = param.split('=', limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }.toMap()

            val ip = params["ip"] ?: return null
            if (ip.isBlank()) return null

            val rawName = params["name"] ?: "Astron Receiver"
            val name = try { URLDecoder.decode(rawName, "UTF-8") } catch (_: Exception) { rawName }
            val port = params["port"]?.toIntOrNull() ?: DEFAULT_PORT

            PeerDevice(
                id = "peer-qr-$ip-$port",
                name = name,
                hostAddress = ip,
                port = port,
                transportType = TransportType.LOCAL_WIFI,
                lastSeen = System.currentTimeMillis()
            )
        } catch (_: Exception) {
            null
        }
    }
}
