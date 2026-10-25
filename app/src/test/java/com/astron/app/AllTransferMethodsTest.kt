package com.astron.app

import android.content.ContextWrapper
import com.astron.app.core.model.PeerDevice
import com.astron.app.core.model.TransferItem
import com.astron.app.core.model.TransferManifest
import com.astron.app.core.model.TransportType
import com.astron.app.core.storage.StorageManager
import com.astron.app.core.util.PairingUtils
import com.astron.app.data.HistoryRepository
import com.astron.app.data.PreferencesRepository
import com.astron.app.transfer.TransferEngine
import com.astron.app.transport.CapabilityDetector
import com.astron.app.transport.DeviceCapabilities
import com.astron.app.transport.TransportOrchestrator
import com.astron.app.transport.hotspot.HotspotConfig
import com.astron.app.transport.lightdrop.LightDropManager
import com.astron.app.transport.lightdrop.OpticalDensity
import com.astron.app.transport.multicast.MulticastGroupManager
import com.astron.app.transport.p2p.WifiDirectManager
import com.astron.app.transport.p2p.WifiDirectState
import com.astron.app.transport.upnp.UpnpPortMapper
import com.astron.app.transport.usb.UsbDriveInfo
import com.astron.app.transport.usb.UsbOtgManager
import com.astron.app.transport.usb.UsbOtgState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AllTransferMethodsTest {

    private val dummyContext = object : ContextWrapper(null) {
        override fun getSystemService(name: String): Any? = null
        override fun getApplicationContext(): android.content.Context = this
        override fun getPackageName(): String = "com.astron.app"
    }

    private val storageManager = StorageManager(dummyContext)
    private val preferencesRepository = PreferencesRepository(dummyContext)
    private val historyRepository = HistoryRepository(dummyContext)

    // ─────────────────────────────────────────────────────────────────────────────
    // 1. LOCAL_WIFI
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    fun testLocalWifiScoringAndPeerRegistration() {
        val orchestrator = TransportOrchestrator(dummyContext)
        val caps = DeviceCapabilities(hasLocalNetwork = true, isWifiConnected = true)
        val scores = orchestrator.evaluate(capabilitiesOverride = caps)
        val localWifiScore = scores.find { it.type == TransportType.LOCAL_WIFI }

        assertNotNull(localWifiScore)
        assertTrue(localWifiScore!!.score >= 95)
        assertEquals(TransportType.LOCAL_WIFI, orchestrator.selectedTransport.value)

        val engine = TransferEngine(dummyContext, storageManager, preferencesRepository, historyRepository)
        val peer = PeerDevice("p1", "LAN Peer", "192.168.1.50", 52737, TransportType.LOCAL_WIFI)
        engine.registerDiscoveredPeer(peer)
        assertTrue(engine.discoveredPeers.value.any { it.id == "p1" && it.transportType == TransportType.LOCAL_WIFI })
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 2. WIFI_DIRECT
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    fun testWifiDirectStateAndPeer() {
        val directManager = WifiDirectManager(dummyContext)
        assertEquals(WifiDirectState.Idle, directManager.state.value)

        val groupState = WifiDirectState.GroupCreated("DIRECT-Astron-77", "secretKey123", "192.168.49.1")
        assertEquals("192.168.49.1", groupState.goIpAddress)
        assertEquals("DIRECT-Astron-77", groupState.groupName)

        val orchestrator = TransportOrchestrator(dummyContext)
        val caps = DeviceCapabilities(hasWifiDirect = true, isWifiEnabled = true)
        val scores = orchestrator.evaluate(capabilitiesOverride = caps)
        val directScore = scores.find { it.type == TransportType.WIFI_DIRECT }
        assertNotNull(directScore)
        assertTrue(directScore!!.score >= 90)

        // Verify pairing QR detection
        val qr = "astron://pair?name=DirectPeer&ip=192.168.49.1&port=52737&ssid=DIRECT-Astron-77&pass=secret"
        val parsed = PairingUtils.parsePairingQr(qr)
        assertNotNull(parsed)
        assertEquals(TransportType.WIFI_DIRECT, parsed?.transportType)
        assertEquals("DIRECT-Astron-77", parsed?.hotspotSsid)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 3. HOTSPOT
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    fun testHotspotConfigAndAutoConnectParsing() {
        val config = HotspotConfig("Astron-Hotspot-42", "pass12345", "192.168.43.1", 52737, true)
        assertEquals("Astron-Hotspot-42", config.ssid)
        assertEquals("192.168.43.1", config.ipAddress)
        assertTrue(config.isActive)

        val qr = PairingUtils.createPairingQr("Receiver", config.ipAddress, config.port, config.ssid, config.passphrase)
        val parsed = PairingUtils.parsePairingQr(qr)
        assertNotNull(parsed)
        assertEquals(TransportType.HOTSPOT, parsed?.transportType)
        assertEquals(config.ssid, parsed?.hotspotSsid)
        assertEquals(config.passphrase, parsed?.hotspotPass)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 4. BLUETOOTH
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    fun testBluetoothScoringAndFallback() {
        val orchestrator = TransportOrchestrator(dummyContext)
        val caps = DeviceCapabilities(hasBluetoothClassic = true, isBluetoothEnabled = true)
        val scores = orchestrator.evaluate(fileSizeBytes = 5 * 1024 * 1024L, capabilitiesOverride = caps)
        val btScore = scores.find { it.type == TransportType.BLUETOOTH }

        assertNotNull(btScore)
        assertEquals(35, btScore!!.score)
        assertEquals(TransportType.BLUETOOTH, orchestrator.selectedTransport.value)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 5. BLE_BEACON
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    fun testBleBeaconScoring() {
        val orchestrator = TransportOrchestrator(dummyContext)
        val caps = DeviceCapabilities(hasBle = true, isBluetoothEnabled = true)
        val scores = orchestrator.evaluate(capabilitiesOverride = caps)
        val bleScore = scores.find { it.type == TransportType.BLE_BEACON }

        assertNotNull(bleScore)
        assertEquals(30, bleScore!!.score)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 6. INTERNET_RELAY
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    fun testInternetRelayScoringAndPrivacyMode() {
        val orchestrator = TransportOrchestrator(dummyContext)
        val caps = DeviceCapabilities(hasInternet = true)
        val scores = orchestrator.evaluate(capabilitiesOverride = caps, preferOffline = false)
        val relayScore = scores.find { it.type == TransportType.INTERNET_RELAY }
        assertNotNull(relayScore)
        assertEquals(25, relayScore!!.score)

        // Privacy mode penalizes internet relay to 0
        val offlineScores = orchestrator.evaluate(capabilitiesOverride = caps, preferOffline = true)
        val offlineRelay = offlineScores.find { it.type == TransportType.INTERNET_RELAY }
        assertEquals(0, offlineRelay?.score)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 7. WEB_DROP
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    fun testWebDropScoring() {
        val orchestrator = TransportOrchestrator(dummyContext)
        val caps = DeviceCapabilities(hasLocalNetwork = true)
        val scores = orchestrator.evaluate(capabilitiesOverride = caps)
        val webDropScore = scores.find { it.type == TransportType.WEB_DROP }

        assertNotNull(webDropScore)
        assertEquals(15, webDropScore!!.score)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 8. OPTICAL_LIGHT
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    fun testOpticalLightDropEncodingDecoding() {
        val lightDrop = LightDropManager()
        val data = "Zero-Radio Optical Frame Test Payload 2026".toByteArray(Charsets.UTF_8)
        val frames = lightDrop.encodeBytesToFrames(data, sessionId = 42, density = OpticalDensity.BALANCED)

        assertTrue(frames.isNotEmpty())
        val serialized = lightDrop.serializeFrame(frames[0])
        assertTrue(serialized.startsWith("ASTL:1:42:0:"))

        val parsed = lightDrop.parseFrame(serialized)
        assertNotNull(parsed)
        assertEquals(42, parsed?.sessionId)
        assertEquals(0, parsed?.frameIndex)
        assertEquals(data.size, parsed?.payload?.size)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 9. MULTICAST_GROUP
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    fun testMulticastGroupFecRecovery() {
        val c1 = byteArrayOf(1, 2, 3, 4)
        val c2 = byteArrayOf(5, 6, 7, 8)
        val parity = MulticastGroupManager.xorParity(listOf(c1, c2))

        val recoveredC2 = MulticastGroupManager.recoverMissingChunk(listOf(c1), parity)
        assertEquals(c2.toList(), recoveredC2.toList())

        val orchestrator = TransportOrchestrator(dummyContext)
        val caps = DeviceCapabilities(hasLocalNetwork = true, isWifiConnected = true)
        val scores = orchestrator.evaluate(capabilitiesOverride = caps)
        val multicastScore = scores.find { it.type == TransportType.MULTICAST_GROUP }
        assertNotNull(multicastScore)
        assertEquals(85, multicastScore!!.score)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 10. UPNP_DIRECT
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    fun testUpnpDirectSoapAndScoring() {
        val soap = UpnpPortMapper.buildSoapAddPortMapping("192.168.1.100", 52737, 52737, "Astron-Test")
        assertTrue(soap.contains("<NewInternalPort>52737</NewInternalPort>"))
        assertTrue(soap.contains("<NewInternalClient>192.168.1.100</NewInternalClient>"))

        val orchestrator = TransportOrchestrator(dummyContext)
        val caps = DeviceCapabilities(hasLocalNetwork = true, hasInternet = true)
        val scores = orchestrator.evaluate(capabilitiesOverride = caps)
        val upnpScore = scores.find { it.type == TransportType.UPNP_DIRECT }
        assertNotNull(upnpScore)
        assertEquals(80, upnpScore!!.score)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 11. WIFI_AWARE
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    fun testWifiAwareScoring() {
        val orchestrator = TransportOrchestrator(dummyContext)
        val caps = DeviceCapabilities(hasWifiAware = true, isWifiEnabled = true)
        val scores = orchestrator.evaluate(capabilitiesOverride = caps)
        val awareScore = scores.find { it.type == TransportType.WIFI_AWARE }

        assertNotNull(awareScore)
        assertEquals(88, awareScore!!.score)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 12. USB_OTG
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    fun testUsbOtgScoringAndExport() = runBlocking {
        val orchestrator = TransportOrchestrator(dummyContext)
        val caps = DeviceCapabilities(hasUsbHost = true, hasUsbDriveAttached = true)
        val scores = orchestrator.evaluate(capabilitiesOverride = caps)
        val otgScore = scores.find { it.type == TransportType.USB_OTG }

        assertNotNull(otgScore)
        assertEquals(99, otgScore!!.score)
        assertEquals(TransportType.USB_OTG, orchestrator.selectedTransport.value)

        val otgManager = UsbOtgManager(dummyContext)
        val tempDir = File(System.getProperty("java.io.tmpdir"), "astron_otg_test_${System.currentTimeMillis()}").also { it.mkdirs() }
        val testDrive = UsbDriveInfo("drive_1", "SanDisk OTG 128GB", tempDir.absolutePath, 128L * 1024 * 1024 * 1024, 64L * 1024 * 1024 * 1024, true)

        val peer = otgManager.toPeerDevice(testDrive)
        assertEquals(TransportType.USB_OTG, peer.transportType)
        assertEquals("SanDisk OTG 128GB", peer.name)

        // Test export to USB destination
        val srcFile = File(tempDir, "sample.txt").also { it.writeText("Astron High-Speed USB OTG Transfer 2026") }
        val item = TransferItem(
            id = "item_1",
            name = "sample.txt",
            size = srcFile.length(),
            mimeType = "text/plain",
            realFilePath = srcFile.absolutePath
        )

        var verified = false
        val ok = otgManager.exportFilesToUsb(
            targetDrive = testDrive,
            items = listOf(item),
            onProgress = { _, _, _ -> },
            onFileCompleted = { verified = true },
            isCancelled = { false }
        )

        assertTrue(ok)
        assertTrue(verified)
        val exportedFile = File(tempDir, "Astron_Export/sample.txt")
        assertTrue(exportedFile.exists())
        assertEquals("Astron High-Speed USB OTG Transfer 2026", exportedFile.readText())

        tempDir.deleteRecursively()
        otgManager.teardown()
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Complete 12-Transport Full Matrix Validation
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    fun testAllTwelveTransportsAreRepresented() {
        val allTypes = TransportType.entries
        assertEquals("Astron must define and support exactly 12 transfer methods", 12, allTypes.size)

        val expected = setOf(
            TransportType.LOCAL_WIFI,
            TransportType.WIFI_DIRECT,
            TransportType.HOTSPOT,
            TransportType.BLUETOOTH,
            TransportType.BLE_BEACON,
            TransportType.INTERNET_RELAY,
            TransportType.WEB_DROP,
            TransportType.OPTICAL_LIGHT,
            TransportType.MULTICAST_GROUP,
            TransportType.UPNP_DIRECT,
            TransportType.WIFI_AWARE,
            TransportType.USB_OTG
        )

        assertEquals(expected, allTypes.toSet())
    }
}
