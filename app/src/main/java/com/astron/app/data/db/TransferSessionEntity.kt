package com.astron.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.astron.app.core.model.SessionState
import com.astron.app.core.model.TransferDirection
import com.astron.app.core.model.TransportType

/**
 * Room Entity representing a persistent transfer session.
 * Preserved across app restarts, system kills, and device reboots so
 * transfers can be resumed at the exact byte offset where they stopped.
 */
@Entity(tableName = "transfer_sessions")
data class TransferSessionEntity(
    @PrimaryKey
    val sessionId: String,
    val direction: TransferDirection,
    val peerId: String,
    val peerName: String,
    val peerAddress: String,
    val transportType: TransportType,
    val totalBytes: Long,
    val transferredBytes: Long,
    val totalFiles: Int,
    val verifiedFilesCount: Int,
    val state: SessionState,
    val startedAt: Long,
    val updatedAt: Long,
    val isInterrupted: Boolean = false
)
