package com.astron.app.transport

import android.content.Context
import com.astron.app.core.storage.StorageManager
import com.astron.app.transport.aware.WifiAwareTransport
import com.astron.app.transport.ble.BleBeaconManager
import com.astron.app.transport.bluetooth.BluetoothClassicTransport
import com.astron.app.transport.hotspot.HotspotManager
import com.astron.app.transport.internet.InternetRelayTransport
import com.astron.app.transport.lan.LanTransport
import com.astron.app.transport.lightdrop.LightDropManager
import com.astron.app.transport.multicast.MulticastGroupManager
import com.astron.app.transport.p2p.WifiDirectManager
import com.astron.app.transport.upnp.UpnpPortMapper
import com.astron.app.transport.usb.UsbOtgManager
import com.astron.app.transport.webdrop.WebDropServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.Closeable

/**
 * Centralized Transport Registry managing all 12+ dynamic transfer engines,
 * lifecycle state, and hardware radio teardown in Astron.
 */
class TransportRegistry(
    val lanTransport: LanTransport,
    val webDropServer: WebDropServer,
    val lightDropManager: LightDropManager,
    val hotspotManager: HotspotManager,
    val bluetoothTransport: BluetoothClassicTransport,
    val internetRelayTransport: InternetRelayTransport,
    val transportOrchestrator: TransportOrchestrator,
    val bleBeaconManager: BleBeaconManager,
    val upnpPortMapper: UpnpPortMapper,
    val multicastGroupManager: MulticastGroupManager,
    val wifiAwareTransport: WifiAwareTransport,
    val wifiDirectManager: WifiDirectManager,
    val usbOtgManager: UsbOtgManager
) : Closeable {

    companion object {
        fun createDefault(context: Context, storageManager: StorageManager): TransportRegistry {
            return TransportRegistry(
                lanTransport = LanTransport(context, storageManager),
                webDropServer = WebDropServer(context, storageManager),
                lightDropManager = LightDropManager(),
                hotspotManager = HotspotManager(context),
                bluetoothTransport = BluetoothClassicTransport(context),
                internetRelayTransport = InternetRelayTransport(context, storageManager),
                transportOrchestrator = TransportOrchestrator(context),
                bleBeaconManager = BleBeaconManager(context),
                upnpPortMapper = UpnpPortMapper(context),
                multicastGroupManager = MulticastGroupManager(context, storageManager),
                wifiAwareTransport = WifiAwareTransport(context),
                wifiDirectManager = WifiDirectManager(context),
                usbOtgManager = UsbOtgManager(context)
            )
        }
    }

    override fun close() {
        hotspotManager.close()
        bluetoothTransport.disconnect()
        internetRelayTransport.close()
        bleBeaconManager.stopBeacon()
        bleBeaconManager.stopScanning()
        multicastGroupManager.stopGroupTransfer()
        wifiAwareTransport.stop()
        wifiDirectManager.teardown()
        usbOtgManager.teardown()
        hotspotManager.disconnectFromHotspot()
        try {
            CoroutineScope(Dispatchers.IO).launch { upnpPortMapper.closePortMapping() }
        } catch (_: Throwable) {}
    }
}

