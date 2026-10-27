package com.astron.app.transport.multicast

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.astron.app.core.model.ManifestItem
import com.astron.app.core.model.TransferItem
import com.astron.app.core.model.TransferManifest
import com.astron.app.core.storage.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.CRC32
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

data class MulticastGroupStatus(
    val isRunning: Boolean = false,
    val isSender: Boolean = false,
    val connectedReceivers: Int = 0,
    val totalTransferredBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val currentFileName: String = ""
)

@Singleton
class MulticastGroupManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val storageManager: StorageManager
) {
    companion object {
        const val TAG = "MulticastGroupManager"
        const val MULTICAST_IP = "239.255.42.99"
        const val MULTICAST_PORT = 52738
        const val MAGIC = 0x4153544D // "ASTM" in ASCII
        const val CHUNK_DATA_SIZE = 1400 // Fits within standard 1500 MTU without IP fragmentation
        const val FEC_BLOCK_SIZE = 4 // 1 parity chunk per 4 data chunks

        // OpCodes
        const val OP_ANNOUNCE: Byte = 0x01
        const val OP_CHUNK: Byte = 0x02
        const val OP_PARITY: Byte = 0x03
        const val OP_ACK_REQ: Byte = 0x04
        const val OP_NACK: Byte = 0x05
        const val OP_COMPLETE: Byte = 0x06

        fun computeCrc32(data: ByteArray, offset: Int = 0, length: Int = data.size): Long {
            val crc = CRC32()
            crc.update(data, offset, length)
            return crc.value
        }

        fun xorParity(chunks: List<ByteArray>): ByteArray {
            if (chunks.isEmpty()) return ByteArray(0)
            val maxLen = chunks.maxOf { it.size }
            val parity = ByteArray(maxLen)
            for (chunk in chunks) {
                for (i in chunk.indices) {
                    parity[i] = (parity[i].toInt() xor chunk[i].toInt()).toByte()
                }
            }
            return parity
        }

        fun recoverMissingChunk(availableChunks: List<ByteArray>, parityChunk: ByteArray): ByteArray {
            val maxLen = (availableChunks + listOf(parityChunk)).maxOf { it.size }
            val recovered = parityChunk.copyOf(maxLen)
            for (chunk in availableChunks) {
                for (i in chunk.indices) {
                    recovered[i] = (recovered[i].toInt() xor chunk[i].toInt()).toByte()
                }
            }
            return recovered
        }

        fun sendNack(socket: MulticastSocket, groupAddress: InetAddress, fileId: String, chunkIndex: Int) {
            val fBytes = fileId.toByteArray(Charsets.UTF_8)
            val buf = ByteBuffer.allocate(4 + 1 + 2 + fBytes.size + 4)
            buf.putInt(MAGIC)
            buf.put(OP_NACK)
            buf.putShort(fBytes.size.toShort())
            buf.put(fBytes)
            buf.putInt(chunkIndex)
            try {
                val packet = DatagramPacket(buf.array(), buf.array().size, groupAddress, MULTICAST_PORT)
                socket.send(packet)
            } catch (_: Exception) {}
        }
    }

    private val json = Json { ignoreUnknownKeys = true }
    private var multicastLock: WifiManager.MulticastLock? = null
    private var activeSocket: MulticastSocket? = null
    private var isCancelledFlag = false

    private val _status = MutableStateFlow(MulticastGroupStatus())
    val status: StateFlow<MulticastGroupStatus> = _status.asStateFlow()

    private fun acquireMulticastLock() {
        try {
            if (multicastLock == null) {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                multicastLock = wifiManager?.createMulticastLock("astron_multicast_lock")?.apply {
                    setReferenceCounted(true)
                    acquire()
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Could not acquire MulticastLock: ${t.message}")
        }
    }

    private fun releaseMulticastLock() {
        try {
            multicastLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (_: Throwable) {}
        multicastLock = null
    }

    /**
     * SENDER: Broadcasts a file batch to multiple receivers simultaneously over UDP Multicast.
     * Includes FEC (Forward Error Correction) parity chunks so packet loss is healed in-stream.
     */
    suspend fun startGroupBroadcast(
        manifest: TransferManifest,
        items: List<TransferItem>,
        onProgress: (transferred: Long, total: Long, activeFileName: String) -> Unit,
        onComplete: (Boolean) -> Unit
    ) = withContext(Dispatchers.IO) {
        acquireMulticastLock()
        isCancelledFlag = false
        val groupAddress = InetAddress.getByName(MULTICAST_IP)
        var socket: MulticastSocket? = null

        try {
            val wifiIf = com.astron.app.core.util.NetworkUtils.getActiveWifiNetworkInterface()
            socket = MulticastSocket().apply {
                timeToLive = 2
                sendBufferSize = 256 * 1024
                if (wifiIf != null) {
                    try { networkInterface = wifiIf } catch (_: Throwable) {}
                }
            }
            activeSocket = socket
            _status.value = MulticastGroupStatus(
                isRunning = true,
                isSender = true,
                totalBytes = manifest.totalBytes
            )

            // 1. Announce Session (repeat 3 times to ensure all listening devices receive manifest)
            val manifestBytes = json.encodeToString(manifest).toByteArray(Charsets.UTF_8)
            val announcePacketBuffer = ByteBuffer.allocate(9 + manifestBytes.size)
            announcePacketBuffer.putInt(MAGIC)
            announcePacketBuffer.put(OP_ANNOUNCE)
            announcePacketBuffer.putInt(manifestBytes.size)
            announcePacketBuffer.put(manifestBytes)

            val announcePacket = DatagramPacket(
                announcePacketBuffer.array(),
                announcePacketBuffer.array().size,
                groupAddress,
                MULTICAST_PORT
            )

            repeat(3) {
                socket.send(announcePacket)
                Thread.sleep(100)
            }

            var totalBytesSent = 0L
            val sentChunksCache = ConcurrentHashMap<String, ByteArray>()

            fun handleIncomingNacks(timeoutMs: Int) {
                val nackBuf = ByteArray(512)
                val nackPacket = DatagramPacket(nackBuf, nackBuf.size)
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline && !isCancelledFlag) {
                    try {
                        socket.soTimeout = maxOf(1, (deadline - System.currentTimeMillis()).toInt())
                        socket.receive(nackPacket)
                        val bb = ByteBuffer.wrap(nackPacket.data, 0, nackPacket.length)
                        if (bb.remaining() >= 5 && bb.int == MAGIC && bb.get() == OP_NACK) {
                            val nLen = bb.short.toInt() and 0xFFFF
                            if (bb.remaining() >= nLen + 4) {
                                val nBytes = ByteArray(nLen)
                                bb.get(nBytes)
                                val nackFileId = String(nBytes, Charsets.UTF_8)
                                val nackChunkIdx = bb.int
                                val cached = sentChunksCache["$nackFileId:$nackChunkIdx"]
                                if (cached != null) {
                                    val chunkCrc = computeCrc32(cached)
                                    val nameBytes = nackFileId.toByteArray(Charsets.UTF_8)
                                    val pb = ByteBuffer.allocate(4 + 1 + 2 + nameBytes.size + 4 + 4 + 8 + cached.size)
                                    pb.putInt(MAGIC)
                                    pb.put(OP_CHUNK)
                                    pb.putShort(nameBytes.size.toShort())
                                    pb.put(nameBytes)
                                    pb.putInt(nackChunkIdx)
                                    pb.putInt(cached.size)
                                    pb.putLong(chunkCrc)
                                    pb.put(cached)
                                    val retx = DatagramPacket(pb.array(), pb.array().size, groupAddress, MULTICAST_PORT)
                                    socket.send(retx)
                                }
                            }
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                        break
                    } catch (_: Exception) {}
                }
            }

            // 2. Stream File Chunks with Forward Error Correction (FEC)
            for (item in items) {
                if (isCancelledFlag) break
                val uri = item.uri
                val inStream = try {
                    if (uri != null) context.contentResolver.openInputStream(uri)
                    else if (item.realFilePath != null) File(item.realFilePath!!).inputStream()
                    else null
                } catch (e: Exception) { null } ?: continue

                _status.value = _status.value.copy(currentFileName = item.name)

                inStream.use { stream ->
                    val chunkBuffer = ByteArray(CHUNK_DATA_SIZE)
                    val fecBlockChunks = mutableListOf<ByteArray>()
                    var chunkIndex = 0
                    var bytesRead: Int

                    while (stream.read(chunkBuffer).also { bytesRead = it } != -1) {
                        if (isCancelledFlag) break
                        val currentChunk = if (bytesRead == CHUNK_DATA_SIZE) chunkBuffer.copyOf() else chunkBuffer.copyOf(bytesRead)
                        fecBlockChunks.add(currentChunk)
                        sentChunksCache["${item.id}:$chunkIndex"] = currentChunk

                        val chunkCrc = computeCrc32(currentChunk)
                        val nameBytes = item.id.toByteArray(Charsets.UTF_8)
                        val packetBuffer = ByteBuffer.allocate(4 + 1 + 2 + nameBytes.size + 4 + 4 + 8 + currentChunk.size)
                        packetBuffer.putInt(MAGIC)
                        packetBuffer.put(OP_CHUNK)
                        packetBuffer.putShort(nameBytes.size.toShort())
                        packetBuffer.put(nameBytes)
                        packetBuffer.putInt(chunkIndex)
                        packetBuffer.putInt(currentChunk.size)
                        packetBuffer.putLong(chunkCrc)
                        packetBuffer.put(currentChunk)

                        val packet = DatagramPacket(packetBuffer.array(), packetBuffer.array().size, groupAddress, MULTICAST_PORT)
                        socket.send(packet)

                        totalBytesSent += bytesRead
                        _status.value = _status.value.copy(totalTransferredBytes = totalBytesSent)
                        onProgress(totalBytesSent, manifest.totalBytes, item.name)

                        // Forward Error Correction: Send XOR Parity Chunk every FEC_BLOCK_SIZE chunks
                        if (fecBlockChunks.size == FEC_BLOCK_SIZE) {
                            val parity = xorParity(fecBlockChunks)
                            val parityCrc = computeCrc32(parity)
                            val parityBuffer = ByteBuffer.allocate(4 + 1 + 2 + nameBytes.size + 4 + 4 + 8 + parity.size)
                            parityBuffer.putInt(MAGIC)
                            parityBuffer.put(OP_PARITY)
                            parityBuffer.putShort(nameBytes.size.toShort())
                            parityBuffer.put(nameBytes)
                            parityBuffer.putInt(chunkIndex / FEC_BLOCK_SIZE)
                            parityBuffer.putInt(parity.size)
                            parityBuffer.putLong(parityCrc)
                            parityBuffer.put(parity)

                            val parityPacket = DatagramPacket(parityBuffer.array(), parityBuffer.array().size, groupAddress, MULTICAST_PORT)
                            socket.send(parityPacket)
                            fecBlockChunks.clear()
                        }

                        chunkIndex++
                        // Throttle slightly to avoid overwhelming network switch
                        Thread.sleep(1)
                    }

                    // Flush residual parity if any remaining
                    if (fecBlockChunks.isNotEmpty()) {
                        val parity = xorParity(fecBlockChunks)
                        val parityCrc = computeCrc32(parity)
                        val nameBytes = item.id.toByteArray(Charsets.UTF_8)
                        val parityBuffer = ByteBuffer.allocate(4 + 1 + 2 + nameBytes.size + 4 + 4 + 8 + parity.size)
                        parityBuffer.putInt(MAGIC)
                        parityBuffer.put(OP_PARITY)
                        parityBuffer.putShort(nameBytes.size.toShort())
                        parityBuffer.put(nameBytes)
                        parityBuffer.putInt(chunkIndex / FEC_BLOCK_SIZE)
                        parityBuffer.putInt(parity.size)
                        parityBuffer.putLong(parityCrc)
                        parityBuffer.put(parity)

                        val parityPacket = DatagramPacket(parityBuffer.array(), parityBuffer.array().size, groupAddress, MULTICAST_PORT)
                        socket.send(parityPacket)
                    }
                }
            }

            // Allow receivers to request any missing chunks before completing
            handleIncomingNacks(200)

            // 3. Announce Complete
            val completeBuf = ByteBuffer.allocate(5)
            completeBuf.putInt(MAGIC)
            completeBuf.put(OP_COMPLETE)
            val compPacket = DatagramPacket(completeBuf.array(), completeBuf.array().size, groupAddress, MULTICAST_PORT)
            repeat(3) {
                socket.send(compPacket)
                Thread.sleep(80)
            }
            handleIncomingNacks(150)

            onComplete(!isCancelledFlag)
        } catch (e: Exception) {
            Log.e(TAG, "Multicast group broadcast error", e)
            onComplete(false)
        } finally {
            socket?.close()
            activeSocket = null
            releaseMulticastLock()
            _status.value = _status.value.copy(isRunning = false)
        }
    }

    /**
     * RECEIVER: Joins the multicast group and receives files broadcast to the room.
     */
    suspend fun startGroupReceiver(
        onManifestReceived: (TransferManifest) -> Unit,
        onProgress: (transferred: Long, total: Long, activeFileName: String) -> Unit,
        onFileFinished: (fileId: String, completedFile: File) -> Unit,
        onComplete: (Boolean) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        acquireMulticastLock()
        isCancelledFlag = false
        val groupAddress = InetAddress.getByName(MULTICAST_IP)
        val groupSocketAddress = java.net.InetSocketAddress(groupAddress, MULTICAST_PORT)
        val wifiIf = com.astron.app.core.util.NetworkUtils.getActiveWifiNetworkInterface()
        var socket: MulticastSocket? = null

        try {
            socket = MulticastSocket(MULTICAST_PORT).apply {
                reuseAddress = true
                receiveBufferSize = 512 * 1024
                try {
                    if (wifiIf != null) {
                        joinGroup(groupSocketAddress, wifiIf)
                    } else {
                        joinGroup(groupAddress)
                    }
                } catch (e: Exception) {
                    joinGroup(groupAddress)
                }
            }
            activeSocket = socket
            _status.value = MulticastGroupStatus(isRunning = true, isSender = false)

            val buffer = ByteArray(65535)
            var activeManifest: TransferManifest? = null
            val fileWriters = ConcurrentHashMap<String, RandomAccessFile>()
            val writtenBytesPerFile = ConcurrentHashMap<String, Long>()
            val receivedChunksPerFile = ConcurrentHashMap<String, MutableSet<Int>>()
            val fileBlockChunks = ConcurrentHashMap<String, ConcurrentHashMap<Int, ConcurrentHashMap<Int, ByteArray>>>()
            val fileBlockParity = ConcurrentHashMap<String, ConcurrentHashMap<Int, ByteArray>>()
            var totalReceived = 0L

            while (isActive && !isCancelledFlag) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (_: Exception) { break }

                if (packet.length < 5) continue
                val byteBuffer = ByteBuffer.wrap(packet.data, 0, packet.length)
                val magic = byteBuffer.int
                if (magic != MAGIC) continue

                val op = byteBuffer.get()
                when (op) {
                    OP_ANNOUNCE -> {
                        val manifestLen = byteBuffer.int
                        if (manifestLen <= byteBuffer.remaining()) {
                            val manifestBytes = ByteArray(manifestLen)
                            byteBuffer.get(manifestBytes)
                            try {
                                val manifest = json.decodeFromString<TransferManifest>(String(manifestBytes, Charsets.UTF_8))
                                activeManifest = manifest
                                onManifestReceived(manifest)
                                _status.value = _status.value.copy(totalBytes = manifest.totalBytes)
                            } catch (e: Exception) {
                                Log.w(TAG, "Error decoding group manifest", e)
                            }
                        }
                    }

                    OP_CHUNK -> {
                        val nameLen = byteBuffer.short.toInt() and 0xFFFF
                        if (byteBuffer.remaining() >= nameLen + 4 + 4 + 8) {
                            val nameBytes = ByteArray(nameLen)
                            byteBuffer.get(nameBytes)
                            val fileId = String(nameBytes, Charsets.UTF_8)
                            val chunkIndex = byteBuffer.int
                            val payloadLen = byteBuffer.int
                            val crc = byteBuffer.long
                            if (payloadLen <= byteBuffer.remaining()) {
                                val payload = ByteArray(payloadLen)
                                byteBuffer.get(payload)

                                // Verify integrity
                                if (computeCrc32(payload) == crc) {
                                    val receivedSet = receivedChunksPerFile.getOrPut(fileId) { ConcurrentHashMap.newKeySet() }
                                    if (receivedSet.add(chunkIndex)) {
                                        val manifestItem = activeManifest?.items?.find { it.id == fileId }
                                        val fileName = manifestItem?.name ?: "multicast_file_$fileId"

                                        val writer = fileWriters.getOrPut(fileId) {
                                            val dest = File(storageManager.downloadDirectory, fileName)
                                            RandomAccessFile(dest, "rw")
                                        }

                                        val offset = chunkIndex.toLong() * CHUNK_DATA_SIZE
                                        writer.seek(offset)
                                        writer.write(payload)

                                        // Store for FEC parity recovery
                                        val blockIdx = chunkIndex / FEC_BLOCK_SIZE
                                        val blocks = fileBlockChunks.getOrPut(fileId) { ConcurrentHashMap() }
                                        val block = blocks.getOrPut(blockIdx) { ConcurrentHashMap() }
                                        block[chunkIndex] = payload

                                        val prev = writtenBytesPerFile[fileId] ?: 0L
                                        val updated = prev + payloadLen
                                        writtenBytesPerFile[fileId] = updated

                                        totalReceived += payloadLen
                                        _status.value = _status.value.copy(
                                            totalTransferredBytes = totalReceived,
                                            currentFileName = fileName
                                        )
                                        onProgress(totalReceived, activeManifest?.totalBytes ?: totalReceived, fileName)

                                        if (manifestItem != null && updated >= manifestItem.size) {
                                            try { writer.close() } catch (_: Exception) {}
                                            fileWriters.remove(fileId)
                                            val finishedFile = File(storageManager.downloadDirectory, fileName)
                                            onFileFinished(fileId, finishedFile)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OP_PARITY -> {
                        val nameLen = byteBuffer.short.toInt() and 0xFFFF
                        if (byteBuffer.remaining() >= nameLen + 4 + 4 + 8) {
                            val nameBytes = ByteArray(nameLen)
                            byteBuffer.get(nameBytes)
                            val fileId = String(nameBytes, Charsets.UTF_8)
                            val blockIndex = byteBuffer.int
                            val parityLen = byteBuffer.int
                            val crc = byteBuffer.long
                            if (parityLen <= byteBuffer.remaining()) {
                                val parityPayload = ByteArray(parityLen)
                                byteBuffer.get(parityPayload)

                                if (computeCrc32(parityPayload) == crc) {
                                    val parities = fileBlockParity.getOrPut(fileId) { ConcurrentHashMap() }
                                    parities[blockIndex] = parityPayload

                                    val manifestItem = activeManifest?.items?.find { it.id == fileId }
                                    if (manifestItem != null) {
                                        val totalFileChunks = ((manifestItem.size + CHUNK_DATA_SIZE - 1) / CHUNK_DATA_SIZE).toInt()
                                        val startChunk = blockIndex * FEC_BLOCK_SIZE
                                        val endChunk = minOf(startChunk + FEC_BLOCK_SIZE, totalFileChunks)
                                        val expectedInBlock = endChunk - startChunk

                                        val receivedSet = receivedChunksPerFile.getOrPut(fileId) { ConcurrentHashMap.newKeySet() }
                                        val missingInBlock = (startChunk until endChunk).filter { !receivedSet.contains(it) }

                                        // Forward Error Correction: If exactly 1 chunk was dropped, recover it instantly!
                                        if (missingInBlock.size == 1) {
                                            val missingChunkIndex = missingInBlock.first()
                                            val blocks = fileBlockChunks.getOrPut(fileId) { ConcurrentHashMap() }
                                            val block = blocks.getOrPut(blockIndex) { ConcurrentHashMap() }
                                            val availableChunks = block.values.toList()

                                            if (availableChunks.size == expectedInBlock - 1) {
                                                val recovered = recoverMissingChunk(availableChunks, parityPayload)
                                                val expectedSize = if (missingChunkIndex == totalFileChunks - 1) {
                                                    val rem = (manifestItem.size % CHUNK_DATA_SIZE).toInt()
                                                    if (rem == 0) CHUNK_DATA_SIZE else rem
                                                } else {
                                                    CHUNK_DATA_SIZE
                                                }
                                                val validRecovered = if (recovered.size > expectedSize) recovered.copyOf(expectedSize) else recovered

                                                if (receivedSet.add(missingChunkIndex)) {
                                                    val writer = fileWriters.getOrPut(fileId) {
                                                        val dest = File(storageManager.downloadDirectory, manifestItem.name)
                                                        RandomAccessFile(dest, "rw")
                                                    }
                                                    val offset = missingChunkIndex.toLong() * CHUNK_DATA_SIZE
                                                    writer.seek(offset)
                                                    writer.write(validRecovered)

                                                    val prev = writtenBytesPerFile[fileId] ?: 0L
                                                    val updated = prev + validRecovered.size
                                                    writtenBytesPerFile[fileId] = updated

                                                    totalReceived += validRecovered.size
                                                    _status.value = _status.value.copy(
                                                        totalTransferredBytes = totalReceived,
                                                        currentFileName = manifestItem.name
                                                    )
                                                    onProgress(totalReceived, activeManifest.totalBytes, manifestItem.name)

                                                    if (updated >= manifestItem.size) {
                                                        try { writer.close() } catch (_: Exception) {}
                                                        fileWriters.remove(fileId)
                                                        val finishedFile = File(storageManager.downloadDirectory, manifestItem.name)
                                                        onFileFinished(fileId, finishedFile)
                                                    }
                                                }
                                            }
                                        } else if (missingInBlock.size > 1) {
                                            // More than 1 chunk missing in block: Send OP_NACK for ARQ retransmission
                                            for (missingChunkIndex in missingInBlock) {
                                                sendNack(socket, groupAddress, fileId, missingChunkIndex)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OP_COMPLETE -> {
                        // Check for any remaining missing chunks across all manifest items
                        val missingChunks = mutableListOf<Pair<String, Int>>()
                        activeManifest?.items?.forEach { item ->
                            val totalFileChunks = ((item.size + CHUNK_DATA_SIZE - 1) / CHUNK_DATA_SIZE).toInt()
                            val receivedSet = receivedChunksPerFile[item.id] ?: emptySet()
                            for (cIdx in 0 until totalFileChunks) {
                                if (!receivedSet.contains(cIdx)) {
                                    missingChunks.add(item.id to cIdx)
                                }
                            }
                        }

                        if (missingChunks.isNotEmpty()) {
                            for ((fId, cIdx) in missingChunks) {
                                sendNack(socket, groupAddress, fId, cIdx)
                            }
                            // Listen briefly for arriving retransmitted OP_CHUNK packets
                            val retxBuffer = ByteArray(2048)
                            val retxPacket = DatagramPacket(retxBuffer, retxBuffer.size)
                            val retxDeadline = System.currentTimeMillis() + 350
                            while (System.currentTimeMillis() < retxDeadline && !isCancelledFlag) {
                                try {
                                    socket.soTimeout = maxOf(1, (retxDeadline - System.currentTimeMillis()).toInt())
                                    socket.receive(retxPacket)
                                    val bb = ByteBuffer.wrap(retxPacket.data, 0, retxPacket.length)
                                    if (bb.remaining() >= 5 && bb.int == MAGIC && bb.get() == OP_CHUNK) {
                                        val nLen = bb.short.toInt() and 0xFFFF
                                        if (bb.remaining() >= nLen + 4 + 4 + 8) {
                                            val nBytes = ByteArray(nLen)
                                            bb.get(nBytes)
                                            val fId = String(nBytes, Charsets.UTF_8)
                                            val cIdx = bb.int
                                            val pLen = bb.int
                                            val cCrc = bb.long
                                            if (pLen <= bb.remaining()) {
                                                val payload = ByteArray(pLen)
                                                bb.get(payload)
                                                if (computeCrc32(payload) == cCrc) {
                                                    val rcvSet = receivedChunksPerFile.getOrPut(fId) { ConcurrentHashMap.newKeySet() }
                                                    if (rcvSet.add(cIdx)) {
                                                        val mItem = activeManifest?.items?.find { it.id == fId }
                                                        val fName = mItem?.name ?: "multicast_file_$fId"
                                                        val writer = fileWriters.getOrPut(fId) {
                                                            val dest = File(storageManager.downloadDirectory, fName)
                                                            RandomAccessFile(dest, "rw")
                                                        }
                                                        writer.seek(cIdx.toLong() * CHUNK_DATA_SIZE)
                                                        writer.write(payload)
                                                        val prev = writtenBytesPerFile[fId] ?: 0L
                                                        writtenBytesPerFile[fId] = prev + pLen
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } catch (_: java.net.SocketTimeoutException) {
                                    break
                                } catch (_: Exception) {}
                            }
                        }

                        for ((fileId, writer) in fileWriters) {
                            val manifestItem = activeManifest?.items?.find { it.id == fileId }
                            val written = writtenBytesPerFile[fileId] ?: 0L
                            try {
                                writer.close()
                                if (manifestItem != null && written >= manifestItem.size) {
                                    val finishedFile = File(storageManager.downloadDirectory, manifestItem.name)
                                    onFileFinished(fileId, finishedFile)
                                }
                            } catch (_: Exception) {}
                        }
                        fileWriters.clear()
                        val allCompleted = activeManifest != null && activeManifest.items.all { item ->
                            (writtenBytesPerFile[item.id] ?: 0L) >= item.size
                        }
                        onComplete(allCompleted)
                        break
                    }
                }
            }

            fileWriters.values.forEach { try { it.close() } catch (_: Exception) {} }
        } catch (e: Exception) {
            Log.e(TAG, "Multicast receiver error", e)
            onComplete(false)
        } finally {
            try {
                if (wifiIf != null) {
                    socket?.leaveGroup(groupSocketAddress, wifiIf)
                } else {
                    socket?.leaveGroup(groupAddress)
                }
            } catch (_: Exception) {}
            socket?.close()
            activeSocket = null
            releaseMulticastLock()
            _status.value = _status.value.copy(isRunning = false)
        }
    }

    fun stopGroupTransfer() {
        isCancelledFlag = true
        activeSocket?.close()
        activeSocket = null
        releaseMulticastLock()
        _status.value = MulticastGroupStatus()
    }
}
