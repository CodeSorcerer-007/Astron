package com.astron.app

import com.astron.app.core.util.PairingUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PairingUtilsTest {

    @Test
    fun testValidPairingQr() {
        val qr = "astron://pair?name=Galaxy%20Ultra&ip=192.168.1.105&port=52737"
        val peer = PairingUtils.parsePairingQr(qr)

        assertNotNull(peer)
        assertEquals("Galaxy Ultra", peer?.name)
        assertEquals("192.168.1.105", peer?.hostAddress)
        assertEquals(52737, peer?.port)
        assertEquals("peer-qr-192.168.1.105-52737", peer?.id)
    }

    @Test
    fun testDefaultPortFallback() {
        val qr = "astron://pair?name=MacBook&ip=10.0.0.42"
        val peer = PairingUtils.parsePairingQr(qr)

        assertNotNull(peer)
        assertEquals("MacBook", peer?.name)
        assertEquals("10.0.0.42", peer?.hostAddress)
        assertEquals(PairingUtils.DEFAULT_PORT, peer?.port)
    }

    @Test
    fun testMissingIpReturnsNull() {
        val qr = "astron://pair?name=OnlyName"
        val peer = PairingUtils.parsePairingQr(qr)
        assertNull(peer)
    }

    @Test
    fun testInvalidSchemeReturnsNull() {
        val qr = "https://pair?name=Phone&ip=192.168.1.2"
        val peer = PairingUtils.parsePairingQr(qr)
        assertNull(peer)
    }

    @Test
    fun testMalformedQrReturnsNull() {
        val qr = "invalid_random_string"
        val peer = PairingUtils.parsePairingQr(qr)
        assertNull(peer)
    }
}
