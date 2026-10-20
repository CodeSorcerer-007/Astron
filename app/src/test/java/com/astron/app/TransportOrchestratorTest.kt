package com.astron.app

import android.content.ContextWrapper
import com.astron.app.core.model.TransportType
import com.astron.app.transport.DeviceCapabilities
import com.astron.app.transport.NetworkType
import com.astron.app.transport.TransportOrchestrator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TransportOrchestratorTest {

    private lateinit var orchestrator: TransportOrchestrator

    private val dummyContext = object : ContextWrapper(null) {
        override fun getSystemService(name: String): Any? = null
        override fun getApplicationContext(): android.content.Context = this
        override fun getPackageName(): String = "com.astron.app"
    }

    @Before
    fun setUp() {
        orchestrator = TransportOrchestrator(dummyContext)
    }

    @Test
    fun testLocalWifiSelectedWhenConnected() {
        val caps = DeviceCapabilities(
            hasLocalNetwork = true,
            isWifiConnected = true,
            hasWifi = true,
            isWifiEnabled = true,
            hasWifiDirect = true
        )

        val scores = orchestrator.evaluate(capabilitiesOverride = caps)
        val selected = orchestrator.selectedTransport.value

        assertEquals(TransportType.LOCAL_WIFI, selected)
        val wifiScore = scores.first { it.type == TransportType.LOCAL_WIFI }
        assertEquals(95, wifiScore.score)
    }

    @Test
    fun testWifiDirectSelectedWhenLocalWifiDown() {
        val caps = DeviceCapabilities(
            hasLocalNetwork = false,
            isWifiConnected = false,
            hasWifi = true,
            isWifiEnabled = true,
            hasWifiDirect = true
        )

        val scores = orchestrator.evaluate(capabilitiesOverride = caps)
        val selected = orchestrator.selectedTransport.value

        assertEquals(TransportType.WIFI_DIRECT, selected)
        val directScore = scores.first { it.type == TransportType.WIFI_DIRECT }
        assertEquals(90, directScore.score)
    }

    @Test
    fun testHotspotSelectedWhenDirectUnavailable() {
        val caps = DeviceCapabilities(
            hasLocalNetwork = false,
            isWifiConnected = false,
            hasWifi = true,
            isWifiEnabled = true,
            hasWifiDirect = false
        )

        orchestrator.evaluate(capabilitiesOverride = caps)
        assertEquals(TransportType.HOTSPOT, orchestrator.selectedTransport.value)
    }

    @Test
    fun testBluetoothSelectedWhenWifiDisabled() {
        val caps = DeviceCapabilities(
            hasWifi = false,
            isWifiEnabled = false,
            hasBluetoothClassic = true,
            isBluetoothEnabled = true
        )

        orchestrator.evaluate(capabilitiesOverride = caps)
        assertEquals(TransportType.BLUETOOTH, orchestrator.selectedTransport.value)
    }

    @Test
    fun testBluetoothPenalizedForLargeFiles() {
        val caps = DeviceCapabilities(
            hasBluetoothClassic = true,
            isBluetoothEnabled = true
        )

        val smallFileScores = orchestrator.evaluate(fileSizeBytes = 10 * 1024 * 1024L, capabilitiesOverride = caps)
        val smallBtScore = smallFileScores.first { it.type == TransportType.BLUETOOTH }.score
        assertEquals(35, smallBtScore)

        val largeFileScores = orchestrator.evaluate(fileSizeBytes = 200 * 1024 * 1024L, capabilitiesOverride = caps)
        val largeBtScore = largeFileScores.first { it.type == TransportType.BLUETOOTH }.score
        assertEquals(20, largeBtScore) // 35 - 15 penalty
    }

    @Test
    fun testInternetRelayPrivacyMode() {
        val caps = DeviceCapabilities(
            hasInternet = true,
            networkType = NetworkType.WIFI
        )

        val onlineScores = orchestrator.evaluate(preferOffline = false, capabilitiesOverride = caps)
        val onlineRelayScore = onlineScores.first { it.type == TransportType.INTERNET_RELAY }.score
        assertTrue(onlineRelayScore > 0)

        val offlineScores = orchestrator.evaluate(preferOffline = true, capabilitiesOverride = caps)
        val offlineRelayScore = offlineScores.first { it.type == TransportType.INTERNET_RELAY }.score
        assertEquals(0, offlineRelayScore)
    }

    @Test
    fun testForceTypeOverride() {
        val caps = DeviceCapabilities(
            hasLocalNetwork = true,
            isWifiConnected = true,
            hasWifi = true,
            isWifiEnabled = true
        )

        orchestrator.evaluate(forceType = TransportType.HOTSPOT, capabilitiesOverride = caps)
        assertEquals(TransportType.HOTSPOT, orchestrator.selectedTransport.value)
    }

    @Test
    fun testFallbackExcludesCurrentTransport() {
        val caps = DeviceCapabilities(
            hasLocalNetwork = true,
            isWifiConnected = true,
            hasWifi = true,
            isWifiEnabled = true,
            hasWifiDirect = true,
            hasBluetoothClassic = true,
            isBluetoothEnabled = true
        )

        orchestrator.evaluate(capabilitiesOverride = caps)
        assertEquals(TransportType.LOCAL_WIFI, orchestrator.selectedTransport.value)

        val fallback = orchestrator.getFallback(TransportType.LOCAL_WIFI, capabilitiesOverride = caps)
        assertEquals(TransportType.WIFI_DIRECT, fallback)
    }
}
