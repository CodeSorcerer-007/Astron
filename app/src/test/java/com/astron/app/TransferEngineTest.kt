package com.astron.app

import android.content.ContextWrapper
import com.astron.app.core.model.SessionState
import com.astron.app.core.model.TransferDirection
import com.astron.app.core.model.TransferManifest
import com.astron.app.core.model.TransferSession
import com.astron.app.core.model.TransportType
import com.astron.app.core.storage.StorageManager
import com.astron.app.data.HistoryRepository
import com.astron.app.data.PreferencesRepository
import com.astron.app.transfer.TransferEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferEngineTest {

    private val dummyContext = object : ContextWrapper(null) {
        override fun getSystemService(name: String): Any? = null
        override fun getApplicationContext(): android.content.Context = this
        override fun getPackageName(): String = "com.astron.app"
    }
    private val storageManager = StorageManager(dummyContext)
    private val preferencesRepository = PreferencesRepository(dummyContext)
    private val historyRepository = HistoryRepository(dummyContext)

    @Test
    fun testInitialPauseStateIsFalse() {
        val engine = TransferEngine(dummyContext, storageManager, preferencesRepository, historyRepository)
        assertFalse(engine.isPaused.value)
    }

    @Test
    fun testTogglePauseTogglesStateFlow() {
        val engine = TransferEngine(dummyContext, storageManager, preferencesRepository, historyRepository)
        assertFalse(engine.isPaused.value)

        engine.togglePause()
        assertTrue(engine.isPaused.value)

        engine.togglePause()
        assertFalse(engine.isPaused.value)
    }

    @Test
    fun testAwaitUnpauseBlocksAndResumes() = runBlocking {
        val engine = TransferEngine(dummyContext, storageManager, preferencesRepository, historyRepository)
        engine.togglePause()
        assertTrue(engine.isPaused.value)

        val unpausedSignal = CompletableDeferred<Unit>()

        val waiterJob = async(Dispatchers.IO) {
            engine.awaitUnpause()
            unpausedSignal.complete(Unit)
        }

        delay(50)
        assertFalse("Should still be waiting while paused", unpausedSignal.isCompleted)

        // Now resume
        engine.togglePause()
        assertFalse(engine.isPaused.value)

        withTimeout(2000) {
            waiterJob.await()
            assertTrue(unpausedSignal.isCompleted)
        }
    }

    @Test
    fun testDismissSessionClearsActiveSession() {
        val engine = TransferEngine(dummyContext, storageManager, preferencesRepository, historyRepository)
        assertNull(engine.currentSession.value)
        engine.dismissSession()
        assertNull(engine.currentSession.value)
    }

    @Test
    fun testCancelTransferPreservesCompletedAndFailedState() {
        val engine = TransferEngine(dummyContext, storageManager, preferencesRepository, historyRepository)

        val field = TransferEngine::class.java.getDeclaredField("_currentSession")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val sessionFlow = field.get(engine) as kotlinx.coroutines.flow.MutableStateFlow<TransferSession?>

        // When COMPLETED, cancelTransfer should not overwrite with CANCELLED
        sessionFlow.value = TransferSession(
            sessionId = "s1",
            direction = TransferDirection.SEND,
            state = SessionState.COMPLETED
        )
        engine.cancelTransfer()
        assertEquals(SessionState.COMPLETED, engine.currentSession.value?.state)

        // When FAILED, cancelTransfer should not overwrite with CANCELLED
        sessionFlow.value = TransferSession(
            sessionId = "s2",
            direction = TransferDirection.SEND,
            state = SessionState.FAILED
        )
        engine.cancelTransfer()
        assertEquals(SessionState.FAILED, engine.currentSession.value?.state)

        // When TRANSFERRING, cancelTransfer should cancel
        sessionFlow.value = TransferSession(
            sessionId = "s3",
            direction = TransferDirection.SEND,
            state = SessionState.TRANSFERRING
        )
        engine.cancelTransfer()
        assertEquals(SessionState.CANCELLED, engine.currentSession.value?.state)
    }
}
