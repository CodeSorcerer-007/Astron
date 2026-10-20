package com.astron.app.transport.upnp

import android.content.Context
import android.util.Log
import com.astron.app.core.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

data class UpnpMappingResult(
    val isMapped: Boolean,
    val externalIp: String?,
    val externalPort: Int,
    val internalIp: String?,
    val internalPort: Int,
    val message: String
)

@Singleton
class UpnpPortMapper @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        const val TAG = "UpnpPortMapper"
        const val SSDP_IP = "239.255.255.250"
        const val SSDP_PORT = 1900
        const val DEFAULT_PORT = 52737
        const val DISCOVERY_TIMEOUT_MS = 2500

        fun buildSoapAddPortMapping(
            internalIp: String,
            internalPort: Int,
            externalPort: Int,
            description: String = "Astron-Transfer"
        ): String {
            return """
                <?xml version="1.0"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                    <s:Body>
                        <u:AddPortMapping xmlns:u="urn:schemas-upnp-org:service:WANIPConnection:1">
                            <NewRemoteHost></NewRemoteHost>
                            <NewExternalPort>$externalPort</NewExternalPort>
                            <NewProtocol>TCP</NewProtocol>
                            <NewInternalPort>$internalPort</NewInternalPort>
                            <NewInternalClient>$internalIp</NewInternalClient>
                            <NewEnabled>1</NewEnabled>
                            <NewPortMappingDescription>$description</NewPortMappingDescription>
                            <NewLeaseDuration>3600</NewLeaseDuration>
                        </u:AddPortMapping>
                    </s:Body>
                </s:Envelope>
            """.trimIndent()
        }

        fun buildSoapGetExternalIp(): String {
            return """
                <?xml version="1.0"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                    <s:Body>
                        <u:GetExternalIPAddress xmlns:u="urn:schemas-upnp-org:service:WANIPConnection:1" />
                    </s:Body>
                </s:Envelope>
            """.trimIndent()
        }

        fun extractXmlTag(xml: String, tagName: String): String? {
            val openTag = "<$tagName>"
            val closeTag = "</$tagName>"
            val start = xml.indexOf(openTag)
            if (start == -1) return null
            val end = xml.indexOf(closeTag, start + openTag.length)
            if (end == -1) return null
            return xml.substring(start + openTag.length, end).trim()
        }
    }

    private var activeControlUrl: String? = null
    private var lastMappedPort: Int? = null

    /**
     * Attempts to open an external WAN port mapping on the local Wi-Fi router via UPnP / IGD.
     * Allows direct peer-to-peer transfers over the internet without relay intermediaries.
     */
    suspend fun openPortMapping(
        port: Int = DEFAULT_PORT,
        leaseDurationSecs: Int = 3600
    ): UpnpMappingResult = withContext(Dispatchers.IO) {
        val localIp = NetworkUtils.getLocalIpAddress()
        if (localIp == null) {
            return@withContext UpnpMappingResult(
                isMapped = false,
                externalIp = null,
                externalPort = port,
                internalIp = null,
                internalPort = port,
                message = "No local Wi-Fi IP address available"
            )
        }

        try {
            val controlUrl = discoverIgdControlUrl()
            if (controlUrl == null) {
                return@withContext UpnpMappingResult(
                    isMapped = false,
                    externalIp = null,
                    externalPort = port,
                    internalIp = localIp,
                    internalPort = port,
                    message = "Router does not advertise UPnP IGD service"
                )
            }

            activeControlUrl = controlUrl
            val soapBody = buildSoapAddPortMapping(localIp, port, port)
            val success = sendSoapRequest(controlUrl, "AddPortMapping", soapBody)

            if (success) {
                lastMappedPort = port
                val extIp = queryExternalIp(controlUrl)
                Log.d(TAG, "UPnP mapping established! External WAN: $extIp:$port -> Local: $localIp:$port")
                return@withContext UpnpMappingResult(
                    isMapped = true,
                    externalIp = extIp,
                    externalPort = port,
                    internalIp = localIp,
                    internalPort = port,
                    message = "Direct WAN port mapped successfully via UPnP"
                )
            } else {
                return@withContext UpnpMappingResult(
                    isMapped = false,
                    externalIp = null,
                    externalPort = port,
                    internalIp = localIp,
                    internalPort = port,
                    message = "Router rejected AddPortMapping request"
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "UPnP port mapping encountered an error", e)
            return@withContext UpnpMappingResult(
                isMapped = false,
                externalIp = null,
                externalPort = port,
                internalIp = localIp,
                internalPort = port,
                message = "UPnP exception: ${e.message}"
            )
        }
    }

    /**
     * Broadcasts SSDP M-SEARCH packet to locate the router's IGD description XML.
     */
    private fun discoverIgdControlUrl(): String? {
        val socket = DatagramSocket()
        socket.soTimeout = DISCOVERY_TIMEOUT_MS

        val mSearch = "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: 239.255.255.250:1900\r\n" +
                "ST: urn:schemas-upnp-org:service:WANIPConnection:1\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: 2\r\n\r\n"

        val sendData = mSearch.toByteArray(Charsets.UTF_8)
        val packet = DatagramPacket(sendData, sendData.size, InetAddress.getByName(SSDP_IP), SSDP_PORT)

        try {
            socket.send(packet)
            val receiveBuffer = ByteArray(2048)
            val responsePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
            socket.receive(responsePacket)

            val response = String(responsePacket.data, 0, responsePacket.length, Charsets.UTF_8)
            val locationLine = response.lines().firstOrNull { it.startsWith("LOCATION:", ignoreCase = true) }
            val descUrl = locationLine?.substringAfter(":")?.trim() ?: return null

            return parseControlUrlFromDescription(descUrl)
        } catch (_: Exception) {
            return null
        } finally {
            socket.close()
        }
    }

    private fun parseControlUrlFromDescription(descriptionUrl: String): String? {
        try {
            val url = URL(descriptionUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            val xml = conn.inputStream.bufferedReader().use { it.readText() }

            val controlTag = extractXmlTag(xml, "controlURL") ?: return null
            return if (controlTag.startsWith("http://") || controlTag.startsWith("https://")) {
                controlTag
            } else {
                "${url.protocol}://${url.host}:${url.port}${if (controlTag.startsWith("/")) "" else "/"}$controlTag"
            }
        } catch (_: Exception) {
            return null
        }
    }

    private fun sendSoapRequest(controlUrl: String, action: String, soapXml: String): Boolean {
        try {
            val url = URL(controlUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            conn.setRequestProperty("SOAPAction", "\"urn:schemas-upnp-org:service:WANIPConnection:1#$action\"")

            val bytes = soapXml.toByteArray(Charsets.UTF_8)
            conn.setRequestProperty("Content-Length", bytes.size.toString())

            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            return code in 200..299
        } catch (_: Exception) {
            return false
        }
    }

    private fun queryExternalIp(controlUrl: String): String? {
        try {
            val soapBody = buildSoapGetExternalIp()
            val url = URL(controlUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 2500
            conn.readTimeout = 2500
            conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            conn.setRequestProperty("SOAPAction", "\"urn:schemas-upnp-org:service:WANIPConnection:1#GetExternalIPAddress\"")

            val bytes = soapBody.toByteArray(Charsets.UTF_8)
            conn.outputStream.use { it.write(bytes) }

            if (conn.responseCode in 200..299) {
                val respXml = conn.inputStream.bufferedReader().use { it.readText() }
                return extractXmlTag(respXml, "NewExternalIPAddress")
            }
        } catch (_: Exception) {}
        return null
    }

    suspend fun closePortMapping() = withContext(Dispatchers.IO) {
        val url = activeControlUrl ?: return@withContext
        val port = lastMappedPort ?: return@withContext
        try {
            val deleteSoap = """
                <?xml version="1.0"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                    <s:Body>
                        <u:DeletePortMapping xmlns:u="urn:schemas-upnp-org:service:WANIPConnection:1">
                            <NewRemoteHost></NewRemoteHost>
                            <NewExternalPort>$port</NewExternalPort>
                            <NewProtocol>TCP</NewProtocol>
                        </u:DeletePortMapping>
                    </s:Body>
                </s:Envelope>
            """.trimIndent()
            sendSoapRequest(url, "DeletePortMapping", deleteSoap)
            Log.d(TAG, "UPnP port mapping deleted for port $port")
        } catch (_: Exception) {}
        lastMappedPort = null
        activeControlUrl = null
    }
}
