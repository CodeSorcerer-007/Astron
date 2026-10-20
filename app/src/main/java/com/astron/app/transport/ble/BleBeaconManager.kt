package com.astron.app.transport.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.astron.app.core.model.PeerDevice
import com.astron.app.core.model.TransportType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

data class BleBeaconPayload(
    val version: Int,
    val flags: Int,
    val port: Int,
    val deviceName: String,
    val deviceId: String
)

@Singleton
class BleBeaconManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        const val TAG = "BleBeaconManager"
        const val ASTRON_SERVICE_UUID_STRING = "0000a570-0000-1000-8000-00805f9b34fb"
        val ASTRON_SERVICE_UUID: ParcelUuid by lazy { ParcelUuid.fromString(ASTRON_SERVICE_UUID_STRING) }
        const val ASTRON_MANUFACTURER_ID = 0x0570
        const val FLAG_LAN_READY = 0x01
        const val FLAG_HOTSPOT_ACTIVE = 0x02
        const val FLAG_RELAY_ACTIVE = 0x04

        fun serializePayload(payload: BleBeaconPayload): ByteArray {
            val nameBytes = payload.deviceName.toByteArray(Charsets.UTF_8).take(12).toByteArray()
            val buffer = ByteBuffer.allocate(4 + nameBytes.size)
            buffer.put(payload.version.toByte())
            buffer.put(payload.flags.toByte())
            buffer.putShort(payload.port.toShort())
            buffer.put(nameBytes)
            return buffer.array()
        }

        fun parsePayload(data: ByteArray, deviceId: String): BleBeaconPayload? {
            if (data.size < 4) return null
            val buffer = ByteBuffer.wrap(data)
            val version = buffer.get().toInt() and 0xFF
            val flags = buffer.get().toInt() and 0xFF
            val port = buffer.short.toInt() and 0xFFFF
            val nameBytes = ByteArray(buffer.remaining())
            buffer.get(nameBytes)
            val name = String(nameBytes, Charsets.UTF_8)
            return BleBeaconPayload(
                version = version,
                flags = flags,
                port = if (port == 0) 52737 else port,
                deviceName = name.ifBlank { "Astron Peer" },
                deviceId = deviceId
            )
        }

        fun estimateRssiSignal(rssi: Int): Int {
            return when {
                rssi >= -50 -> 98 // Immediate (< 1m)
                rssi >= -65 -> 85 // Near (1-3m)
                rssi >= -80 -> 65 // Medium (3-6m)
                else -> 40        // Far (> 6m)
            }
        }
    }

    private val bluetoothManager = try {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    } catch (_: Throwable) {
        null
    }

    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null

    private var advertiseCallback: AdvertiseCallback? = null
    private var scanCallback: ScanCallback? = null

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun isBleSupported(): Boolean {
        val adapter = bluetoothAdapter ?: return false
        return adapter.isEnabled && context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }

    fun isAdvertisingSupported(): Boolean {
        val adapter = bluetoothAdapter ?: return false
        return isBleSupported() && (adapter.isMultipleAdvertisementSupported || adapter.bluetoothLeAdvertiser != null)
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkAdvertisePermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE) &&
                   hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        }
        return true
    }

    private fun checkScanPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return hasPermission(Manifest.permission.BLUETOOTH_SCAN) &&
                   hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        }
        return hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
               hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    /**
     * SENDER / RECEIVER: Starts broadcasting Astron BLE beacon in background or foreground.
     */
    fun startBeacon(
        deviceName: String,
        flags: Int = FLAG_LAN_READY,
        port: Int = 52737,
        onStarted: () -> Unit = {},
        onFailed: (Int) -> Unit = {}
    ) {
        if (_isAdvertising.value) return
        if (!isAdvertisingSupported() || !checkAdvertisePermissions()) {
            Log.w(TAG, "BLE advertising not supported or permission missing")
            return
        }

        try {
            advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
            if (advertiser == null) return

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(false)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build()

            val payload = BleBeaconPayload(
                version = 1,
                flags = flags,
                port = port,
                deviceName = deviceName.take(12),
                deviceId = UUID.randomUUID().toString().take(8)
            )

            val advertiseData = AdvertiseData.Builder()
                .addServiceUuid(ASTRON_SERVICE_UUID)
                .addServiceData(ASTRON_SERVICE_UUID, serializePayload(payload))
                .setIncludeDeviceName(false)
                .build()

            advertiseCallback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    super.onStartSuccess(settingsInEffect)
                    _isAdvertising.value = true
                    Log.d(TAG, "Astron BLE beacon broadcast active for: $deviceName")
                    onStarted()
                }

                override fun onStartFailure(errorCode: Int) {
                    super.onStartFailure(errorCode)
                    _isAdvertising.value = false
                    Log.w(TAG, "Astron BLE beacon broadcast failed: code $errorCode")
                    onFailed(errorCode)
                }
            }

            advertiser?.startAdvertising(settings, advertiseData, advertiseCallback)
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException starting BLE advertising", e)
        } catch (t: Throwable) {
            Log.e(TAG, "Error starting BLE advertising", t)
        }
    }

    fun stopBeacon() {
        try {
            advertiseCallback?.let {
                advertiser?.stopAdvertising(it)
            }
        } catch (_: Throwable) {}
        advertiseCallback = null
        advertiser = null
        _isAdvertising.value = false
    }

    /**
     * SENDER / RECEIVER: Starts scanning for nearby Astron BLE beacons.
     */
    fun startScanning(onPeerDiscovered: (PeerDevice) -> Unit) {
        if (_isScanning.value) return
        if (!isBleSupported() || !checkScanPermissions()) {
            Log.w(TAG, "BLE scanning not supported or permission missing")
            return
        }

        try {
            scanner = bluetoothAdapter?.bluetoothLeScanner
            if (scanner == null) return

            val filter = ScanFilter.Builder()
                .setServiceUuid(ASTRON_SERVICE_UUID)
                .build()

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    result ?: return
                    val record = result.scanRecord ?: return
                    val rawData = record.getServiceData(ASTRON_SERVICE_UUID) ?: return
                    val address = result.device?.address ?: return
                    val payload = parsePayload(rawData, address) ?: return

                    val signalPercent = estimateRssiSignal(result.rssi)
                    val peer = PeerDevice(
                        id = "ble-$address",
                        name = payload.deviceName,
                        hostAddress = address,
                        port = payload.port,
                        transportType = TransportType.BLE_BEACON,
                        model = "BLE Beacon (${result.rssi} dBm)",
                        signalStrength = signalPercent,
                        lastSeen = System.currentTimeMillis()
                    )
                    onPeerDiscovered(peer)
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.w(TAG, "BLE scan failed with code: $errorCode")
                    _isScanning.value = false
                }
            }

            scanner?.startScan(listOf(filter), settings, scanCallback)
            _isScanning.value = true
            Log.d(TAG, "Astron BLE background beacon scan started")
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException starting BLE scan", e)
        } catch (t: Throwable) {
            Log.e(TAG, "Error starting BLE scan", t)
        }
    }

    fun stopScanning() {
        try {
            scanCallback?.let {
                scanner?.stopScan(it)
            }
        } catch (_: Throwable) {}
        scanCallback = null
        scanner = null
        _isScanning.value = false
    }
}
