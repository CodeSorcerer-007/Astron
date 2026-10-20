package com.astron.app

import com.astron.app.transport.p2p.WifiDirectManager
import com.astron.app.transport.p2p.WifiDirectState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiDirectStateTest {

    @Test
    fun testWifiDirectDefaultGoIp() {
        assertEquals("192.168.49.1", WifiDirectManager.DEFAULT_GO_IP)
    }

    @Test
    fun testWifiDirectStateIdle() {
        val state: WifiDirectState = WifiDirectState.Idle
        assertTrue(state is WifiDirectState.Idle)
    }

    @Test
    fun testWifiDirectStateDiscovering() {
        val state: WifiDirectState = WifiDirectState.Discovering
        assertTrue(state is WifiDirectState.Discovering)
    }

    @Test
    fun testWifiDirectStateGroupCreated() {
        val state = WifiDirectState.GroupCreated(
            groupName = "DIRECT-ab-Astron",
            passphrase = "supersecretpass",
            goIpAddress = "192.168.49.1"
        )
        assertEquals("DIRECT-ab-Astron", state.groupName)
        assertEquals("supersecretpass", state.passphrase)
        assertEquals("192.168.49.1", state.goIpAddress)
    }

    @Test
    fun testWifiDirectStateConnectedGroupOwner() {
        val state = WifiDirectState.Connected(
            isGroupOwner = true,
            groupOwnerIp = "192.168.49.1"
        )
        assertTrue(state.isGroupOwner)
        assertEquals("192.168.49.1", state.groupOwnerIp)
    }

    @Test
    fun testWifiDirectStateConnectedClient() {
        val state = WifiDirectState.Connected(
            isGroupOwner = false,
            groupOwnerIp = "192.168.49.1"
        )
        assertFalse(state.isGroupOwner)
        assertEquals("192.168.49.1", state.groupOwnerIp)
    }

    @Test
    fun testWifiDirectStateError() {
        val state = WifiDirectState.Error("Wi-Fi Direct is disabled or unsupported on this chipset")
        assertTrue(state.message.contains("Wi-Fi Direct is disabled"))
    }
}
