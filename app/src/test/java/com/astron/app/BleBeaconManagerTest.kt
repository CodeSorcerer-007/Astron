package com.astron.app

import com.astron.app.transport.ble.BleBeaconManager
import com.astron.app.transport.ble.BleBeaconPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BleBeaconManagerTest {

    @Test
    fun testSerializeAndParsePayloadRoundtrip() {
        val original = BleBeaconPayload(
            version = 1,
            flags = BleBeaconManager.FLAG_LAN_READY or BleBeaconManager.FLAG_HOTSPOT_ACTIVE,
            port = 52737,
            deviceName = "GalaxyUltra",
            deviceId = "device-uuid-999"
        )

        val bytes = BleBeaconManager.serializePayload(original)
        assertTrue(bytes.isNotEmpty())

        val parsed = BleBeaconManager.parsePayload(bytes, "device-uuid-999")
        assertNotNull(parsed)
        assertEquals(original.version, parsed!!.version)
        assertEquals(original.flags, parsed.flags)
        assertEquals(original.port, parsed.port)
        assertEquals("GalaxyUltra", parsed.deviceName)
        assertEquals("device-uuid-999", parsed.deviceId)
    }

    @Test
    fun testParsePayloadInvalidOrShortData() {
        assertNull(BleBeaconManager.parsePayload(ByteArray(0), "dev-1"))
        assertNull(BleBeaconManager.parsePayload(ByteArray(3), "dev-1"))
    }

    @Test
    fun testLongDeviceNameTruncation() {
        val original = BleBeaconPayload(
            version = 1,
            flags = 0,
            port = 8080,
            deviceName = "VeryLongAndroidDeviceNameThatExceedsTwelveBytes",
            deviceId = "dev-trunc"
        )
        val bytes = BleBeaconManager.serializePayload(original)
        // 4 header bytes + at most 12 name bytes = 16 bytes max
        assertTrue(bytes.size <= 16)

        val parsed = BleBeaconManager.parsePayload(bytes, "dev-trunc")
        assertNotNull(parsed)
        assertEquals(12, parsed!!.deviceName.toByteArray(Charsets.UTF_8).size)
    }

    @Test
    fun testEstimateRssiSignal() {
        assertEquals(98, BleBeaconManager.estimateRssiSignal(-40))
        assertEquals(98, BleBeaconManager.estimateRssiSignal(-50))
        assertEquals(85, BleBeaconManager.estimateRssiSignal(-55))
        assertEquals(85, BleBeaconManager.estimateRssiSignal(-65))
        assertEquals(65, BleBeaconManager.estimateRssiSignal(-70))
        assertEquals(65, BleBeaconManager.estimateRssiSignal(-80))
        assertEquals(40, BleBeaconManager.estimateRssiSignal(-85))
        assertEquals(40, BleBeaconManager.estimateRssiSignal(-100))
    }
}
