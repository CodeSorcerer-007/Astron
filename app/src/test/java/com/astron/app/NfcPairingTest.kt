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
}
