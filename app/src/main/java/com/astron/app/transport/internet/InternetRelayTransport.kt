package com.astron.app.transport.internet

import android.content.Context
import android.net.Uri
import android.util.Log
import com.astron.app.core.model.ManifestItem
import com.astron.app.core.model.TransferItem
import com.astron.app.core.model.TransferManifest
import com.astron.app.core.security.CryptoManager
import com.astron.app.core.storage.StorageManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpMethod
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Serializable
data class RelayControlMessage(
    val type: String, // "JOIN", "PEER_CONNECTED", "MANIFEST", "CHUNK_HEADER", "ACK", "ERROR"
    val sessionId: String,
    val pairingCode: String,
    val role: String, // "SENDER", "RECEIVER"
    val payload: String = ""
)

sealed class RelayConnectionState {
    data object Idle : RelayConnectionState()
    data class Connecting(val endpoint: String) : RelayConnectionState()
    data class WaitingForPeer(val pairingCode: String) : RelayConnectionState()
    data class Connected(val peerRole: String) : RelayConnectionState()
    data class Error(val message: String) : RelayConnectionState()
}

@Singleton
class InternetRelayTransport @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val storageManager: StorageManager
) {
    companion object {
        private const val TAG = "InternetRelayTransport"
        const val DEFAULT_RELAY_HOST = "relay.astron.app"
        const val DEFAULT_RELAY_PORT = 443
        const val CHUNK_SIZE = 64 * 1024 // 64 KB for internet streaming
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val client by lazy {
        HttpClient(Android) {
            install(WebSockets) {
                pingIntervalMillis = 15_000
            }
        }
    }

    private val _connectionState = MutableStateFlow<RelayConnectionState>(RelayConnectionState.Idle)
    val connectionState: StateFlow<RelayConnectionState> = _connectionState.asStateFlow()

    private var activeSession: DefaultClientWebSocketSession? = null

    /**
     * SENDER: Connects to relay, waits for receiver with the same pairingCode, then sends files encrypted.
     */
    suspend fun sendViaRelay(
        relayHost: String = DEFAULT_RELAY_HOST,
        relayPort: Int = DEFAULT_RELAY_PORT,
        pairingCode: String,
        manifest: TransferManifest,
        items: List<TransferItem>,
        onProgress: (transferred: Long, total: Long, activeFileName: String) -> Unit,
        onFileVerified: (fileId: String) -> Unit,
        isCancelled: () -> Boolean,
        awaitUnpause: suspend () -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            _connectionState.value = RelayConnectionState.Connecting(relayHost)
            val sessionKey = CryptoManager.deriveKeyFromSecret(pairingCode)

            var isTransferSuccess = false

            client.webSocket(
                method = HttpMethod.Get,
                host = relayHost,
                port = relayPort,
                path = "/ws/transfer"
            ) {
                activeSession = this

                // 1. Send JOIN message
                val joinMsg = RelayControlMessage(
                    type = "JOIN",
                    sessionId = manifest.sessionId,
                    pairingCode = pairingCode,
                    role = "SENDER"
                )
                send(Frame.Text(json.encodeToString(joinMsg)))
                _connectionState.value = RelayConnectionState.WaitingForPeer(pairingCode)

                // 2. Wait for PEER_CONNECTED
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        val msg = try { json.decodeFromString<RelayControlMessage>(text) } catch (e: Exception) { null }
                        if (msg?.type == "PEER_CONNECTED") {
                            _connectionState.value = RelayConnectionState.Connected("RECEIVER")
                            break
                        }
                    }
                }

                // 3. Send encrypted Manifest
                val manifestJson = json.encodeToString(manifest)
                val encryptedManifest = CryptoManager.encryptPayload(manifestJson.toByteArray(Charsets.UTF_8), sessionKey)
                val manifestMsg = RelayControlMessage(
                    type = "MANIFEST",
                    sessionId = manifest.sessionId,
                    pairingCode = pairingCode,
                    role = "SENDER",
                    payload = android.util.Base64.encodeToString(encryptedManifest, android.util.Base64.NO_WRAP)
                )
                send(Frame.Text(json.encodeToString(manifestMsg)))

                // 4. Stream encrypted file chunks
                var totalTransferred = 0L
                val totalBytes = manifest.totalBytes
                val buffer = ByteArray(CHUNK_SIZE)

                for (item in items) {
                    if (isCancelled()) break
                    awaitUnpause()

                    val inputStream = openItemInputStream(item) ?: continue
                    val md5OrSha = MessageDigest.getInstance("SHA-256")

                    inputStream.use { stream ->
                        var bytesRead: Int
                        while (stream.read(buffer).also { bytesRead = it } != -1) {
                            if (isCancelled()) break
                            awaitUnpause()

                            val rawChunk = if (bytesRead == CHUNK_SIZE) buffer else buffer.copyOf(bytesRead)
                            md5OrSha.update(rawChunk)
                            val encryptedChunk = CryptoManager.encryptPayload(rawChunk, sessionKey)

                            // Send binary frame
                            send(Frame.Binary(true, encryptedChunk))

                            totalTransferred += bytesRead
                            onProgress(totalTransferred, totalBytes, item.name)
                        }
                    }

                    onFileVerified(item.id)
                }

                // 5. Send COMPLETE
                val doneMsg = RelayControlMessage(
                    type = "COMPLETE",
                    sessionId = manifest.sessionId,
                    pairingCode = pairingCode,
                    role = "SENDER"
                )
                send(Frame.Text(json.encodeToString(doneMsg)))
                isTransferSuccess = !isCancelled()
            }

            _connectionState.value = RelayConnectionState.Idle
            isTransferSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Error in sendViaRelay", e)
            _connectionState.value = RelayConnectionState.Error(e.message ?: "Relay transfer failed")
            false
        } finally {
            activeSession = null
        }
    }

    /**
     * RECEIVER: Connects to relay with pairing code, accepts manifest and receives encrypted chunks.
     */
    suspend fun receiveViaRelay(
        relayHost: String = DEFAULT_RELAY_HOST,
        relayPort: Int = DEFAULT_RELAY_PORT,
        pairingCode: String,
        onIncomingManifest: suspend (TransferManifest) -> Boolean,
        onProgress: (transferred: Long, total: Long, activeFileName: String) -> Unit,
        onFileCompleted: (fileId: String, file: File, isVerified: Boolean) -> Unit,
        isCancelled: () -> Boolean,
        awaitUnpause: suspend () -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            _connectionState.value = RelayConnectionState.Connecting(relayHost)
            val sessionKey = CryptoManager.deriveKeyFromSecret(pairingCode)
            var isTransferSuccess = false

            client.webSocket(
                method = HttpMethod.Get,
                host = relayHost,
                port = relayPort,
                path = "/ws/transfer"
            ) {
                activeSession = this

                // 1. Send JOIN message as RECEIVER
                val joinMsg = RelayControlMessage(
                    type = "JOIN",
                    sessionId = "",
                    pairingCode = pairingCode,
                    role = "RECEIVER"
                )
                send(Frame.Text(json.encodeToString(joinMsg)))
                _connectionState.value = RelayConnectionState.Connected("SENDER")

                var manifest: TransferManifest? = null
                var currentItemIndex = 0
                var currentOutputFile: File? = null
                var currentOutputStream: java.io.FileOutputStream? = null
                var currentFileTransferred = 0L
                var totalTransferred = 0L
                var currentSha = MessageDigest.getInstance("SHA-256")

                for (frame in incoming) {
                    if (isCancelled()) break
                    awaitUnpause()

                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            val msg = try { json.decodeFromString<RelayControlMessage>(text) } catch (e: Exception) { null }
                            if (msg?.type == "MANIFEST") {
                                val encryptedBytes = android.util.Base64.decode(msg.payload, android.util.Base64.NO_WRAP)
                                val decryptedBytes = CryptoManager.decryptPayload(encryptedBytes, sessionKey)
                                val manifestString = String(decryptedBytes, Charsets.UTF_8)
                                val parsedManifest = json.decodeFromString<TransferManifest>(manifestString)

                                val accepted = onIncomingManifest(parsedManifest)
                                if (!accepted) {
                                    send(Frame.Text(json.encodeToString(RelayControlMessage("REJECT", msg.sessionId, pairingCode, "RECEIVER"))))
                                    break
                                }
                                manifest = parsedManifest

                                if (parsedManifest.items.isNotEmpty()) {
                                    val firstItem = parsedManifest.items[0]
                                    val f = storageManager.createDestinationFile(firstItem.name, firstItem.relativePath)
                                    currentOutputFile = f
                                    currentOutputStream = java.io.FileOutputStream(f)
                                    currentSha = MessageDigest.getInstance("SHA-256")
                                    currentFileTransferred = 0L
                                }
                            } else if (msg?.type == "COMPLETE") {
                                currentOutputStream?.flush()
                                currentOutputStream?.close()
                                isTransferSuccess = true
                                break
                            }
                        }

                        is Frame.Binary -> {
                            val encryptedChunk = frame.readBytes()
                            val chunk = CryptoManager.decryptPayload(encryptedChunk, sessionKey)
                            currentOutputStream?.write(chunk)
                            currentSha.update(chunk)
                            currentFileTransferred += chunk.size
                            totalTransferred += chunk.size

                            val currentItem = manifest?.items?.getOrNull(currentItemIndex)
                            val fileName = currentItem?.name ?: "File"
                            onProgress(totalTransferred, manifest?.totalBytes ?: 0L, fileName)

                            // Check if current file finished
                            if (currentItem != null && currentFileTransferred >= currentItem.size) {
                                currentOutputStream?.flush()
                                currentOutputStream?.close()
                                currentOutputStream = null

                                val computedHash = currentSha.digest().joinToString("") { "%02x".format(it) }
                                val expectedHash = currentItem.checksumSha256
                                val verified = expectedHash == null || expectedHash.equals(computedHash, ignoreCase = true)

                                currentOutputFile?.let { completedFile ->
                                    onFileCompleted(currentItem.id, completedFile, verified)
                                }

                                currentItemIndex++
                                val activeManifest = manifest
                                if (activeManifest != null && currentItemIndex < activeManifest.items.size) {
                                    val nextItem = activeManifest.items[currentItemIndex]
                                    val f = storageManager.createDestinationFile(nextItem.name, nextItem.relativePath)
                                    currentOutputFile = f
                                    currentOutputStream = java.io.FileOutputStream(f)
                                    currentSha = MessageDigest.getInstance("SHA-256")
                                    currentFileTransferred = 0L
                                }
                            }
                        }
                        else -> Unit
                    }
                }
            }

            _connectionState.value = RelayConnectionState.Idle
            isTransferSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Error in receiveViaRelay", e)
            _connectionState.value = RelayConnectionState.Error(e.message ?: "Relay receive failed")
            false
        } finally {
            activeSession = null
        }
    }

    fun close() {
        try {
            val session = activeSession
            activeSession = null
            if (session != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        session.close()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error closing relay session", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error closing relay session", e)
        }
    }

    private fun openItemInputStream(item: TransferItem): InputStream? {
        return try {
            val uri = item.uri
            val path = item.realFilePath
            if (uri != null) {
                context.contentResolver.openInputStream(uri)
            } else if (path != null) {
                File(path).inputStream()
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open stream for ${item.name}", e)
            null
        }
    }
}
