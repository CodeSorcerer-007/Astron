package com.astron.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.astron.app.core.model.ItemStatus

/**
 * Room Entity tracking individual file progress within a session.
 * Stores byte offset checkpoint to allow seamless resumption.
 */
@Entity(
    tableName = "transfer_files",
    foreignKeys = [
        ForeignKey(
            entity = TransferSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class TransferFileEntity(
    @PrimaryKey
    val fileId: String,
    val sessionId: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val relativePath: String = "",
    val checksumSha256: String? = null,
    val bytesTransferred: Long = 0L,
    val status: ItemStatus = ItemStatus.PENDING,
    val localUri: String? = null,
    val localFilePath: String? = null,
    val lastChunkIndex: Int = 0,
    val lastCheckpointOffset: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
)
