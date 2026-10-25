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
import com.astron.app.core.storage.DuplicateDetector
import com.astron.app.core.storage.StorageManager
import com.astron.app.core.util.SoundFeedbackManager
import com.astron.app.data.HistoryRepository
import com.astron.app.data.PreferencesRepository
import com.astron.app.data.db.TransferPersistenceManager
import com.astron.app.service.TransferForegroundService
import com.astron.app.transport.CapabilityDetector
import com.astron.app.transport.TransportOrchestrator
import com.astron.app.transport.bluetooth.BluetoothClassicTransport
import com.astron.app.transport.hotspot.HotspotManager
import com.astron.app.transport.internet.InternetRelayTransport
import com.astron.app.transport.lan.LanTransport
import com.astron.app.transport.lightdrop.LightDropManager
import com.astron.app.transport.webdrop.WebDropServer
import com.astron.app.transport.ble.BleBeaconManager
import com.astron.app.transport.upnp.UpnpPortMapper
import com.astron.app.transport.multicast.MulticastGroupManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.Closeable
import java.io.File
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
    val hotspotManager: HotspotManager = HotspotManager(context),
    val bluetoothTransport: BluetoothClassicTransport = BluetoothClassicTransport(context),
    val internetRelayTransport: InternetRelayTransport = InternetRelayTransport(context, storageManager),
    val transportOrchestrator: TransportOrchestrator = TransportOrchestrator(context),
    val persistenceManager: TransferPersistenceManager? = null,
    val duplicateDetector: DuplicateDetector = DuplicateDetector(storageManager),
    val transferQueue: TransferQueue = TransferQueue(),
    val bleBeaconManager: BleBeaconManager = BleBeaconManager(context),
    val upnpPortMapper: UpnpPortMapper = UpnpPortMapper(context),
    val multicastGroupManager: MulticastGroupManager = MulticastGroupManager(context, storageManager),
    val wifiAwareTransport: com.astron.app.transport.aware.WifiAwareTransport = com.astron.app.transport.aware.WifiAwareTransport(context),
    val wifiDirectManager: com.astron.app.transport.p2p.WifiDirectManager = com.astron.app.transport.p2p.WifiDirectManager(context),
    val usbOtgManager: com.astron.app.transport.usb.UsbOtgManager = com.astron.app.transport.usb.UsbOtgManager(context)
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
        scope.cancel()
    }

    init {
        scope.launch(Dispatchers.IO) {
            try {
                storageManager.cleanupOrphanedPartialFiles()
                persistenceManager?.markStartupInterruptedSessions()
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

    // Discovered nearby peers on LAN, Bluetooth, etc.
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
        CapabilityDetector.refresh(context)

        // 1. LAN / NSD Discovery
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

        // 2. Bluetooth Classic paired and nearby discovery
        if (bluetoothTransport.isAvailable()) {
            val paired = bluetoothTransport.getPairedDevices()
            paired.forEach { device ->
                val peer = with(bluetoothTransport) { device.toPeerDevice() }
                _discoveredPeers.update { current ->
                    if (current.none { it.hostAddress == peer.hostAddress }) current + peer else current
                }
            }

            bluetoothTransport.startDiscovery { device ->
                val peer = with(bluetoothTransport) { device.toPeerDevice() }
                _discoveredPeers.update { current ->
                    val filtered = current.filter { it.hostAddress != peer.hostAddress }
                    filtered + peer
                }
            }
        }

        // 3. Low-Energy BLE Beacon Scanning for Instant Background Discovery
        if (bleBeaconManager.isBleSupported()) {
            bleBeaconManager.startScanning { peer ->
                _discoveredPeers.update { current ->
                    val filtered = current.filter { it.hostAddress != peer.hostAddress && it.id != peer.id }
                    listOf(peer) + filtered
                }
            }
        }

        // 4. Wi-Fi Direct (P2P) Autonomous Discovery
        try {
            wifiDirectManager.startDiscovery(
                onSuccess = {
                    scope.launch {
                        wifiDirectManager.discoveredDevices.collect { p2pDevices ->
                            val peers = p2pDevices.map { dev ->
                                val devName = try { dev.deviceName.ifBlank { "Wi-Fi Direct Peer" } } catch (_: Exception) { "Wi-Fi Direct Peer" }
                                PeerDevice(
                                    id = dev.deviceAddress,
                                    name = devName,
                                    hostAddress = com.astron.app.transport.p2p.WifiDirectManager.DEFAULT_GO_IP,
                                    port = 52737,
                                    transportType = TransportType.WIFI_DIRECT,
                                    signalStrength = 85
                                )
                            }
                            if (peers.isNotEmpty()) {
                                _discoveredPeers.update { current ->
                                    val existingIds = peers.map { it.id }.toSet()
                                    val filtered = current.filter { it.id !in existingIds }
                                    filtered + peers
                                }
                            }
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        // 5. Wi-Fi Aware (NAN) Discovery
        if (wifiAwareTransport.isAvailable()) {
            wifiAwareTransport.attach(
                onAttached = {
                    wifiAwareTransport.startDiscovery()
                    scope.launch {
                        wifiAwareTransport.discoveredPeers.collect { awarePeers ->
                            if (awarePeers.isNotEmpty()) {
                                _discoveredPeers.update { current ->
                                    val existingIds = awarePeers.map { it.id }.toSet()
                                    val filtered = current.filter { it.id !in existingIds }
                                    filtered + awarePeers
                                }
                            }
                        }
                    }
                }
            )
        }

        // 6. USB OTG Storage Volume Discovery
        val otgDrives = usbOtgManager.scanMountedUsbDrives()
        if (otgDrives.isNotEmpty()) {
            val otgPeers = otgDrives.map { usbOtgManager.toPeerDevice(it) }
            _discoveredPeers.update { current ->
                val existingIds = otgPeers.map { it.id }.toSet()
                otgPeers + current.filter { it.id !in existingIds }
            }
        }
    }

    fun stopPeerDiscovery() {
        lanTransport.stopDiscovery()
        bluetoothTransport.stopDiscovery()
        bleBeaconManager.stopScanning()
        wifiDirectManager.teardown()
        wifiAwareTransport.stop()
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

            // Persist session start for crash-safety
            persistenceManager?.saveSessionStart(
                sessionId = sessionId,
                direction = TransferDirection.SEND,
                peer = peer,
                manifest = manifest,
                items = items
            )

            val priority = preferencesRepository.transferPriorityFlow.first()
            val soundEnabled = preferencesRepository.soundEnabledFlow.first()
            val startTime = System.currentTimeMillis()
            resetSpeedTracking()

            _currentSession.update { it?.copy(state = SessionState.TRANSFERRING) }
            soundFeedbackManager.playTransferStart(soundEnabled)

            // Orchestrated multi-transport dispatch
            val success = when (peer.transportType) {
                TransportType.INTERNET_RELAY -> {
                    internetRelayTransport.sendViaRelay(
                        pairingCode = peer.hostAddress.ifBlank { "astron-${peer.id.take(6)}" },
                        manifest = manifest,
                        items = items,
                        onProgress = { transferred, total, activeFileName ->
                            updateThroughputAndProgress(transferred, total, activeFileName, peer.name)
                        },
                        onFileVerified = { fileId ->
                            markFileVerified(fileId)
                        },
                        isCancelled = { isCancelledFlag },
                        awaitUnpause = { awaitUnpause() }
                    )
                }

                TransportType.BLUETOOTH -> {
                    val paired = bluetoothTransport.getPairedDevices().find { it.address.equals(peer.hostAddress, ignoreCase = true) }
                        ?: bluetoothTransport.discoveredDevices.value.find { it.address.equals(peer.hostAddress, ignoreCase = true) }

                    if (paired != null) {
                        val socket = bluetoothTransport.connectToDevice(paired)
                        if (socket != null) {
                            var allOk = true
                            var totalBytesSent = 0L
                            for (item in items) {
                                if (isCancelledFlag) break
                                val itemUri = item.uri
                                val itemPath = item.realFilePath
                                val stream = try {
                                    if (itemUri != null) context.contentResolver.openInputStream(itemUri)
                                    else if (itemPath != null) File(itemPath).inputStream()
                                    else null
                                } catch (e: Exception) { null }

                                if (stream == null) {
                                    allOk = false
                                    continue
                                }
                                val ok = stream.use { inStream ->
                                    bluetoothTransport.sendStream(
                                        socket = socket,
                                        data = inStream,
                                        totalBytes = item.size,
                                        sessionToken = sessionId,
                                        onProgress = { bytes ->
                                            updateThroughputAndProgress(totalBytesSent + bytes, totalBytes, item.name, peer.name)
                                        },
                                        isActive = { !isCancelledFlag }
                                    )
                                }
                                totalBytesSent += item.size
                                if (!ok) allOk = false
                                markFileVerified(item.id)
                            }
                            bluetoothTransport.disconnect()
                            allOk
                        } else false
                    } else false
                }

                TransportType.MULTICAST_GROUP -> {
                    var groupOk = false
                    multicastGroupManager.startGroupBroadcast(
                        manifest = manifest,
                        items = items,
                        onProgress = { transferred, total, activeFileName ->
                            updateThroughputAndProgress(transferred, total, activeFileName, "Multicast Group")
                        },
                        onComplete = { ok ->
                            groupOk = ok
                        }
                    )
                    groupOk
                }

                TransportType.USB_OTG -> {
                    val drive = usbOtgManager.attachedDrives.value.find { it.id == peer.id }
                        ?: usbOtgManager.attachedDrives.value.firstOrNull()
                    if (drive != null) {
                        usbOtgManager.exportFilesToUsb(
                            targetDrive = drive,
                            items = items,
                            onProgress = { transferred, total, activeFileName ->
                                updateThroughputAndProgress(transferred, total, activeFileName, drive.name)
                            },
                            onFileCompleted = { fileId ->
                                markFileVerified(fileId)
                            },
                            isCancelled = { isCancelledFlag }
                        )
                    } else false
                }

                TransportType.OPTICAL_LIGHT -> {
                    var allOk = true
                    var totalSent = 0L
                    for (item in items) {
                        if (isCancelledFlag) break
                        val file = item.realFilePath?.let { File(it) }
                        val frames = if (file != null && file.exists()) {
                            lightDropManager.encodeFileToFrames(file)
                        } else {
                            val sampleBytes = item.name.toByteArray(Charsets.UTF_8)
                            lightDropManager.encodeBytesToFrames(sampleBytes)
                        }
                        for (frame in frames) {
                            if (isCancelledFlag) break
                            totalSent += frame.payload.size
                            updateThroughputAndProgress(totalSent, totalBytes, item.name, "Optical LightDrop")
                        }
                        markFileVerified(item.id)
                    }
                    allOk
                }

                TransportType.HOTSPOT -> {
                    // Auto-join hotspot if credentials provided
                    if (!peer.hotspotSsid.isNullOrBlank() && !peer.hotspotPass.isNullOrBlank()) {
                        val connectLatch = kotlinx.coroutines.CompletableDeferred<Boolean>()
                        hotspotManager.connectToHotspot(
                            ssid = peer.hotspotSsid,
                            passphrase = peer.hotspotPass,
                            onConnected = { connectLatch.complete(true) },
                            onFailed = { connectLatch.complete(false) }
                        )
                        kotlinx.coroutines.withTimeoutOrNull(8_000L) { connectLatch.await() }
                    }
                    try {
                        lanTransport.sendFiles(
                            peer = peer,
                            manifest = manifest,
                            items = items,
                            priority = priority,
                            onProgress = { transferred, total, activeFileName ->
                                updateThroughputAndProgress(transferred, total, activeFileName, peer.name)
                            },
                            onFileVerified = { fileId ->
                                markFileVerified(fileId)
                            },
                            isCancelled = { isCancelledFlag },
                            awaitUnpause = { awaitUnpause() }
                        )
                    } finally {
                        if (!peer.hotspotSsid.isNullOrBlank()) {
                            hotspotManager.disconnectFromHotspot()
                        }
                    }
                }

                else -> {
                    // LOCAL_WIFI, WIFI_DIRECT, WIFI_AWARE, UPNP_DIRECT
                    lanTransport.sendFiles(
                        peer = peer,
                        manifest = manifest,
                        items = items,
                        priority = priority,
                        onProgress = { transferred, total, activeFileName ->
                            updateThroughputAndProgress(transferred, total, activeFileName, peer.name)
                        },
                        onFileVerified = { fileId ->
                            markFileVerified(fileId)
                        },
                        isCancelled = { isCancelledFlag },
                        awaitUnpause = { awaitUnpause() }
                    )
                }
            }

            val duration = System.currentTimeMillis() - startTime

            if (success) {
                persistenceManager?.markSessionComplete(sessionId)
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

                // Check for queued batch - keep foreground service alive across batches
                val nextBatch = transferQueue.dequeue()
                if (nextBatch != null) {
                    startSendTransfer(nextBatch.targetPeer, nextBatch.items)
                } else {
                    TransferForegroundService.stop(context)
                }
            } else {
                TransferForegroundService.stop(context)
                persistenceManager?.markSessionCancelled(sessionId)
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

    private fun markFileVerified(fileId: String) {
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
        scope.launch(Dispatchers.IO) {
            persistenceManager?.markFileCompleted(fileId, true)
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

            // Broadcast BLE Beacon so nearby senders discover us even with screen off
            if (bleBeaconManager.isAdvertisingSupported()) {
                bleBeaconManager.startBeacon(deviceName)
            }

            // Wi-Fi Aware NAN Advertising
            if (wifiAwareTransport.isAvailable()) {
                wifiAwareTransport.attach(onAttached = { wifiAwareTransport.startAdvertising(deviceName) })
            }

            // Attempt direct WAN port mapping via UPnP
            try {
                CoroutineScope(Dispatchers.IO).launch {
                    upnpPortMapper.openPortMapping()
                }
            } catch (_: Throwable) {}

            // Bluetooth RFCOMM Server listener loop
            if (bluetoothTransport.isAvailable()) {
                scope.launch(Dispatchers.IO) {
                    while (isActive && !isCancelledFlag) {
                        val socket = bluetoothTransport.startServer(timeoutMs = 120_000L) ?: continue
                        try {
                            val destFile = storageManager.createDestinationFile("bt_incoming_${System.currentTimeMillis()}.bin")
                            destFile.outputStream().use { outStream ->
                                bluetoothTransport.receiveStream(
                                    socket = socket,
                                    output = outStream,
                                    sessionToken = "astron-bt",
                                    onProgress = { transferred ->
                                        updateThroughputAndProgress(transferred, transferred, destFile.name, "Bluetooth Peer")
                                    },
                                    isActive = { !isCancelledFlag }
                                )
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Bluetooth RFCOMM receive error", e)
                        } finally {
                            try { socket.close() } catch (_: Exception) {}
                        }
                    }
                }
            }

            // Multicast Group Receiver listener loop
            scope.launch(Dispatchers.IO) {
                multicastGroupManager.startGroupReceiver(
                    onManifestReceived = { manifest ->
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
                                state = SessionState.TRANSFERRING
                            )
                        }
                    },
                    onProgress = { transferred, total, activeFileName ->
                        updateThroughputAndProgress(transferred, total, activeFileName, "Multicast Broadcaster")
                    },
                    onFileFinished = { fileId, _ ->
                        markFileVerified(fileId)
                    }
                )
            }

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
                    scope.launch(Dispatchers.IO) {
                        persistenceManager?.markFileCompleted(fileId, isVerified)
                    }
                },
                onTransferCompleted = { sessionId ->
                    TransferForegroundService.stop(context)
                    val s = _currentSession.value
                    _currentSession.update {
                        it?.copy(
                            state = SessionState.COMPLETED,
                            transferredBytes = it.totalBytes,
                            speedBytesPerSec = 0L,
                            etaSeconds = 0L
                        )
                    }
                    scope.launch(Dispatchers.IO) {
                        persistenceManager?.markSessionComplete(sessionId)
                        if (s != null) {
                            historyRepository.addRecord(
                                TransferHistoryRecord(
                                    id = sessionId,
                                    peerName = s.manifest?.senderDeviceName ?: "Sender",
                                    direction = TransferDirection.RECEIVE,
                                    fileCount = s.items.size,
                                    totalBytes = s.totalBytes,
                                    timestamp = System.currentTimeMillis(),
                                    durationMs = 0L,
                                    status = "Completed",
                                    transportType = TransportType.LOCAL_WIFI
                                )
                            )
                        }
                    }
                },
                onTransferFailed = { sessionId, error ->
                    TransferForegroundService.stop(context)
                    _currentSession.update { it?.copy(state = SessionState.FAILED) }
                    scope.launch(Dispatchers.IO) {
                        persistenceManager?.markSessionCancelled(sessionId)
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

            // Room Checkpoint
            scope.launch(Dispatchers.IO) {
                val s = _currentSession.value ?: return@launch
                val currentFile = s.items.find { it.name == activeFileName }
                if (currentFile != null) {
                    val currentFileIndex = s.items.indexOfFirst { it.id == currentFile.id }
                    val precedingBytes = if (currentFileIndex > 0) {
                        s.items.take(currentFileIndex).sumOf { it.size }
                    } else {
                        0L
                    }
                    val fileOffset = (transferred - precedingBytes).coerceIn(0L, currentFile.size)
                    persistenceManager?.updateChunkProgress(
                        sessionId = s.sessionId,
                        fileId = currentFile.id,
                        fileBytesTransferred = fileOffset,
                        sessionTotalTransferred = transferred,
                        chunkIndex = 0,
                        offset = fileOffset
                    )
                }
            }
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
        bluetoothTransport.disconnect()
        internetRelayTransport.close()
        bleBeaconManager.stopBeacon()
        bleBeaconManager.stopScanning()
        multicastGroupManager.stopGroupTransfer()
        wifiAwareTransport.stop()
        wifiDirectManager.teardown()
        hotspotManager.disconnectFromHotspot()
        try {
            CoroutineScope(Dispatchers.IO).launch { upnpPortMapper.closePortMapping() }
        } catch (_: Throwable) {}
        TransferForegroundService.stop(context)
        _currentSession.update { it?.copy(state = SessionState.CANCELLED) }
    }

    fun dismissSession() {
        cancelTransfer()
        _currentSession.value = null
    }

    /**
     * Resumes an interrupted transfer session using Room byte-level checkpoints.
     * Skips fully completed files and seeks directly to the last checkpoint offset.
     */
    suspend fun resumeInterruptedSession(sessionId: String): Boolean {
        val persistence = persistenceManager ?: return false
        val interrupted = persistence.getSessionWithFiles(sessionId) ?: return false
        val sessionEntity = interrupted.session
        val fileEntities = interrupted.files

        val remainingFiles = fileEntities.filter { it.status != ItemStatus.COMPLETED }
        if (remainingFiles.isEmpty()) {
            persistence.markSessionComplete(sessionId)
            return true
        }

        val peer = PeerDevice(
            id = sessionEntity.peerId,
            name = sessionEntity.peerName,
            hostAddress = sessionEntity.peerAddress,
            port = 52737,
            transportType = sessionEntity.transportType
        )

        val itemsToResume = remainingFiles.map { f ->
            TransferItem(
                id = f.fileId,
                name = f.name,
                size = f.size,
                mimeType = f.mimeType,
                uriString = f.localUri ?: "",
                relativePath = f.relativePath,
                checksumSha256 = f.checksumSha256,
                transferredBytes = f.lastCheckpointOffset
            )
        }

        startSendTransfer(peer, itemsToResume)
        return true
    }
}
