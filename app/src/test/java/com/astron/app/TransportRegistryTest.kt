package com.astron.app

import android.content.Context
import android.content.ContextWrapper
import com.astron.app.core.storage.StorageManager
import com.astron.app.transport.TransportRegistry
import org.junit.Assert.assertNotNull
import org.junit.Test

class TransportRegistryTest {

    private val dummyContext = object : ContextWrapper(null) {
        override fun getSystemService(name: String): Any? = null
        override fun getApplicationContext(): Context = this
        override fun getPackageName(): String = "com.astron.app"
    }

    @Test
    fun testTransportRegistryEncapsulation() {
        val storageManager = StorageManager(dummyContext)
        val registry = TransportRegistry.createDefault(dummyContext, storageManager)

        assertNotNull(registry.lanTransport)
        assertNotNull(registry.webDropServer)
        assertNotNull(registry.lightDropManager)
        assertNotNull(registry.hotspotManager)
        assertNotNull(registry.bluetoothTransport)
        assertNotNull(registry.internetRelayTransport)
        assertNotNull(registry.transportOrchestrator)
        assertNotNull(registry.bleBeaconManager)
        assertNotNull(registry.upnpPortMapper)
        assertNotNull(registry.multicastGroupManager)
        assertNotNull(registry.wifiAwareTransport)
        assertNotNull(registry.wifiDirectManager)
        assertNotNull(registry.usbOtgManager)

        // Close should cleanly execute without exceptions
        registry.close()
    }
}
