package com.astron.app

import com.astron.app.core.model.TransportType
import com.astron.app.core.nfc.NfcPairingPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcPairingTest {

    @Test
    fun testNfcPayloadToJsonAndFromJsonRoundtrip() {
        val original = NfcPairingPayload(
            deviceName = "Pixel 9 Pro",
            hostIp = "192.168.1.188",
            port = 52737,
            sessionToken = "tok_sec_9999",
            transportType = "LAN"
        )

        val json = original.toJson()
        assertTrue(json.contains("Pixel 9 Pro"))
        assertTrue(json.contains("192.168.1.188"))
        assertTrue(json.contains("52737"))

        val restored = NfcPairingPayload.fromJson(json)
        assertNotNull(restored)
        assertEquals(original.deviceName, restored?.deviceName)
        assertEquals(original.hostIp, restored?.hostIp)
        assertEquals(original.port, restored?.port)
        assertEquals(original.sessionToken, restored?.sessionToken)
        assertEquals(original.transportType, restored?.transportType)
        assertNull(restored?.hotspotSsid)
        assertNull(restored?.hotspotPass)
    }

    @Test
    fun testNfcPayloadToPeerDeviceLocalWifi() {
        val payload = NfcPairingPayload(
            deviceName = "Galaxy Tab S9",
            hostIp = "192.168.0.50",
            port = 52737,
            sessionToken = "tok_lan_123",
            transportType = "LAN"
        )

        val peer = payload.toPeerDevice()
        assertEquals("192.168.0.50:52737", peer.id)
        assertEquals("Galaxy Tab S9", peer.name)
        assertEquals("192.168.0.50", peer.hostAddress)
        assertEquals(52737, peer.port)
        assertEquals(TransportType.LOCAL_WIFI, peer.transportType)
        assertTrue(peer.isTrusted)
        assertEquals(100, peer.signalStrength)
    }

    @Test
    fun testNfcPayloadToPeerDeviceWifiDirect() {
        val payload = NfcPairingPayload(
            deviceName = "Direct Node",
            hostIp = "192.168.49.1",
            port = 52737,
            sessionToken = "tok_p2p_456",
            transportType = "P2P"
        )

        val peer = payload.toPeerDevice()
        assertEquals("192.168.49.1:52737", peer.id)
        assertEquals("Direct Node", peer.name)
        assertEquals("192.168.49.1", peer.hostAddress)
        assertEquals(TransportType.WIFI_DIRECT, peer.transportType)
        assertTrue(peer.isTrusted)
    }

    @Test
    fun testNfcPayloadWithHotspotCredentials() {
        val payload = NfcPairingPayload(
            deviceName = "Astron-Hotspot-Master",
            hostIp = "192.168.43.1",
            port = 52737,
            sessionToken = "tok_hotspot_789",
            transportType = "HOTSPOT",
            hotspotSsid = "Astron-SoftAP-Node",
            hotspotPass = "highspeedpass"
        )

        val json = payload.toJson()
        val restored = NfcPairingPayload.fromJson(json)

        assertNotNull(restored)
        assertEquals("Astron-SoftAP-Node", restored?.hotspotSsid)
        assertEquals("highspeedpass", restored?.hotspotPass)

        val peer = restored!!.toPeerDevice()
        assertEquals("Astron-SoftAP-Node", peer.hotspotSsid)
        assertEquals("highspeedpass", peer.hotspotPass)
    }

    @Test
    fun testNfcPayloadMalformedJsonReturnsNull() {
        val malformed = "{ invalid_json_syntax: true "
        val result = NfcPairingPayload.fromJson(malformed)
        assertNull(result)
    }

    @Test
    fun testNfcPayloadMissingOptionalFieldsDefaults() {
        val json = """{"deviceName":"Minimal Device","hostIp":"10.0.0.1","port":52737}"""
        val restored = NfcPairingPayload.fromJson(json)

        assertNotNull(restored)
        assertEquals("Minimal Device", restored?.deviceName)
        assertEquals("10.0.0.1", restored?.hostIp)
        assertEquals(52737, restored?.port)
        assertEquals("", restored?.sessionToken)
        assertEquals("LAN", restored?.transportType)
        assertNull(restored?.hotspotSsid)
        assertNull(restored?.hotspotPass)
    }

    @Test
    fun testNfcPayloadWithExtendedBeamNegotiation() {
        val payload = NfcPairingPayload(
            deviceName = "Galaxy S24 Ultra",
            hostIp = "192.168.1.55",
            port = 52737,
            sessionToken = "tok_beam_42",
            transportType = "P2P",
            bluetoothMac = "AA:BB:CC:DD:EE:FF",
            wifiDirectMac = "12:34:56:78:9A:BC",
            preferredTransport = "WIFI_DIRECT",
            supportedTransports = listOf("WIFI_DIRECT", "BLUETOOTH", "LAN")
        )

        val json = payload.toJson()
        val restored = NfcPairingPayload.fromJson(json)

        assertNotNull(restored)
        assertEquals("Galaxy S24 Ultra", restored?.deviceName)
        assertEquals("AA:BB:CC:DD:EE:FF", restored?.bluetoothMac)
        assertEquals("12:34:56:78:9A:BC", restored?.wifiDirectMac)
        assertEquals("WIFI_DIRECT", restored?.preferredTransport)
        assertTrue(restored!!.supportedTransports.contains("BLUETOOTH"))

        val peer = restored.toPeerDevice()
        assertEquals(TransportType.WIFI_DIRECT, peer.transportType)
        assertEquals("Galaxy S24 Ultra", peer.name)
    }

    @Test
    fun testNfcManagerNegotiatePreferredTransport() {
        val remotePayload = NfcPairingPayload(
            deviceName = "Receiver Device",
            hostIp = "192.168.1.100",
            port = 52737,
            wifiDirectMac = "AA:11:22:33:44:55",
            preferredTransport = "AUTO"
        )

        val directCaps = setOf(TransportType.WIFI_DIRECT, TransportType.LOCAL_WIFI)
        val negotiated = com.astron.app.core.nfc.NfcManager.negotiatePreferredTransport(directCaps, remotePayload)
        assertEquals(TransportType.LOCAL_WIFI, negotiated)

        // Remote prefers Wi-Fi Direct
        val p2pPayload = remotePayload.copy(preferredTransport = "WIFI_DIRECT")
        val negotiatedP2p = com.astron.app.core.nfc.NfcManager.negotiatePreferredTransport(directCaps, p2pPayload)
        assertEquals(TransportType.WIFI_DIRECT, negotiatedP2p)

        // Offline / No LAN, only Bluetooth
        val btCaps = setOf(TransportType.BLUETOOTH)
        val btPayload = remotePayload.copy(hostIp = "", bluetoothMac = "00:11:22:33:44:55", preferredTransport = "BLUETOOTH")
        val negotiatedBt = com.astron.app.core.nfc.NfcManager.negotiatePreferredTransport(btCaps, btPayload)
        assertEquals(TransportType.BLUETOOTH, negotiatedBt)
    }
}
