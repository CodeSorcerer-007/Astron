package com.astron.app.core.model

import kotlinx.serialization.Serializable

/**
 * Represents a single file or directory item queued or active in a transfer.
 */
@Serializable
data class TransferItem(
    val id: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val uriString: String = "",
    val relativePath: String = "",
    val checksumSha256: String? = null,
    val isDirectory: Boolean = false,
    val transferredBytes: Long = 0L,
    val status: ItemStatus = ItemStatus.PENDING
) {
    val uri: android.net.Uri? get() = if (uriString.isNotBlank()) android.net.Uri.parse(uriString) else null
    val realFilePath: String? get() = if (uriString.startsWith("file://")) uriString.removePrefix("file://") else if (uriString.startsWith("/")) uriString else null

    constructor(
        id: String,
        name: String,
        size: Long,
        mimeType: String,
        uri: android.net.Uri? = null,
        realFilePath: String? = null,
        relativePath: String = "",
        checksumSha256: String? = null,
        isDirectory: Boolean = false,
        transferredBytes: Long = 0L,
        status: ItemStatus = ItemStatus.PENDING
    ) : this(
        id = id,
        name = name,
        size = size,
        mimeType = mimeType,
        uriString = uri?.toString() ?: (realFilePath?.let { "file://$it" } ?: ""),
        relativePath = relativePath,
        checksumSha256 = checksumSha256,
        isDirectory = isDirectory,
        transferredBytes = transferredBytes,
        status = status
    )
}


@Serializable
enum class ItemStatus {
    PENDING,
    TRANSFERRING,
    VERIFYING,
    COMPLETED,
    FAILED,
    SKIPPED
}

/**
 * Metadata manifest describing the entire batch of files in a transfer session.
 */
@Serializable
data class TransferManifest(
    val sessionId: String,
    val senderDeviceId: String,
    val senderDeviceName: String,
    val totalFiles: Int,
    val totalBytes: Long,
    val items: List<ManifestItem>,
    val createdAt: Long = System.currentTimeMillis(),
    val protocolVersion: Int = 1
)

@Serializable
data class ManifestItem(
    val id: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val relativePath: String = "",
    val checksumSha256: String? = null
)

/**
 * Represents a nearby discovered or paired device.
 */
@Serializable
data class PeerDevice(
    val id: String,
    val name: String,
    val hostAddress: String,
    val port: Int,
    val transportType: TransportType = TransportType.LOCAL_WIFI,
    val model: String = "",
    val isTrusted: Boolean = false,
    val signalStrength: Int = 95,
    val hotspotSsid: String? = null,
    val hotspotPass: String? = null,
    val lastSeen: Long = System.currentTimeMillis()
)

@Serializable
enum class TransportType(val displayName: String, val badge: String) {
    LOCAL_WIFI("Local Wi-Fi", "⚡ High-Speed LAN"),
    WIFI_DIRECT("Wi-Fi Direct", "📶 Direct P2P"),
    HOTSPOT("Hotspot", "📡 Local Hotspot"),
    BLUETOOTH("Bluetooth", "🔵 Bluetooth Classic"),
    BLE_BEACON("BLE Beacon", "⚡ BLE Low-Power"),
    INTERNET_RELAY("Internet Relay", "🌍 Remote Transfer"),
    WEB_DROP("Web Drop", "🌐 Browser Mode"),
    OPTICAL_LIGHT("Light Drop", "✨ Zero-Radio Optical"),
    MULTICAST_GROUP("Multicast Group", "📢 1-to-Many Group"),
    UPNP_DIRECT("Direct WAN (UPnP)", "🌐 Direct Router P2P"),
    WIFI_AWARE("Wi-Fi Aware", "📡 NAN Direct"),
    USB_OTG("USB OTG", "🔌 USB Transfer")
}

enum class TransferDirection {
    SEND,
    RECEIVE
}

enum class SessionState {
    IDLE,
    PREPARING,
    ADVERTISING,
    DISCOVERING,
    CONNECTING,
    WAITING_APPROVAL,
    TRANSFERRING,
    PAUSED,
    VERIFYING,
    COMPLETED,
    CANCELLED,
    FAILED
}

data class TransferSession(
    val sessionId: String,
    val direction: TransferDirection,
    val peer: PeerDevice? = null,
    val transport: TransportType = TransportType.LOCAL_WIFI,
    val manifest: TransferManifest? = null,
    val items: List<TransferItem> = emptyList(),
    val totalBytes: Long = 0L,
    val transferredBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long = 0L,
    val currentItemIndex: Int = 0,
    val currentItemName: String = "",
    val state: SessionState = SessionState.IDLE,
    val verifiedFilesCount: Int = 0,
    val errorMessage: String? = null
) {
    val progress: Float
        get() = if (totalBytes > 0) (transferredBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    val completedFilesCount: Int
        get() = items.count { it.status == ItemStatus.COMPLETED }
}

enum class TransferPriority(val title: String, val description: String) {
    BALANCED("Balanced", "Optimal speed & battery efficiency"),
    MAX_SPEED("Maximum Speed", "Aggressive parallel streaming & throughput"),
    BATTERY_SAVER("Battery Saver", "Reduces scanning and throttles peak power"),
    RELIABILITY("High Reliability", "Smaller chunks with aggressive retries")
}

enum class DuplicateAction {
    REPLACE,
    KEEP_BOTH,
    SKIP
}

@Serializable
data class TransferHistoryRecord(
    val id: String,
    val peerName: String,
    val direction: TransferDirection,
    val fileCount: Int,
    val totalBytes: Long,
    val timestamp: Long,
    val durationMs: Long,
    val status: String,
    val transportType: TransportType
)
