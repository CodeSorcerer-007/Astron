package com.astron.app

import com.astron.app.transport.stun.StunClient
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class StunClientTest {

    @Test
    fun testBuildBindingRequestHeaderAndLength() {
        val (packet, txId) = StunClient.buildBindingRequest()

        assertEquals(20, packet.size)
        assertEquals(12, txId.size)

        // Type: 0x0001 (Binding Request)
        val msgType = ((packet[0].toInt() and 0xFF) shl 8) or (packet[1].toInt() and 0xFF)
        assertEquals(0x0001, msgType)

        // Message length: 0
        val msgLen = ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF)
        assertEquals(0, msgLen)

        // Magic Cookie: 0x2112A442
        val cookie = ByteBuffer.wrap(packet, 4, 4).int
        assertEquals(StunClient.MAGIC_COOKIE, cookie)

        // Transaction ID matches
        val extractedTx = packet.copyOfRange(8, 20)
        assertArrayEquals(txId, extractedTx)
    }

    @Test
    fun testParseXorMappedAddressIpv4() {
        // Target: IP 198.51.100.1, Port 54321
        // Port XOR 0x2112 -> 54321 (0xD431) xor 0x2112 = 0xF523
        val rawPortXor = 54321 xor (StunClient.MAGIC_COOKIE ushr 16)
        // IP 198.51.100.1 -> 0xC6336401
        // 0xC6 xor 0x21 = 0xE7
        // 0x33 xor 0x12 = 0x21
        // 0x64 xor 0xA4 = 0xC0
        // 0x01 xor 0x42 = 0x43
        val attrData = ByteArray(8)
        attrData[0] = 0x00 // Reserved
        attrData[1] = 0x01 // IPv4 Family
        attrData[2] = ((rawPortXor ushr 8) and 0xFF).toByte()
        attrData[3] = (rawPortXor and 0xFF).toByte()
        attrData[4] = (198 xor 0x21).toByte()
        attrData[5] = (51 xor 0x12).toByte()
        attrData[6] = (100 xor 0xA4).toByte()
        attrData[7] = (1 xor 0x42).toByte()

        val parsed = StunClient.parseXorMappedAddress(attrData, 0, 8)
        assertNotNull(parsed)
        assertEquals("198.51.100.1", parsed!!.first)
        assertEquals(54321, parsed.second)
    }

    @Test
    fun testParseBindingResponseFullRoundtrip() {
        val fixedTxId = ByteArray(12) { (it + 7).toByte() }

        // Construct 32-byte STUN Binding Response containing XOR-MAPPED-ADDRESS
        // Header (20 bytes) + Attr (8 bytes data + 4 bytes header = 12 bytes) -> Total 32 bytes
        val response = ByteArray(32)

        // Type: 0x0101 (Binding Response)
        response[0] = 0x01
        response[1] = 0x01

        // Length: 12 bytes
        response[2] = 0x00
        response[3] = 0x0C

        // Magic Cookie: 0x2112A442
        ByteBuffer.wrap(response, 4, 4).putInt(StunClient.MAGIC_COOKIE)

        // Transaction ID
        System.arraycopy(fixedTxId, 0, response, 8, 12)

        // Attr Type: 0x0020 (XOR-MAPPED-ADDRESS)
        response[20] = 0x00
        response[21] = 0x20

        // Attr Length: 8 bytes
        response[22] = 0x00
        response[23] = 0x08

        // Attribute value: Port 52737, IP 203.0.113.195
        val xorPort = 52737 xor (StunClient.MAGIC_COOKIE ushr 16)
        response[24] = 0x00 // Reserved
        response[25] = 0x01 // IPv4
        response[26] = ((xorPort ushr 8) and 0xFF).toByte()
        response[27] = (xorPort and 0xFF).toByte()
        response[28] = (203 xor 0x21).toByte()
        response[29] = (0 xor 0x12).toByte()
        response[30] = (113 xor 0xA4).toByte()
        response[31] = (195 xor 0x42).toByte()

        val result = StunClient.parseBindingResponse(response, response.size, fixedTxId)
        assertNotNull(result)
        assertEquals("203.0.113.195", result!!.first)
        assertEquals(52737, result.second)
    }

    @Test
    fun testParseBindingResponseTxIdMismatchRejected() {
        val expectedTxId = ByteArray(12) { 1.toByte() }
        val wrongTxId = ByteArray(12) { 2.toByte() }

        val response = ByteArray(20)
        response[0] = 0x01
        response[1] = 0x01
        response[2] = 0x00
        response[3] = 0x00
        ByteBuffer.wrap(response, 4, 4).putInt(StunClient.MAGIC_COOKIE)
        System.arraycopy(wrongTxId, 0, response, 8, 12)

        val result = StunClient.parseBindingResponse(response, response.size, expectedTxId)
        assertNull(result)
    }

    @Test
    fun testParseLegacyMappedAddress() {
        val attrData = ByteArray(8)
        attrData[0] = 0x00
        attrData[1] = 0x01 // IPv4
        // Port 8080 (0x1F90)
        attrData[2] = 0x1F
        attrData[3] = 0x90.toByte()
        // IP 192.0.2.1
        attrData[4] = 192.toByte()
        attrData[5] = 0.toByte()
        attrData[6] = 2.toByte()
        attrData[7] = 1.toByte()

        val parsed = StunClient.parseMappedAddress(attrData, 0, 8)
        assertNotNull(parsed)
        assertEquals("192.0.2.1", parsed!!.first)
        assertEquals(8080, parsed.second)
    }
}
