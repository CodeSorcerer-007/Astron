package com.astron.app.transport.lan

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.astron.app.core.model.ManifestItem
import com.astron.app.core.model.PeerDevice
import com.astron.app.core.model.TransferItem
import com.astron.app.core.model.TransferManifest
import com.astron.app.core.model.TransportType
import com.astron.app.core.security.CryptoManager
import com.astron.app.core.storage.StorageManager
import com.astron.app.core.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.UUID

class LanTransport(
    private val context: Context,
    private val storageManager: StorageManager
) {
    companion object {
        const val TAG = "LanTransport"
        const val SERVICE_TYPE = "_astron._tcp."
        const val DEFAULT_PORT = 52737
        const val CHUNK_BUFFER_SIZE = 128 * 1024 // 128 KB chunk buffer

        // Packet OpCodes
        const val OP_HANDSHAKE_REQ: Byte = 0x01
        const val OP_HANDSHAKE_RESP: Byte = 0x02
        const val OP_MANIFEST: Byte = 0x03
        const val OP_FILE_START: Byte = 0x04
        const val OP_CHUNK: Byte = 0x05
        const val OP_FILE_END: Byte = 0x06
        const val OP_TRANSFER_COMPLETE: Byte = 0x07
        const val OP_CANCEL: Byte = 0x08
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val nsdManager = try {
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    } catch (_: Throwable) {
        null
    }

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var serverSocket: ServerSocket? = null
    private var isAdvertising = false
    private var isDiscovering = false

    /**
     * Starts advertising this device on the local network via NSD (mDNS).
     */
    fun startAdvertising(deviceName: String, port: Int = DEFAULT_PORT) {
        if (isAdvertising) return
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "Astron-$deviceName-${UUID.randomUUID().toString().take(6)}"
            serviceType = SERVICE_TYPE
            setPort(port)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                isAdvertising = true
                Log.d(TAG, "NSD Service registered: ${serviceInfo.serviceName}")
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "NSD Registration failed: $errorCode")
                isAdvertising = false
            }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                isAdvertising = false
            }
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "NSD Unregistration failed: $errorCode")
            }
        }

        try {
            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering NSD service", e)
        }
    }

    fun stopAdvertising() {
        try {
            registrationListener?.let { nsdManager?.unregisterService(it) }
        } catch (_: Throwable) {}
        registrationListener = null
        isAdvertising = false
    }

    /**
     * Starts discovering other Astron peers on the local Wi-Fi.
     */
    fun startDiscovery(onPeerFound: (PeerDevice) -> Unit) {
        if (isDiscovering) return

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                isDiscovering = true
                Log.d(TAG, "NSD Discovery started")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType.contains("astron")) {
                    resolveService(serviceInfo, onPeerFound)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "NSD Service lost: ${serviceInfo.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                isDiscovering = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                isDiscovering = false
                Log.e(TAG, "NSD Start discovery failed: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "NSD Stop discovery failed: $errorCode")
            }
        }

        try {
            nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting NSD discovery", e)
        }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo, onPeerFound: (PeerDevice) -> Unit) {
        val resolver = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "NSD Resolve failed for ${serviceInfo.serviceName}: $errorCode")
            }

            override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                val host = resolvedInfo.host?.hostAddress ?: return
                val port = resolvedInfo.port
                val localIp = NetworkUtils.getLocalIpAddress()
                if (host == localIp && port == DEFAULT_PORT) {
                    // Ignore self
                    return
                }

                val displayName = resolvedInfo.serviceName
                    .removePrefix("Astron-")
                    .substringBeforeLast('-')
                    .ifBlank { "Nearby Device" }

                val peer = PeerDevice(
                    id = "$host:$port",
                    name = displayName,
                    hostAddress = host,
                    port = port,
                    transportType = TransportType.LOCAL_WIFI,
                    signalStrength = 95
                )
                onPeerFound(peer)
            }
        }

        try {
            nsdManager?.resolveService(serviceInfo, resolver)
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating NSD resolve", e)
        }
    }

    fun stopDiscovery() {
        try {
            discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
        } catch (_: Throwable) {}
        discoveryListener = null
        isDiscovering = false
    }

    /**
     * SENDER: Sends a batch of files to a remote peer over a high-speed TCP socket.
     */
    suspend fun sendFiles(
        peer: PeerDevice,
        manifest: TransferManifest,
        items: List<TransferItem>,
        onProgress: (transferredBytes: Long, totalBytes: Long, activeFileName: String) -> Unit,
        onFileVerified: (fileId: String) -> Unit,
        isCancelled: () -> Boolean,
        awaitUnpause: suspend () -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        try {
            socket = Socket()
            socket.tcpNoDelay = true
            socket.sendBufferSize = CHUNK_BUFFER_SIZE
            socket.connect(InetSocketAddress(peer.hostAddress, peer.port), 10_000)

            val outStream = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), CHUNK_BUFFER_SIZE))
            val inStream = DataInputStream(BufferedInputStream(socket.getInputStream()))

            // 1. Handshake
            outStream.writeByte(OP_HANDSHAKE_REQ.toInt())
            outStream.writeUTF(manifest.sessionId)
            outStream.writeUTF(manifest.senderDeviceName)
            outStream.flush()

            val respOp = inStream.readByte()
            if (respOp != OP_HANDSHAKE_RESP) {
                Log.e(TAG, "Invalid handshake response from receiver")
                return@withContext false
            }

            // 2. Send Manifest JSON
            val manifestJson = json.encodeToString(manifest)
            val manifestBytes = manifestJson.toByteArray(Charsets.UTF_8)
            outStream.writeByte(OP_MANIFEST.toInt())
            outStream.writeInt(manifestBytes.size)
            outStream.write(manifestBytes)
            outStream.flush()

            var totalTransferred = 0L
            val buffer = ByteArray(CHUNK_BUFFER_SIZE)

            // 3. Send Each File with bounded streaming & offset resume
            for (item in items) {
                if (isCancelled()) {
                    outStream.writeByte(OP_CANCEL.toInt())
                    outStream.flush()
                    return@withContext false
                }

                val uri = android.net.Uri.parse(item.uriString)
                val inputStream = storageManager.openInputStreamAtOffset(uri, 0L)

                outStream.writeByte(OP_FILE_START.toInt())
                outStream.writeUTF(item.id)
                outStream.writeUTF(item.name)
                outStream.writeLong(item.size)
                outStream.flush()

                // Stream chunks
                var bytesRead: Int
                var fileTransferred = 0L

                inputStream.use { stream ->
                    while (stream.read(buffer).also { bytesRead = it } != -1) {
                        if (isCancelled()) {
                            outStream.writeByte(OP_CANCEL.toInt())
                            outStream.flush()
                            return@withContext false
                        }
                        awaitUnpause()

                        outStream.writeByte(OP_CHUNK.toInt())
                        outStream.writeInt(bytesRead)
                        outStream.write(buffer, 0, bytesRead)
                        outStream.flush()

                        fileTransferred += bytesRead
                        totalTransferred += bytesRead
                        onProgress(totalTransferred, manifest.totalBytes, item.name)
                    }
                }

                outStream.writeByte(OP_FILE_END.toInt())
                outStream.writeUTF(item.id)
                outStream.flush()

                onFileVerified(item.id)
            }

            outStream.writeByte(OP_TRANSFER_COMPLETE.toInt())
            outStream.flush()
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "LAN sendFiles failed", e)
            return@withContext false
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    /**
     * RECEIVER: Starts listening on a local port for incoming transfers.
     */
    suspend fun startReceiverServer(
        port: Int = DEFAULT_PORT,
        onIncomingRequest: suspend (manifest: TransferManifest) -> Boolean,
        onProgress: (transferredBytes: Long, totalBytes: Long, activeFileName: String) -> Unit,
        onFileCompleted: (file: File, checksumVerified: Boolean) -> Unit,
        isCancelled: () -> Boolean,
        awaitUnpause: suspend () -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        try {
            serverSocket = ServerSocket(port).apply {
                reuseAddress = true
                receiveBufferSize = CHUNK_BUFFER_SIZE
            }
            Log.d(TAG, "Receiver server listening on port $port")

            coroutineScope {
                while (isActive && !isCancelled()) {
                    val clientSocket = try {
                        serverSocket?.accept() ?: break
                    } catch (_: Exception) {
                        break
                    }

                    clientSocket.tcpNoDelay = true
                    clientSocket.receiveBufferSize = CHUNK_BUFFER_SIZE

                    // Handle each transfer concurrently in its own coroutine
                    launch(Dispatchers.IO) {
                        handleIncomingConnection(
                            clientSocket,
                            onIncomingRequest,
                            onProgress,
                            onFileCompleted,
                            isCancelled,
                            awaitUnpause
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Receiver server exception", e)
        } finally {
            stopReceiverServer()
        }
    }

    private suspend fun handleIncomingConnection(
        socket: Socket,
        onIncomingRequest: suspend (manifest: TransferManifest) -> Boolean,
        onProgress: (transferredBytes: Long, totalBytes: Long, activeFileName: String) -> Unit,
        onFileCompleted: (file: File, checksumVerified: Boolean) -> Unit,
        isCancelled: () -> Boolean,
        awaitUnpause: suspend () -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        var currentWriteSession: StorageManager.FileWriteSession? = null
        try {
            val inStream = DataInputStream(BufferedInputStream(socket.getInputStream(), CHUNK_BUFFER_SIZE))
            val outStream = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

            // 1. Handshake
            val handshakeOp = inStream.readByte()
            if (handshakeOp != OP_HANDSHAKE_REQ) {
                socket.close()
                return@withContext
            }
            val sessionId = inStream.readUTF()
            val senderName = inStream.readUTF()

            outStream.writeByte(OP_HANDSHAKE_RESP.toInt())
            outStream.flush()

            // 2. Read Manifest
            val manifestOp = inStream.readByte()
            if (manifestOp != OP_MANIFEST) {
                socket.close()
                return@withContext
            }
            val manifestLen = inStream.readInt()
            if (manifestLen <= 0 || manifestLen > 10 * 1024 * 1024) {
                socket.close()
                return@withContext
            }
            val manifestBytes = ByteArray(manifestLen)
            inStream.readFully(manifestBytes)

            val manifest = json.decodeFromString<TransferManifest>(String(manifestBytes, Charsets.UTF_8))

            // Ask user for approval
            val approved = onIncomingRequest(manifest)
            if (!approved) {
                outStream.writeByte(OP_CANCEL.toInt())
                outStream.flush()
                socket.close()
                return@withContext
            }

            var totalReceived = 0L
            val buffer = ByteArray(CHUNK_BUFFER_SIZE)

            // Map of file metadata by ID
            val manifestMap = manifest.items.associateBy { it.id }

            // 3. Receive Files
            var currentFile: File? = null
            var currentItem: ManifestItem? = null
            var currentFileBytes = 0L

            while (isActive && !isCancelled()) {
                val op = inStream.readByte()
                when (op) {
                    OP_FILE_START -> {
                        val fileId = inStream.readUTF()
                        val fileName = inStream.readUTF()
                        val fileSize = inStream.readLong()

                        currentItem = manifestMap[fileId]
                        val relPath = currentItem?.relativePath ?: ""
                        currentFile = storageManager.prepareDestinationFile(
                            fileName,
                            relPath,
                            com.astron.app.core.model.DuplicateAction.KEEP_BOTH
                        )
                        currentFileBytes = 0L
                        currentWriteSession?.close()
                        currentFile?.let { target ->
                            currentWriteSession = storageManager.openWriteSession(target, 0L)
                        }
                    }
                    OP_CHUNK -> {
                        val chunkLen = inStream.readInt()
                        if (chunkLen <= 0 || chunkLen > CHUNK_BUFFER_SIZE * 2) {
                            socket.close()
                            return@withContext
                        }
                        inStream.readFully(buffer, 0, chunkLen)

                        awaitUnpause()
                        currentWriteSession?.writeChunk(buffer, chunkLen)
                        currentFileBytes += chunkLen
                        totalReceived += chunkLen
                        currentFile?.let { target ->
                            onProgress(totalReceived, manifest.totalBytes, target.name)
                        }
                    }
                    OP_FILE_END -> {
                        val fileId = inStream.readUTF()
                        currentWriteSession?.close()
                        currentWriteSession = null

                        currentFile?.let { completedFile ->
                            // Checksum verification
                            val expectedSha = currentItem?.checksumSha256
                            val isVerified = if (!expectedSha.isNullOrBlank()) {
                                val actualSha = CryptoManager.computeSha256(completedFile.inputStream())
                                actualSha.equals(expectedSha, ignoreCase = true)
                            } else {
                                true
                            }
                            onFileCompleted(completedFile, isVerified)
                        }
                        currentFile = null
                        currentItem = null
                    }
                    OP_TRANSFER_COMPLETE -> {
                        break
                    }
                    OP_CANCEL -> {
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming LAN transfer", e)
        } finally {
            try { currentWriteSession?.close() } catch (_: Exception) {}
            try { socket.close() } catch (_: Exception) {}
        }
    }

    fun stopReceiverServer() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }
}
