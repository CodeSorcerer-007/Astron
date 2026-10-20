package com.astron.app

import com.astron.app.core.util.PairingUtils
import com.astron.app.transport.hotspot.HotspotConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HotspotAndPairingTest {

    @Test
    fun testStandardPairingQrBackwardsCompatibility() {
        val qr = PairingUtils.createPairingQr(
            name = "Pixel 8 Pro",
            ip = "192.168.1.150",
            port = 52737
        )

        assertTrue(qr.startsWith("astron://pair?"))
        val peer = PairingUtils.parsePairingQr(qr)

        assertNotNull(peer)
        assertEquals("Pixel 8 Pro", peer?.name)
        assertEquals("192.168.1.150", peer?.hostAddress)
        assertEquals(52737, peer?.port)
        assertNull(peer?.hotspotSsid)
        assertNull(peer?.hotspotPass)
    }

    @Test
    fun testPairingQrWithHotspotCredentials() {
        val ssid = "Astron-Direct-8492"
        val pass = "k9s2m4p8"
        val qr = PairingUtils.createPairingQr(
            name = "Astron Master Node",
            ip = "192.168.49.1",
            port = 52737,
            hotspotSsid = ssid,
            hotspotPass = pass
        )

        assertTrue(qr.contains("ssid=Astron-Direct-8492"))
        assertTrue(qr.contains("pass=k9s2m4p8"))

        val peer = PairingUtils.parsePairingQr(qr)
        assertNotNull(peer)
        assertEquals("Astron Master Node", peer?.name)
        assertEquals("192.168.49.1", peer?.hostAddress)
        assertEquals(52737, peer?.port)
        assertEquals(ssid, peer?.hotspotSsid)
        assertEquals(pass, peer?.hotspotPass)
    }

    @Test
    fun testCreateWifiConnectQrStandardFormat() {
        val wifiQr = PairingUtils.createWifiConnectQr("Astron-Direct-1234", "supersecret")
        assertEquals("WIFI:S:Astron-Direct-1234;T:WPA;P:supersecret;;", wifiQr)
    }

    @Test
    fun testHotspotConfigProperties() {
        val config = HotspotConfig(
            ssid = "Astron-Zero-1",
            passphrase = "password123",
            ipAddress = "192.168.43.1",
            port = 52737
        )

        assertEquals("Astron-Zero-1", config.ssid)
        assertEquals("password123", config.passphrase)
        assertEquals("192.168.43.1", config.ipAddress)
        assertEquals(52737, config.port)
    }
}
