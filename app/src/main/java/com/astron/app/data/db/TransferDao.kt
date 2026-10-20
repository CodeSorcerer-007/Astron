package com.astron.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.astron.app.core.model.ItemStatus
import com.astron.app.core.model.SessionState
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: TransferSessionEntity)

    @Update
    suspend fun updateSession(session: TransferSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<TransferFileEntity>)

    @Update
    suspend fun updateFile(file: TransferFileEntity)

    @Query("SELECT * FROM transfer_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): TransferSessionEntity?

    @Query("SELECT * FROM transfer_files WHERE sessionId = :sessionId")
    suspend fun getFilesForSession(sessionId: String): List<TransferFileEntity>

    @Query("SELECT * FROM transfer_sessions WHERE state IN (:activeStates) OR isInterrupted = 1 ORDER BY updatedAt DESC")
    suspend fun getInterruptedSessions(
        activeStates: List<SessionState> = listOf(SessionState.TRANSFERRING, SessionState.PAUSED, SessionState.CONNECTING)
    ): List<TransferSessionEntity>

    @Query("SELECT * FROM transfer_sessions WHERE state IN (:activeStates) OR isInterrupted = 1 ORDER BY updatedAt DESC")
    fun observeInterruptedSessions(
        activeStates: List<SessionState> = listOf(SessionState.TRANSFERRING, SessionState.PAUSED, SessionState.CONNECTING)
    ): Flow<List<TransferSessionEntity>>

    @Query("UPDATE transfer_sessions SET transferredBytes = :transferred, updatedAt = :now WHERE sessionId = :sessionId")
    suspend fun updateSessionProgress(sessionId: String, transferred: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE transfer_sessions SET state = :state, isInterrupted = :isInterrupted, updatedAt = :now WHERE sessionId = :sessionId")
    suspend fun updateSessionState(sessionId: String, state: SessionState, isInterrupted: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE transfer_files SET bytesTransferred = :transferred, lastCheckpointOffset = :offset, lastChunkIndex = :chunkIndex, status = :status, updatedAt = :now WHERE fileId = :fileId")
    suspend fun updateFileCheckpoint(
        fileId: String,
        transferred: Long,
        offset: Long,
        chunkIndex: Int,
        status: ItemStatus,
        now: Long = System.currentTimeMillis()
    )

    @Query("UPDATE transfer_sessions SET isInterrupted = 1 WHERE state IN (:inProgressStates)")
    suspend fun markActiveSessionsAsInterrupted(
        inProgressStates: List<SessionState> = listOf(SessionState.TRANSFERRING, SessionState.CONNECTING)
    )

    @Query("DELETE FROM transfer_sessions WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM transfer_sessions WHERE state = 'COMPLETED'")
    suspend fun clearCompletedSessions()

    @Transaction
    suspend fun saveFullSession(session: TransferSessionEntity, files: List<TransferFileEntity>) {
        insertSession(session)
        insertFiles(files)
    }
}
