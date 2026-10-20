package com.astron.app.data.db

import android.content.Context
import android.util.Log
import com.astron.app.core.model.ItemStatus
import com.astron.app.core.model.ManifestItem
import com.astron.app.core.model.PeerDevice
import com.astron.app.core.model.SessionState
import com.astron.app.core.model.TransferDirection
import com.astron.app.core.model.TransferItem
import com.astron.app.core.model.TransferManifest
import com.astron.app.core.model.TransportType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class InterruptedSession(
    val session: TransferSessionEntity,
    val files: List<TransferFileEntity>
)

@Singleton
class TransferPersistenceManager @Inject constructor(
    private val transferDao: TransferDao
) {
    companion object {
        private const val TAG = "TransferPersistenceMgr"
    }

    /**
     * Call at app startup to check for sessions that were running when the process was killed.
     */
    suspend fun markStartupInterruptedSessions() = withContext(Dispatchers.IO) {
        try {
            transferDao.markActiveSessionsAsInterrupted()
        } catch (e: Exception) {
            Log.e(TAG, "Failed marking interrupted sessions on startup", e)
        }
    }

    fun observeInterruptedSessions(): Flow<List<TransferSessionEntity>> {
        return transferDao.observeInterruptedSessions()
    }

    suspend fun getInterruptedSessions(): List<InterruptedSession> = withContext(Dispatchers.IO) {
        try {
            val sessions = transferDao.getInterruptedSessions()
            sessions.map { session ->
                val files = transferDao.getFilesForSession(session.sessionId)
                InterruptedSession(session = session, files = files)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching interrupted sessions", e)
            emptyList()
        }
    }

    suspend fun getSessionWithFiles(sessionId: String): InterruptedSession? = withContext(Dispatchers.IO) {
        try {
            val session = transferDao.getSessionById(sessionId) ?: return@withContext null
            val files = transferDao.getFilesForSession(sessionId)
            InterruptedSession(session = session, files = files)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching session $sessionId", e)
            null
        }
    }

    suspend fun saveSessionStart(
        sessionId: String,
        direction: TransferDirection,
        peer: PeerDevice,
        manifest: TransferManifest,
        items: List<TransferItem>
    ) = withContext(Dispatchers.IO) {
        try {
            val sessionEntity = TransferSessionEntity(
                sessionId = sessionId,
                direction = direction,
                peerId = peer.id,
                peerName = peer.name,
                peerAddress = peer.hostAddress,
                transportType = peer.transportType,
                totalBytes = manifest.totalBytes,
                transferredBytes = 0L,
                totalFiles = items.size,
                verifiedFilesCount = 0,
                state = SessionState.TRANSFERRING,
                startedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                isInterrupted = false
            )

            val fileEntities = items.map { item ->
                TransferFileEntity(
                    fileId = item.id,
                    sessionId = sessionId,
                    name = item.name,
                    size = item.size,
                    mimeType = item.mimeType,
                    relativePath = item.relativePath,
                    checksumSha256 = item.checksumSha256,
                    bytesTransferred = 0L,
                    status = ItemStatus.PENDING,
                    localUri = item.uri?.toString(),
                    localFilePath = null
                )
            }

            transferDao.saveFullSession(sessionEntity, fileEntities)
            Log.d(TAG, "Persisted session $sessionId (${items.size} files, ${manifest.totalBytes} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist session start", e)
        }
    }

    suspend fun updateChunkProgress(
        sessionId: String,
        fileId: String,
        fileBytesTransferred: Long,
        sessionTotalTransferred: Long,
        chunkIndex: Int,
        offset: Long
    ) = withContext(Dispatchers.IO) {
        try {
            transferDao.updateSessionProgress(sessionId, sessionTotalTransferred)
            transferDao.updateFileCheckpoint(
                fileId = fileId,
                transferred = fileBytesTransferred,
                offset = offset,
                chunkIndex = chunkIndex,
                status = ItemStatus.TRANSFERRING
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed updating checkpoint for $fileId", e)
        }
    }

    suspend fun markFileCompleted(fileId: String, verified: Boolean) = withContext(Dispatchers.IO) {
        try {
            transferDao.updateFileCheckpoint(
                fileId = fileId,
                transferred = 0L,
                offset = 0L,
                chunkIndex = 0,
                status = if (verified) ItemStatus.COMPLETED else ItemStatus.FAILED
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed marking file completed: $fileId", e)
        }
    }

    suspend fun markSessionComplete(sessionId: String) = withContext(Dispatchers.IO) {
        try {
            transferDao.updateSessionState(sessionId, SessionState.COMPLETED, isInterrupted = false)
        } catch (e: Exception) {
            Log.w(TAG, "Failed marking session complete: $sessionId", e)
        }
    }

    suspend fun markSessionCancelled(sessionId: String) = withContext(Dispatchers.IO) {
        try {
            transferDao.updateSessionState(sessionId, SessionState.CANCELLED, isInterrupted = false)
        } catch (e: Exception) {
            Log.w(TAG, "Failed marking session cancelled: $sessionId", e)
        }
    }

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        try {
            transferDao.deleteSession(sessionId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed deleting session $sessionId", e)
        }
    }
}
