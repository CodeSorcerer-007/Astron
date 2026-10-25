package com.astron.app

import android.content.Context
import android.content.ContextWrapper
import com.astron.app.transport.aware.WifiAwareTransport
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WifiAwareTransportTest {

    private lateinit var transport: WifiAwareTransport

    private val dummyContext = object : ContextWrapper(null) {
        override fun getSystemService(name: String): Any? = null
        override fun getApplicationContext(): Context = this
        override fun getPackageName(): String = "com.astron.app"
    }

    @Before
    fun setUp() {
        transport = WifiAwareTransport(dummyContext)
    }

    @Test
    fun testInitialStateIsNotActive() {
        assertNotNull(transport)
        assertFalse(transport.isSessionActive.value)
        assertTrue(transport.discoveredPeers.value.isEmpty())
    }

    @Test
    fun testStopResetsStateCleanly() {
        transport.stop()
        assertFalse(transport.isSessionActive.value)
        assertTrue(transport.discoveredPeers.value.isEmpty())
    }

    @Test
    fun testAttachHandlesUnsupportedHardwareGracefully() {
        var errorMsg = ""
        transport.attach(
            onAttached = {},
            onError = { errorMsg = it }
        )
        assertTrue(errorMsg.isNotEmpty())
    }

    @Test
    fun testServiceConstants() {
        org.junit.Assert.assertEquals("com.astron.transfer", WifiAwareTransport.AWARE_SERVICE_NAME)
    }

    @Test
    fun testIsSupportedAndAvailableReturnWithoutException() {
        // Must never throw even on dummy context / emulated environments
        val supported = transport.isSupported()
        val available = transport.isAvailable()
        assertFalse(available) // dummy context has null WifiAwareManager
    }

    @Test
    fun testStartAdvertisingAndDiscoveryWithoutActiveSessionIsNoOp() {
        // Without active session, should cleanly no-op and not throw
        transport.startAdvertising("Test-Device")
        transport.startDiscovery()
        assertFalse(transport.isSessionActive.value)
        assertTrue(transport.discoveredPeers.value.isEmpty())
    }
}
