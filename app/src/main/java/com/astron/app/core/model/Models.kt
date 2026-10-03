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
)

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
    val lastSeen: Long = System.currentTimeMillis()
)

@Serializable
enum class TransportType(val displayName: String, val badge: String) {
    LOCAL_WIFI("Local Wi-Fi", "⚡ High-Speed LAN"),
    WIFI_DIRECT("Wi-Fi Direct", "📶 Direct P2P"),
    WEB_DROP("Web Drop", "🌐 Browser Mode"),
    OPTICAL_LIGHT("Light Drop", "✨ Zero-Radio Optical"),
    BLUETOOTH("Bluetooth", "🔵 BLE")
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
