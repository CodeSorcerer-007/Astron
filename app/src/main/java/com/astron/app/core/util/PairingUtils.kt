package com.astron.app.core.util

import com.astron.app.core.model.PeerDevice
import com.astron.app.core.model.TransportType
import java.net.URLDecoder
import java.net.URLEncoder

object PairingUtils {

    const val SCHEME = "astron"
    const val HOST_PAIR = "pair"
    const val DEFAULT_PORT = 52737

    /**
     * Creates an Astron pairing QR URL payload with optional embedded hotspot credentials.
     */
    fun createPairingQr(
        name: String,
        ip: String,
        port: Int = DEFAULT_PORT,
        hotspotSsid: String? = null,
        hotspotPass: String? = null
    ): String {
        val encodedName = try { URLEncoder.encode(name, "UTF-8") } catch (_: Exception) { name }
        val base = "$SCHEME://$HOST_PAIR?name=$encodedName&ip=$ip&port=$port"
        return if (!hotspotSsid.isNullOrBlank() && !hotspotPass.isNullOrBlank()) {
            val encodedSsid = try { URLEncoder.encode(hotspotSsid, "UTF-8") } catch (_: Exception) { hotspotSsid }
            val encodedPass = try { URLEncoder.encode(hotspotPass, "UTF-8") } catch (_: Exception) { hotspotPass }
            "$base&ssid=$encodedSsid&pass=$encodedPass"
        } else {
            base
        }
    }

    /**
     * Creates a universal Wi-Fi standard QR code format (`WIFI:S:<SSID>;T:WPA;P:<PASSWORD>;;`)
     * compatible with any camera or iOS/Android native scanner.
     */
    fun createWifiConnectQr(ssid: String, pass: String): String {
        return "WIFI:S:$ssid;T:WPA;P:$pass;;"
    }

    /**
     * Parses an Astron pairing QR string into a PeerDevice.
     * Expected format: astron://pair?name=<name>&ip=<ip>&port=<port>&ssid=<ssid>&pass=<pass>
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

            val rawSsid = params["ssid"]
            val ssid = rawSsid?.let { try { URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it } }

            val rawPass = params["pass"]
            val pass = rawPass?.let { try { URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it } }

            PeerDevice(
                id = "peer-qr-$ip-$port",
                name = name,
                hostAddress = ip,
                port = port,
                transportType = if (ssid != null) TransportType.WIFI_DIRECT else TransportType.LOCAL_WIFI,
                hotspotSsid = ssid,
                hotspotPass = pass,
                lastSeen = System.currentTimeMillis()
            )
        } catch (_: Exception) {
            null
        }
    }
}
