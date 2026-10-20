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
}
