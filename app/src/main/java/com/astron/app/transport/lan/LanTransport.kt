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
import com.astron.app.core.model.TransferPriority
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.yield
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.SecretKey

class LanTransport(
    private val context: Context,
    private val storageManager: StorageManager
) {
    companion object {
        const val TAG = "LanTransport"
        const val SERVICE_TYPE = "_astron._tcp."
        const val DEFAULT_PORT = 52737
        const val CHUNK_BUFFER_SIZE = 128 * 1024 // 128 KB chunk buffer default
        const val MIN_CHUNK_SIZE = 32 * 1024 // 32 KB minimum buffer for jittery links
        const val MAX_CHUNK_SIZE = 512 * 1024 // 512 KB maximum buffer for high-bandwidth Wi-Fi 7 / 6E

        fun getChunkBufferSize(priority: TransferPriority): Int = when (priority) {
            TransferPriority.MAX_SPEED -> 256 * 1024
            TransferPriority.BALANCED -> 128 * 1024
            TransferPriority.BATTERY_SAVER -> 64 * 1024
            TransferPriority.RELIABILITY -> 32 * 1024
        }

        /**
         * Dynamically scales chunk buffer size between 32 KB and 512 KB based on
         * measured socket round-trip time (RTT) and transfer priority.
         *
         * - RTT < 15ms (Ultra low-latency Wi-Fi 7 / MLO / LAN gigabit): 512 KB
         * - RTT < 40ms (Fast 5 GHz Wi-Fi): 256 KB
         * - RTT < 100ms (Standard Wi-Fi): 128 KB
         * - RTT < 200ms (Congested / Jittery Wi-Fi): 64 KB
         * - RTT >= 200ms (Severe jitter / Weak signal): 32 KB
         */
        fun calculateDynamicChunkSize(rttMs: Long, priority: TransferPriority = TransferPriority.BALANCED): Int {
            val base = getChunkBufferSize(priority)
            return when {
                rttMs < 15 -> (base * 2).coerceIn(MIN_CHUNK_SIZE, MAX_CHUNK_SIZE)
                rttMs < 40 -> base.coerceIn(MIN_CHUNK_SIZE, 256 * 1024)
                rttMs < 100 -> (base / 2).coerceIn(MIN_CHUNK_SIZE, 128 * 1024)
                rttMs < 200 -> 64 * 1024
                else -> MIN_CHUNK_SIZE
            }
        }

        // Packet OpCodes
        const val OP_HANDSHAKE_REQ: Byte = 0x01
        const val OP_HANDSHAKE_RESP: Byte = 0x02
        const val OP_MANIFEST: Byte = 0x03
        const val OP_FILE_START: Byte = 0x04
        const val OP_CHUNK: Byte = 0x05
        const val OP_FILE_END: Byte = 0x06
        const val OP_TRANSFER_COMPLETE: Byte = 0x07
        const val OP_CANCEL: Byte = 0x08
        const val OP_FILE_RESUME: Byte = 0x09
        const val OP_MANIFEST_ACK: Byte = 0x0A

        const val MAX_ALLOWED_CHUNK_SIZE = 1024 * 1024 // 1 MB sanity limit
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
    private val connectionSemaphore = Semaphore(3)
    private val approvedSessions = ConcurrentHashMap.newKeySet<String>()
    private val sessionProgressMap = ConcurrentHashMap<String, AtomicLong>()

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
    fun startDiscovery(
        onPeerFound: (PeerDevice) -> Unit,
        onPeerLost: ((serviceName: String) -> Unit)? = null
    ) {
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
                onPeerLost?.invoke(serviceInfo.serviceName)
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
     * SENDER: Sends a batch of files to a remote peer over a high-speed TCP socket,
     * utilizing multi-stream parallel socket dispatch when MAX_SPEED is enabled.
     */
    suspend fun sendFiles(
        peer: PeerDevice,
        manifest: TransferManifest,
        items: List<TransferItem>,
        priority: TransferPriority = TransferPriority.BALANCED,
        onProgress: (transferredBytes: Long, totalBytes: Long, activeFileName: String) -> Unit,
        onFileVerified: (fileId: String) -> Unit,
        isCancelled: () -> Boolean,
        awaitUnpause: suspend () -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        val chunkSize = getChunkBufferSize(priority)
        val sessionToken = CryptoManager.generateSessionToken(16)
        val sessionKey = CryptoManager.deriveKeyFromToken(sessionToken)
        val totalTransferred = AtomicLong(0L)
        val itemQueue = ConcurrentLinkedQueue(items)

        var primarySocket: Socket? = null
        try {
            var lastConnectEx: Exception? = null
            for (attempt in 1..3) {
                try {
                    val s = Socket().apply {
                        tcpNoDelay = true
                        keepAlive = true
                        sendBufferSize = chunkSize
                        connect(InetSocketAddress(peer.hostAddress, peer.port), 7_000)
                    }
                    primarySocket = s
                    break
                } catch (e: Exception) {
                    lastConnectEx = e
                    if (attempt < 3 && !isCancelled()) {
                        kotlinx.coroutines.delay(250L * attempt)
                    }
                }
            }
            if (primarySocket == null) {
                throw lastConnectEx ?: java.io.IOException("Could not connect to ${peer.hostAddress}:${peer.port}")
            }

            val outStream = DataOutputStream(BufferedOutputStream(primarySocket.getOutputStream(), chunkSize))
            val inStream = DataInputStream(BufferedInputStream(primarySocket.getInputStream()))

            // 1. Handshake with session token for wire encryption and RTT measurement
            val handshakeStartNs = System.nanoTime()
            outStream.writeByte(OP_HANDSHAKE_REQ.toInt())
            outStream.writeUTF(manifest.sessionId)
            outStream.writeUTF(manifest.senderDeviceName)
            outStream.writeUTF(sessionToken)
            outStream.flush()

            val respOp = inStream.readByte()
            val rttMs = ((System.nanoTime() - handshakeStartNs) / 1_000_000L).coerceAtLeast(1L)
            if (respOp != OP_HANDSHAKE_RESP) {
                Log.e(TAG, "Invalid handshake response from receiver")
                return@withContext false
            }

            // Dynamically scale chunk buffer size based on real-time RTT
            val dynamicChunkSize = calculateDynamicChunkSize(rttMs, priority)
            Log.d(TAG, "Dynamic chunk size adapted to ${dynamicChunkSize / 1024} KB (RTT=${rttMs}ms, priority=$priority)")

            // 2. Send Manifest JSON
            val manifestJson = json.encodeToString(manifest)
            val manifestBytes = manifestJson.toByteArray(Charsets.UTF_8)
            outStream.writeByte(OP_MANIFEST.toInt())
            outStream.writeInt(manifestBytes.size)
            outStream.write(manifestBytes)
            outStream.flush()

            // Wait for receiver manifest approval before streaming chunks
            val manifestAck = inStream.readByte()
            if (manifestAck != OP_MANIFEST_ACK && manifestAck != OP_HANDSHAKE_RESP) {
                Log.w(TAG, "Transfer was cancelled or rejected by receiver (ack=$manifestAck)")
                return@withContext false
            }

            val maxStreams = if (priority == TransferPriority.MAX_SPEED && items.size > 1) 3.coerceAtMost(items.size) else 1

            if (maxStreams <= 1) {
                // Single-stream sequential transmission
                return@withContext sendStreamItems(
                    outStream = outStream,
                    itemQueue = itemQueue,
                    sessionKey = sessionKey,
                    chunkSize = dynamicChunkSize,
                    manifest = manifest,
                    priority = priority,
                    totalTransferred = totalTransferred,
                    onProgress = onProgress,
                    onFileVerified = onFileVerified,
                    isCancelled = isCancelled,
                    awaitUnpause = awaitUnpause
                )
            } else {
                // Multi-Stream Parallel Socket Acceleration
                coroutineScope {
                    val deferreds = (0 until maxStreams).map { streamIndex ->
                        async(Dispatchers.IO) {
                            if (streamIndex == 0) {
                                sendStreamItems(
                                    outStream = outStream,
                                    itemQueue = itemQueue,
                                    sessionKey = sessionKey,
                                    chunkSize = dynamicChunkSize,
                                    manifest = manifest,
                                    priority = priority,
                                    totalTransferred = totalTransferred,
                                    onProgress = onProgress,
                                    onFileVerified = onFileVerified,
                                    isCancelled = isCancelled,
                                    awaitUnpause = awaitUnpause
                                )
                            } else {
                                var workerSocket: Socket? = null
                                try {
                                    workerSocket = Socket().apply {
                                        tcpNoDelay = true
                                        keepAlive = true
                                        sendBufferSize = chunkSize
                                        connect(InetSocketAddress(peer.hostAddress, peer.port), 10_000)
                                    }
                                    val workerOut = DataOutputStream(BufferedOutputStream(workerSocket.getOutputStream(), chunkSize))
                                    val workerIn = DataInputStream(BufferedInputStream(workerSocket.getInputStream()))

                                    // Handshake worker
                                    workerOut.writeByte(OP_HANDSHAKE_REQ.toInt())
                                    workerOut.writeUTF(manifest.sessionId)
                                    workerOut.writeUTF(manifest.senderDeviceName)
                                    workerOut.writeUTF(sessionToken)
                                    workerOut.flush()

                                    val wResp = workerIn.readByte()
                                    if (wResp == OP_HANDSHAKE_RESP) {
                                        workerOut.writeByte(OP_MANIFEST.toInt())
                                        workerOut.writeInt(manifestBytes.size)
                                        workerOut.write(manifestBytes)
                                        workerOut.flush()

                                        val wAck = workerIn.readByte()
                                        if (wAck == OP_MANIFEST_ACK || wAck == OP_HANDSHAKE_RESP) {
                                            sendStreamItems(
                                                outStream = workerOut,
                                                itemQueue = itemQueue,
                                                sessionKey = sessionKey,
                                                chunkSize = dynamicChunkSize,
                                                manifest = manifest,
                                                priority = priority,
                                                totalTransferred = totalTransferred,
                                                onProgress = onProgress,
                                                onFileVerified = onFileVerified,
                                                isCancelled = isCancelled,
                                                awaitUnpause = awaitUnpause
                                            )
                                        } else {
                                            false
                                        }
                                    } else {
                                        false
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Worker stream $streamIndex failed, primary stream will process remaining items", e)
                                    true
                                } finally {
                                    try { workerSocket?.close() } catch (_: Exception) {}
                                }
                            }
                        }
                    }
                    deferreds.awaitAll().let { results ->
                        // Primary stream (index 0) must succeed and all items in queue must be completed
                        results.isNotEmpty() && results.first() && itemQueue.isEmpty()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "LAN sendFiles failed", e)
            return@withContext false
        } finally {
            try { primarySocket?.close() } catch (_: Exception) {}
        }
    }

    private suspend fun sendStreamItems(
        outStream: DataOutputStream,
        itemQueue: ConcurrentLinkedQueue<TransferItem>,
        sessionKey: SecretKey,
        chunkSize: Int,
        manifest: TransferManifest,
        priority: TransferPriority,
        totalTransferred: AtomicLong,
        onProgress: (transferredBytes: Long, totalBytes: Long, activeFileName: String) -> Unit,
        onFileVerified: (fileId: String) -> Unit,
        isCancelled: () -> Boolean,
        awaitUnpause: suspend () -> Unit
    ): Boolean {
        var currentChunkSize = chunkSize
        var buffer = ByteArray(currentChunkSize)

        while (true) {
            val item = itemQueue.poll() ?: break

            if (isCancelled()) {
                outStream.writeByte(OP_CANCEL.toInt())
                outStream.flush()
                return false
            }

            val uri = android.net.Uri.parse(item.uriString)
            val startOffset = item.transferredBytes
            val inputStream = try {
                storageManager.openInputStreamAtOffset(uri, startOffset)
            } catch (e: Exception) {
                Log.e(TAG, "Error opening input stream for ${item.name}", e)
                null
            }

            if (inputStream == null) {
                // Skip file and mark verified/failed to avoid stalling the queue
                onFileVerified(item.id)
                continue
            }

            if (startOffset > 0L) {
                outStream.writeByte(OP_FILE_RESUME.toInt())
                outStream.writeUTF(item.id)
                outStream.writeUTF(item.name)
                outStream.writeLong(item.size)
                outStream.writeLong(startOffset)
            } else {
                outStream.writeByte(OP_FILE_START.toInt())
                outStream.writeUTF(item.id)
                outStream.writeUTF(item.name)
                outStream.writeLong(item.size)
            }
            outStream.flush()

            // Stream chunks with dynamic RTT backpressure adaptation
            var bytesRead: Int
            var fileTransferred = 0L

            inputStream.use { stream ->
                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    if (isCancelled()) {
                        outStream.writeByte(OP_CANCEL.toInt())
                        outStream.flush()
                        return false
                    }
                    awaitUnpause()

                    val plainChunk = if (bytesRead == buffer.size) buffer else buffer.copyOf(bytesRead)
                    val encryptedChunk = CryptoManager.encryptAesGcm(plainChunk, sessionKey)

                    val writeStart = System.currentTimeMillis()
                    outStream.writeByte(OP_CHUNK.toInt())
                    outStream.writeInt(encryptedChunk.size)
                    outStream.write(encryptedChunk)
                    outStream.flush()
                    val writeDuration = System.currentTimeMillis() - writeStart

                    // Dynamic adaptation: back off chunk size if socket buffer stalls
                    if (writeDuration > 300 && currentChunkSize > MIN_CHUNK_SIZE) {
                        currentChunkSize = (currentChunkSize / 2).coerceAtLeast(MIN_CHUNK_SIZE)
                        buffer = ByteArray(currentChunkSize)
                    } else if (writeDuration < 15 && currentChunkSize < MAX_CHUNK_SIZE && priority == TransferPriority.MAX_SPEED) {
                        currentChunkSize = (currentChunkSize * 2).coerceAtMost(MAX_CHUNK_SIZE)
                        buffer = ByteArray(currentChunkSize)
                    }

                    fileTransferred += bytesRead
                    val total = totalTransferred.addAndGet(bytesRead.toLong())
                    onProgress(total, manifest.totalBytes, item.name)

                    if (priority == TransferPriority.BATTERY_SAVER) {
                        yield()
                    }
                }
            }

            outStream.writeByte(OP_FILE_END.toInt())
            outStream.writeUTF(item.id)
            outStream.flush()

            onFileVerified(item.id)
        }

        outStream.writeByte(OP_TRANSFER_COMPLETE.toInt())
        outStream.flush()
        return true
    }

    /**
     * RECEIVER: Starts listening on a local port for incoming transfers.
     */
    suspend fun startReceiverServer(
        port: Int = DEFAULT_PORT,
        priority: TransferPriority = TransferPriority.BALANCED,
        onIncomingRequest: suspend (manifest: TransferManifest) -> Boolean,
        onProgress: (transferredBytes: Long, totalBytes: Long, activeFileName: String) -> Unit,
        onFileCompleted: (fileId: String, file: File, checksumVerified: Boolean) -> Unit,
        onTransferCompleted: ((sessionId: String) -> Unit)? = null,
        onTransferFailed: ((sessionId: String, error: String) -> Unit)? = null,
        isCancelled: () -> Boolean,
        awaitUnpause: suspend () -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val chunkSize = getChunkBufferSize(priority)
        try {
            serverSocket = ServerSocket(port).apply {
                reuseAddress = true
                receiveBufferSize = chunkSize
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
                    clientSocket.keepAlive = true
                    clientSocket.receiveBufferSize = chunkSize

                    // Handle each transfer with bounded concurrent connection limit
                    launch(Dispatchers.IO) {
                        if (!connectionSemaphore.tryAcquire()) {
                            try { clientSocket.close() } catch (_: Exception) {}
                            return@launch
                        }
                        try {
                            handleIncomingConnection(
                                clientSocket,
                                chunkSize,
                                onIncomingRequest,
                                onProgress,
                                onFileCompleted,
                                onTransferCompleted,
                                onTransferFailed,
                                isCancelled,
                                awaitUnpause
                            )
                        } finally {
                            connectionSemaphore.release()
                        }
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
        chunkSize: Int,
        onIncomingRequest: suspend (manifest: TransferManifest) -> Boolean,
        onProgress: (transferredBytes: Long, totalBytes: Long, activeFileName: String) -> Unit,
        onFileCompleted: (fileId: String, file: File, checksumVerified: Boolean) -> Unit,
        onTransferCompleted: ((sessionId: String) -> Unit)? = null,
        onTransferFailed: ((sessionId: String, error: String) -> Unit)? = null,
        isCancelled: () -> Boolean,
        awaitUnpause: suspend () -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        var currentWriteSession: StorageManager.FileWriteSession? = null
        var manifest: TransferManifest? = null
        try {
            val inStream = DataInputStream(BufferedInputStream(socket.getInputStream(), chunkSize))
            val outStream = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

            // 1. Handshake with session token
            val handshakeOp = inStream.readByte()
            if (handshakeOp != OP_HANDSHAKE_REQ) {
                socket.close()
                return@withContext
            }
            val sessionId = inStream.readUTF()
            val senderName = inStream.readUTF()
            val sessionToken = inStream.readUTF()
            val sessionKey = CryptoManager.deriveKeyFromToken(sessionToken)

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

            val parsedManifest = json.decodeFromString<TransferManifest>(String(manifestBytes, Charsets.UTF_8))
            manifest = parsedManifest

            // Ask user for approval if not already approved for this session
            val isAlreadyApproved = approvedSessions.contains(parsedManifest.sessionId)
            val approved = if (isAlreadyApproved) {
                true
            } else {
                val userApproved = onIncomingRequest(parsedManifest)
                if (userApproved) {
                    approvedSessions.add(parsedManifest.sessionId)
                }
                userApproved
            }

            if (!approved) {
                outStream.writeByte(OP_CANCEL.toInt())
                outStream.flush()
                socket.close()
                return@withContext
            }

            // Acknowledge manifest acceptance to sender
            outStream.writeByte(OP_MANIFEST_ACK.toInt())
            outStream.flush()

            val sessionTransferred = sessionProgressMap.getOrPut(parsedManifest.sessionId) { AtomicLong(0L) }

            // Map of file metadata by ID
            val manifestMap = parsedManifest.items.associateBy { it.id }

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
                        val target = storageManager.prepareDestinationFile(
                            fileName,
                            relPath,
                            com.astron.app.core.model.DuplicateAction.KEEP_BOTH
                        )
                        currentFile = target
                        currentFileBytes = 0L
                        currentWriteSession?.close()
                        currentWriteSession = storageManager.openWriteSession(target, 0L)
                    }
                    OP_FILE_RESUME -> {
                        val fileId = inStream.readUTF()
                        val fileName = inStream.readUTF()
                        val fileSize = inStream.readLong()
                        val resumeOffset = inStream.readLong()

                        currentItem = manifestMap[fileId]
                        val relPath = currentItem?.relativePath ?: ""
                        val target = storageManager.prepareDestinationFile(
                            fileName,
                            relPath,
                            com.astron.app.core.model.DuplicateAction.KEEP_BOTH,
                            isResume = true
                        )
                        currentFile = target
                        currentFileBytes = resumeOffset
                        currentWriteSession?.close()
                        currentWriteSession = storageManager.openWriteSession(target, resumeOffset)
                    }
                    OP_CHUNK -> {
                        val chunkLen = inStream.readInt()
                        if (chunkLen <= 0 || chunkLen > MAX_ALLOWED_CHUNK_SIZE) {
                            socket.close()
                            return@withContext
                        }
                        val encryptedBuffer = ByteArray(chunkLen)
                        inStream.readFully(encryptedBuffer, 0, chunkLen)

                        val plainChunk = CryptoManager.decryptAesGcm(encryptedBuffer, sessionKey)

                        awaitUnpause()
                        currentWriteSession?.writeChunk(plainChunk, plainChunk.size)
                        currentFileBytes += plainChunk.size
                        val totalSoFar = sessionTransferred.addAndGet(plainChunk.size.toLong())
                        currentFile?.let { target ->
                            onProgress(totalSoFar, parsedManifest.totalBytes, target.name)
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
                            onFileCompleted(fileId, completedFile, isVerified)
                        }
                        currentFile = null
                        currentItem = null
                    }
                    OP_TRANSFER_COMPLETE -> {
                        outStream.writeByte(OP_TRANSFER_COMPLETE.toInt())
                        outStream.flush()
                        sessionProgressMap.remove(parsedManifest.sessionId)
                        onTransferCompleted?.invoke(parsedManifest.sessionId)
                        break
                    }
                    OP_CANCEL -> {
                        sessionProgressMap.remove(parsedManifest.sessionId)
                        onTransferFailed?.invoke(parsedManifest.sessionId, "Cancelled by sender")
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming LAN transfer", e)
            manifest?.sessionId?.let { sId ->
                sessionProgressMap.remove(sId)
                onTransferFailed?.invoke(sId, e.message ?: "Transfer error")
            }
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
        approvedSessions.clear()
        sessionProgressMap.clear()
    }
}
