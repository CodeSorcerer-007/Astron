package com.astron.app.transfer

import android.content.Context
import android.util.Log
import com.astron.app.core.model.ItemStatus
import com.astron.app.core.model.ManifestItem
import com.astron.app.core.model.PeerDevice
import com.astron.app.core.model.SessionState
import com.astron.app.core.model.TransferDirection
import com.astron.app.core.model.TransferHistoryRecord
import com.astron.app.core.model.TransferItem
import com.astron.app.core.model.TransferManifest
import com.astron.app.core.model.TransportType
import com.astron.app.core.security.CryptoManager
import com.astron.app.core.storage.StorageManager
import com.astron.app.data.HistoryRepository
import com.astron.app.data.PreferencesRepository
import com.astron.app.service.TransferForegroundService
import com.astron.app.transport.lan.LanTransport
import com.astron.app.transport.lightdrop.LightDropManager
import com.astron.app.transport.webdrop.WebDropServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable
import kotlinx.coroutines.cancel
import java.io.File
import com.astron.app.core.util.SoundFeedbackManager
import com.astron.app.transport.hotspot.HotspotManager
import java.util.UUID

class TransferEngine(
    private val context: Context,
    val storageManager: StorageManager,
    val preferencesRepository: PreferencesRepository,
    val historyRepository: HistoryRepository,
    val lanTransport: LanTransport = LanTransport(context, storageManager),
    val webDropServer: WebDropServer = WebDropServer(context, storageManager),
    val lightDropManager: LightDropManager = LightDropManager(),
    val soundFeedbackManager: SoundFeedbackManager = SoundFeedbackManager(),
    val hotspotManager: HotspotManager = HotspotManager(context)
) : Closeable {
    companion object {
        const val TAG = "TransferEngine"
        private const val SPEED_SMOOTHING_FACTOR = 0.25f // Exponential moving average weight
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var activeJob: Job? = null
    private var isCancelledFlag = false

    override fun close() {
        activeJob?.cancel()
        soundFeedbackManager.close()
        hotspotManager.close()
        scope.cancel()
    }

    init {
        scope.launch(Dispatchers.IO) {
            try {
                storageManager.cleanupOrphanedPartialFiles()
            } catch (e: Exception) {
                Log.w(TAG, "Error cleaning orphaned partial files", e)
            }
        }
    }

    // Pause/Resume state
    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    fun togglePause() {
        val next = !_isPaused.value
        _isPaused.value = next
        _currentSession.update { current ->
            if (current != null) {
                if (next) current.copy(state = SessionState.PAUSED)
                else current.copy(state = SessionState.TRANSFERRING)
            } else null
        }
    }

    suspend fun awaitUnpause() {
        _isPaused.first { !it }
    }

    // Discovered nearby peers on LAN/NSD
    private val _discoveredPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    val discoveredPeers: StateFlow<List<PeerDevice>> = _discoveredPeers.asStateFlow()

    // Active transfer session
    private val _currentSession = MutableStateFlow<com.astron.app.core.model.TransferSession?>(null)
    val currentSession: StateFlow<com.astron.app.core.model.TransferSession?> = _currentSession.asStateFlow()

    // Incoming request approval callback for Receiver flow
    var onIncomingTransferPrompt: (suspend (TransferManifest) -> Boolean)? = null

    // Speed and ETA calculation tracking
    private var lastSpeedCalcTime = 0L
    private var lastSpeedCalcBytes = 0L
    private var smoothedSpeedBps = 0L

    private fun resetSpeedTracking() {
        lastSpeedCalcTime = System.currentTimeMillis()
        lastSpeedCalcBytes = 0L
        smoothedSpeedBps = 0L
    }

    fun startPeerDiscovery() {
        _discoveredPeers.value = emptyList()
        lanTransport.startDiscovery(
            onPeerFound = { peer ->
                _discoveredPeers.update { current ->
                    val filtered = current.filter { it.id != peer.id }
                    filtered + peer
                }
            },
            onPeerLost = { serviceName ->
                val base = serviceName.removePrefix("Astron-").substringBeforeLast('-')
                _discoveredPeers.update { current ->
                    current.filter { !it.name.equals(base, ignoreCase = true) && !serviceName.contains(it.name) }
                }
            }
        )
    }

    fun stopPeerDiscovery() {
        lanTransport.stopDiscovery()
    }

    /**
     * Registers a known peer discovered via NFC tap-to-pair, QR scan, or Wi-Fi Direct.
     */
    fun registerDiscoveredPeer(peer: PeerDevice) {
        _discoveredPeers.update { current ->
            val filtered = current.filter { it.id != peer.id && it.hostAddress != peer.hostAddress }
            listOf(peer) + filtered
        }
    }

    /**
     * SENDER: Initiates transfer of selected items to target peer device.
     */
    fun startSendTransfer(peer: PeerDevice, items: List<TransferItem>) {
        if (items.isEmpty()) return
        isCancelledFlag = false
        val totalBytes = items.sumOf { it.size }
        val sessionId = UUID.randomUUID().toString()

        activeJob?.cancel()
        activeJob = scope.launch(Dispatchers.IO) {
            val deviceName = preferencesRepository.deviceNameFlow.first()
            val manifestItems = items.map {
                ManifestItem(
                    id = it.id,
                    name = it.name,
                    size = it.size,
                    mimeType = it.mimeType,
                    relativePath = it.relativePath,
                    checksumSha256 = null // computed on stream
                )
            }
            val manifest = TransferManifest(
                sessionId = sessionId,
                senderDeviceId = UUID.randomUUID().toString(),
                senderDeviceName = deviceName,
                totalFiles = items.size,
                totalBytes = totalBytes,
                items = manifestItems
            )

            val session = com.astron.app.core.model.TransferSession(
                sessionId = sessionId,
                direction = TransferDirection.SEND,
                peer = peer,
                transport = peer.transportType,
                manifest = manifest,
                items = items,
                totalBytes = totalBytes,
                state = SessionState.CONNECTING
            )
            _currentSession.value = session
            TransferForegroundService.start(context, peer.name)

            val priority = preferencesRepository.transferPriorityFlow.first()
            val soundEnabled = preferencesRepository.soundEnabledFlow.first()
            val startTime = System.currentTimeMillis()
            resetSpeedTracking()

            _currentSession.update { it?.copy(state = SessionState.TRANSFERRING) }
            soundFeedbackManager.playTransferStart(soundEnabled)

            val success = lanTransport.sendFiles(
                peer = peer,
                manifest = manifest,
                items = items,
                priority = priority,
                onProgress = { transferred, total, activeFileName ->
                    updateThroughputAndProgress(transferred, total, activeFileName, peer.name)
                },
                onFileVerified = { fileId ->
                    _currentSession.update { current ->
                        if (current == null) return@update null
                        val updatedItems = current.items.map { item ->
                            if (item.id == fileId) item.copy(status = ItemStatus.COMPLETED) else item
                        }
                        current.copy(
                            items = updatedItems,
                            verifiedFilesCount = current.verifiedFilesCount + 1
                        )
                    }
                },
                isCancelled = { isCancelledFlag },
                awaitUnpause = { awaitUnpause() }
            )

            val duration = System.currentTimeMillis() - startTime
            TransferForegroundService.stop(context)

            if (success) {
                soundFeedbackManager.playTransferComplete(soundEnabled)
                _currentSession.update {
                    it?.copy(
                        state = SessionState.COMPLETED,
                        transferredBytes = totalBytes,
                        speedBytesPerSec = 0L,
                        etaSeconds = 0L
                    )
                }
                historyRepository.addRecord(
                    TransferHistoryRecord(
                        id = sessionId,
                        peerName = peer.name,
                        direction = TransferDirection.SEND,
                        fileCount = items.size,
                        totalBytes = totalBytes,
                        timestamp = System.currentTimeMillis(),
                        durationMs = duration,
                        status = "Completed",
                        transportType = peer.transportType
                    )
                )
            } else {
                val finalState = if (isCancelledFlag) SessionState.CANCELLED else SessionState.FAILED
                _currentSession.update { it?.copy(state = finalState) }
                historyRepository.addRecord(
                    TransferHistoryRecord(
                        id = sessionId,
                        peerName = peer.name,
                        direction = TransferDirection.SEND,
                        fileCount = items.size,
                        totalBytes = totalBytes,
                        timestamp = System.currentTimeMillis(),
                        durationMs = duration,
                        status = if (isCancelledFlag) "Cancelled" else "Failed",
                        transportType = peer.transportType
                    )
                )
            }
        }
    }

    /**
     * RECEIVER: Starts listening for incoming LAN connections and advertises presence.
     */
    fun startReceiverMode() {
        isCancelledFlag = false
        activeJob?.cancel()
        activeJob = scope.launch(Dispatchers.IO) {
            val deviceName = preferencesRepository.deviceNameFlow.first()
            lanTransport.startAdvertising(deviceName)

            _currentSession.value = com.astron.app.core.model.TransferSession(
                sessionId = UUID.randomUUID().toString(),
                direction = TransferDirection.RECEIVE,
                state = SessionState.ADVERTISING
            )

            val priority = preferencesRepository.transferPriorityFlow.first()

            lanTransport.startReceiverServer(
                priority = priority,
                onIncomingRequest = { manifest ->
                    _currentSession.update { current ->
                        current?.copy(
                            manifest = manifest,
                            totalBytes = manifest.totalBytes,
                            items = manifest.items.map {
                                TransferItem(
                                    id = it.id,
                                    name = it.name,
                                    size = it.size,
                                    mimeType = it.mimeType,
                                    checksumSha256 = it.checksumSha256,
                                    relativePath = it.relativePath
                                )
                            },
                            state = SessionState.WAITING_APPROVAL
                        )
                    }

                    // Check storage space before accepting
                    val available = storageManager.getAvailableStorageBytes()
                    if (manifest.totalBytes > available) {
                        Log.w(TAG, "Not enough storage for incoming transfer: needed ${manifest.totalBytes}, free $available")
                        _currentSession.update { it?.copy(state = SessionState.FAILED) }
                        return@startReceiverServer false
                    }

                    val autoAccept = preferencesRepository.autoAcceptTrustedFlow.first()
                    val soundEnabled = preferencesRepository.soundEnabledFlow.first()
                    if (!autoAccept) {
                        soundFeedbackManager.playIncomingAlert(soundEnabled)
                    }
                    val approved = if (autoAccept) {
                        true
                    } else {
                        onIncomingTransferPrompt?.invoke(manifest) ?: true
                    }

                    if (approved) {
                        resetSpeedTracking()
                        _currentSession.update { it?.copy(state = SessionState.TRANSFERRING) }
                        TransferForegroundService.start(context, manifest.senderDeviceName)
                        soundFeedbackManager.playTransferStart(soundEnabled)
                    } else {
                        _currentSession.update { it?.copy(state = SessionState.CANCELLED) }
                    }
                    approved
                },
                onProgress = { transferred, total, activeFileName ->
                    val senderName = _currentSession.value?.manifest?.senderDeviceName ?: "Sender"
                    updateThroughputAndProgress(transferred, total, activeFileName, senderName)
                },
                onFileCompleted = { fileId, completedFile, isVerified ->
                    _currentSession.update { current ->
                        if (current == null) return@update null
                        val updatedItems = current.items.map { item ->
                            if (item.id == fileId) {
                                item.copy(status = if (isVerified) ItemStatus.COMPLETED else ItemStatus.FAILED)
                            } else item
                        }
                        val nextCount = current.verifiedFilesCount + (if (isVerified) 1 else 0)
                        if (nextCount == current.items.size && current.items.isNotEmpty()) {
                            scope.launch {
                                val soundEnabled = preferencesRepository.soundEnabledFlow.first()
                                soundFeedbackManager.playTransferComplete(soundEnabled)
                            }
                        }
                        current.copy(
                            items = updatedItems,
                            verifiedFilesCount = nextCount
                        )
                    }
                },
                isCancelled = { isCancelledFlag },
                awaitUnpause = { awaitUnpause() }
            )
        }
    }

    private fun updateThroughputAndProgress(
        transferred: Long,
        total: Long,
        activeFileName: String,
        peerName: String
    ) {
        val now = System.currentTimeMillis()
        val timeDeltaMs = now - lastSpeedCalcTime

        if (timeDeltaMs >= 400) { // update speed every ~400ms for stable display
            val bytesDelta = transferred - lastSpeedCalcBytes
            val instantSpeed = if (timeDeltaMs > 0) (bytesDelta * 1000L) / timeDeltaMs else 0L

            smoothedSpeedBps = if (smoothedSpeedBps == 0L) {
                instantSpeed
            } else {
                (SPEED_SMOOTHING_FACTOR * instantSpeed + (1f - SPEED_SMOOTHING_FACTOR) * smoothedSpeedBps).toLong()
            }

            lastSpeedCalcTime = now
            lastSpeedCalcBytes = transferred

            val remainingBytes = (total - transferred).coerceAtLeast(0L)
            val etaSec = if (smoothedSpeedBps > 0) remainingBytes / smoothedSpeedBps else 0L

            _currentSession.update {
                it?.copy(
                    transferredBytes = transferred,
                    speedBytesPerSec = smoothedSpeedBps,
                    etaSeconds = etaSec,
                    currentItemName = activeFileName
                )
            }

            TransferForegroundService.updateProgress(
                context,
                peerName,
                transferred,
                total,
                smoothedSpeedBps,
                activeFileName
            )
        } else {
            _currentSession.update {
                it?.copy(
                    transferredBytes = transferred,
                    currentItemName = activeFileName
                )
            }
        }
    }

    fun cancelTransfer() {
        isCancelledFlag = true
        _isPaused.value = false
        activeJob?.cancel()
        lanTransport.stopAdvertising()
        lanTransport.stopDiscovery()
        lanTransport.stopReceiverServer()
        webDropServer.stopServer()
        TransferForegroundService.stop(context)
        _currentSession.update { it?.copy(state = SessionState.CANCELLED) }
    }

    fun dismissSession() {
        cancelTransfer()
        _currentSession.value = null
    }
}
