package com.astron.app.transport.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.astron.app.core.model.PeerDevice
import com.astron.app.core.model.TransportType
import com.astron.app.core.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Bluetooth Classic RFCOMM file transfer transport.
 *
 * Uses a stable, registered UUID for the Astron SPP service so that both
 * sides can reliably discover and connect to each other.
 *
 * Architecture:
 *  - Receiver: calls [startServer] — opens an RFCOMM server socket and waits.
 *  - Sender:   calls [connectToDevice] — connects to a paired device's server socket.
 *  - Both sides then call [sendStream] / [receiveStream] on the resulting BluetoothSocket.
 *
 * Security: All payload is encrypted with AES-256-GCM using a session key derived from
 * a token exchanged during the handshake phase (same CryptoManager used by LanTransport).
 *
 * Throughput: ~1-3 MB/s on modern Android devices. For files > 100 MB Wi-Fi is preferred;
 * Bluetooth is the last-resort fallback and handles small files very well.
 */
class BluetoothClassicTransport(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothClassicTransport"

        /** Registered UUID for Astron SPP profile — must match on sender and receiver. */
        val SERVICE_UUID: UUID = UUID.fromString("a2b3c4d5-e6f7-4801-8900-abcdef012345")
        const val SERVICE_NAME = "AstronFileTransfer"

        /** Transfer protocol version tag sent at start of every connection. */
        const val PROTOCOL_VERSION = 1

        /** Chunk size optimized for BT throughput ~2 KB per chunk keeps buffers low. */
        const val CHUNK_SIZE = 2 * 1024  // 2 KB chunks for BT
        const val DISCOVERY_TIMEOUT_MS = 12_000L
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices.asStateFlow()

    private val _connectionState = MutableStateFlow<BluetoothConnectionState>(BluetoothConnectionState.Idle)
    val connectionState: StateFlow<BluetoothConnectionState> = _connectionState.asStateFlow()

    private var serverSocket: BluetoothServerSocket? = null
    private var activeSocket: BluetoothSocket? = null
    private var discoveryReceiver: BroadcastReceiver? = null

    sealed class BluetoothConnectionState {
        data object Idle : BluetoothConnectionState()
        data object Listening : BluetoothConnectionState()
        data class Discovering(val found: Int) : BluetoothConnectionState()
        data class Connected(val deviceName: String, val deviceAddress: String) : BluetoothConnectionState()
        data class Error(val message: String) : BluetoothConnectionState()
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Availability check
    // ─────────────────────────────────────────────────────────────────────────────

    fun isAvailable(): Boolean {
        val adapter = bluetoothAdapter ?: return false
        if (!adapter.isEnabled) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_CONNECT) &&
                    hasPermission(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            hasPermission(Manifest.permission.BLUETOOTH) &&
                    hasPermission(Manifest.permission.BLUETOOTH_ADMIN)
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    // ─────────────────────────────────────────────────────────────────────────────
    // Discovery — scan for nearby Bluetooth devices
    // ─────────────────────────────────────────────────────────────────────────────

    fun startDiscovery(onDeviceFound: (BluetoothDevice) -> Unit) {
        if (!isAvailable()) {
            Log.w(TAG, "Bluetooth not available/permitted for discovery")
            return
        }
        val adapter = bluetoothAdapter ?: return
        val foundDevices = mutableSetOf<String>()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        } ?: return
                        if (foundDevices.add(device.address)) {
                            val devName = try { device.name ?: "Device" } catch (_: SecurityException) { "Device" }
                            Log.d(TAG, "BT device found: $devName (${device.address})")
                            _discoveredDevices.value = _discoveredDevices.value + device
                            _connectionState.value = BluetoothConnectionState.Discovering(foundDevices.size)
                            onDeviceFound(device)
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        Log.d(TAG, "BT discovery finished. Found: ${foundDevices.size}")
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        try {
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            discoveryReceiver = receiver
            adapter.startDiscovery()
            _discoveredDevices.value = emptyList()
            _connectionState.value = BluetoothConnectionState.Discovering(0)
            Log.d(TAG, "BT discovery started")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start BT discovery", e)
        }
    }

    fun stopDiscovery() {
        try {
            bluetoothAdapter?.cancelDiscovery()
            discoveryReceiver?.let { context.unregisterReceiver(it) }
            discoveryReceiver = null
        } catch (_: Throwable) {}
    }

    /** Returns the set of already-paired (bonded) devices. */
    fun getPairedDevices(): Set<BluetoothDevice> {
        if (!isAvailable()) return emptySet()
        return try {
            bluetoothAdapter?.bondedDevices ?: emptySet()
        } catch (_: Throwable) {
            emptySet()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Server side (Receiver) — listen for incoming connection
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Opens an RFCOMM server socket and blocks until a client connects or [timeoutMs] expires.
     * Call from a background coroutine.
     *
     * @return [BluetoothSocket] on success or null on failure/timeout.
     */
    suspend fun startServer(timeoutMs: Long = 60_000L): BluetoothSocket? = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            Log.w(TAG, "BT not available for server")
            return@withContext null
        }
        val adapter = bluetoothAdapter ?: return@withContext null

        try {
            @Suppress("MissingPermission")
            val server = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
            serverSocket = server
            _connectionState.value = BluetoothConnectionState.Listening
            Log.d(TAG, "BT RFCOMM server listening...")

            val socket = withContext(Dispatchers.IO) {
                server.accept(timeoutMs.toInt())
            }
            serverSocket?.close()
            serverSocket = null

            if (socket != null) {
                activeSocket = socket
                @Suppress("MissingPermission")
                val deviceName = socket.remoteDevice?.name ?: "Unknown"
                _connectionState.value = BluetoothConnectionState.Connected(
                    deviceName = deviceName,
                    deviceAddress = socket.remoteDevice?.address ?: ""
                )
                Log.d(TAG, "BT client connected: $deviceName")
            }
            socket
        } catch (e: IOException) {
            Log.e(TAG, "BT server accept failed", e)
            _connectionState.value = BluetoothConnectionState.Error("BT server error: ${e.message}")
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Client side (Sender) — connect to a receiver device
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Retrieves a remote [BluetoothDevice] by its MAC address.
     */
    fun getRemoteDevice(address: String): BluetoothDevice? {
        return try {
            bluetoothAdapter?.getRemoteDevice(address)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Connects to [device] via RFCOMM using the Astron service UUID.
     * If the device is not yet bonded, pairing must be initiated separately.
     * Call from a background coroutine.
     *
     * @return [BluetoothSocket] on success or null on failure.
     */
    suspend fun connectToDevice(device: BluetoothDevice): BluetoothSocket? = withContext(Dispatchers.IO) {
        if (!isAvailable()) return@withContext null
        try {
            stopDiscovery() // Cancel discovery before connecting — important!
            @Suppress("MissingPermission")
            val socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
            activeSocket = socket
            @Suppress("MissingPermission")
            Log.d(TAG, "Connecting to ${device.name} (${device.address})...")
            socket.connect()
            @Suppress("MissingPermission")
            _connectionState.value = BluetoothConnectionState.Connected(
                deviceName = device.name ?: "Unknown",
                deviceAddress = device.address
            )
            @Suppress("MissingPermission")
            Log.d(TAG, "BT connected to ${device.name}")
            socket
        } catch (e: IOException) {
            Log.e(TAG, "BT connect failed", e)
            _connectionState.value = BluetoothConnectionState.Error("BT connect error: ${e.message}")
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Data transfer — works the same on both sides once connected
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Sends raw [data] bytes over [socket]. Prepends a length header and metadata.
     * All bytes are encrypted with AES-256-GCM using [sessionToken].
     */
    suspend fun sendStream(
        socket: BluetoothSocket,
        data: InputStream,
        totalBytes: Long,
        sessionToken: String,
        fileName: String = "",
        onProgress: (Long) -> Unit = {},
        isActive: () -> Boolean = { true }
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val key = CryptoManager.deriveKeyFromToken(sessionToken)
            val out = DataOutputStream(socket.outputStream.buffered(8 * 1024))

            // Header: protocol version + session token + file name + total size
            out.writeInt(PROTOCOL_VERSION)
            out.writeUTF(sessionToken)
            out.writeUTF(fileName)
            out.writeLong(totalBytes)
            out.flush()

            var sentBytes = 0L
            val buffer = ByteArray(CHUNK_SIZE)
            var bytesRead: Int

            while (data.read(buffer).also { bytesRead = it } != -1 && isActive()) {
                val chunk = buffer.copyOf(bytesRead)
                val encrypted = CryptoManager.encryptAesGcm(chunk, key)
                out.writeInt(encrypted.size)
                out.write(encrypted)
                sentBytes += bytesRead
                onProgress(sentBytes)
            }

            // Sentinel: chunk size of -1 signals end of stream
            out.writeInt(-1)
            out.flush()
            Log.d(TAG, "BT send complete: $sentBytes bytes for $fileName")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "BT send error", e)
            false
        }
    }

    data class BluetoothReceiveResult(
        val file: File,
        val fileName: String,
        val bytesReceived: Long
    )

    /**
     * Receives file from [socket] and writes it directly to storage, deriving key from transmitted token.
     */
    suspend fun receiveStreamToFile(
        socket: BluetoothSocket,
        storageManager: com.astron.app.core.storage.StorageManager,
        sessionTokenFallback: String = "astron-bt",
        onFileHeaderReceived: ((fileName: String, totalBytes: Long) -> Unit)? = null,
        onProgress: (Long) -> Unit = {},
        isActive: () -> Boolean = { true }
    ): BluetoothReceiveResult? = withContext(Dispatchers.IO) {
        try {
            val input = DataInputStream(socket.inputStream.buffered(8 * 1024))

            val version = input.readInt()
            val token = try { input.readUTF() } catch (_: Throwable) { sessionTokenFallback }
            val fileName = try { input.readUTF() } catch (_: Throwable) { "" }
            val totalBytes = input.readLong()
            Log.d(TAG, "BT receive: protocol v$version, file=$fileName, size=$totalBytes")

            val safeName = if (fileName.isNotBlank()) CryptoManager.sanitizeFilename(fileName)
                else "bt_incoming_${System.currentTimeMillis()}.bin"
            val destFile = storageManager.createDestinationFile(safeName)
            onFileHeaderReceived?.invoke(destFile.name, totalBytes)

            val key = CryptoManager.deriveKeyFromToken(token.ifBlank { sessionTokenFallback })
            var receivedBytes = 0L

            destFile.outputStream().buffered(8 * 1024).use { output ->
                while (isActive()) {
                    val chunkSize = input.readInt()
                    if (chunkSize == -1) break // End of stream sentinel
                    if (chunkSize <= 0 || chunkSize > 64 * 1024) {
                        Log.e(TAG, "Invalid BT chunk size: $chunkSize — aborting")
                        return@withContext null
                    }
                    val encryptedChunk = ByteArray(chunkSize)
                    input.readFully(encryptedChunk)
                    val decrypted = CryptoManager.decryptAesGcm(encryptedChunk, key)
                    output.write(decrypted)
                    receivedBytes += decrypted.size
                    onProgress(receivedBytes)
                }
                output.flush()
            }
            Log.d(TAG, "BT receive complete: $receivedBytes bytes to ${destFile.name}")
            BluetoothReceiveResult(destFile, destFile.name, receivedBytes)
        } catch (e: Throwable) {
            Log.e(TAG, "BT receive error", e)
            null
        }
    }

    /**
     * Receives data from [socket] and writes it to [output].
     * Decrypts each chunk using AES-256-GCM with the session key.
     *
     * @return Total bytes received or -1 on error.
     */
    suspend fun receiveStream(
        socket: BluetoothSocket,
        output: OutputStream,
        sessionToken: String,
        onProgress: (Long) -> Unit = {},
        isActive: () -> Boolean = { true }
    ): Long = withContext(Dispatchers.IO) {
        try {
            val input = DataInputStream(socket.inputStream.buffered(8 * 1024))

            val version = input.readInt()
            val token = try { input.readUTF() } catch (_: Throwable) { sessionToken }
            val fileName = try { input.readUTF() } catch (_: Throwable) { "" }
            val totalBytes = input.readLong()
            Log.d(TAG, "BT receive: protocol v$version, expecting $totalBytes bytes for $fileName")

            val key = CryptoManager.deriveKeyFromToken(token.ifBlank { sessionToken })
            var receivedBytes = 0L

            while (isActive()) {
                val chunkSize = input.readInt()
                if (chunkSize == -1) break // End of stream sentinel
                if (chunkSize <= 0 || chunkSize > 64 * 1024) {
                    Log.e(TAG, "Invalid BT chunk size: $chunkSize — aborting")
                    return@withContext -1L
                }
                val encryptedChunk = ByteArray(chunkSize)
                input.readFully(encryptedChunk)
                val decrypted = CryptoManager.decryptAesGcm(encryptedChunk, key)
                output.write(decrypted)
                receivedBytes += decrypted.size
                onProgress(receivedBytes)
            }

            output.flush()
            Log.d(TAG, "BT receive complete: $receivedBytes bytes")
            receivedBytes
        } catch (e: Throwable) {
            Log.e(TAG, "BT receive error", e)
            -1L
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Peer model helpers
    // ─────────────────────────────────────────────────────────────────────────────

    /** Converts a [BluetoothDevice] to an Astron [PeerDevice] model. */
    @Suppress("MissingPermission")
    fun BluetoothDevice.toPeerDevice(): PeerDevice = PeerDevice(
        id = address,
        name = name ?: address,
        hostAddress = address,
        port = 0,
        transportType = TransportType.BLUETOOTH,
        signalStrength = 60
    )

    // ─────────────────────────────────────────────────────────────────────────────
    // Teardown
    // ─────────────────────────────────────────────────────────────────────────────

    fun disconnect() {
        try {
            activeSocket?.close()
            activeSocket = null
        } catch (_: Throwable) {}
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (_: Throwable) {}
        stopDiscovery()
        _connectionState.value = BluetoothConnectionState.Idle
    }
}
