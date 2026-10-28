package com.astron.app

import android.content.ContextWrapper
import com.astron.app.core.model.SessionState
import com.astron.app.core.model.TransferDirection
import com.astron.app.core.model.TransferSession
import com.astron.app.core.storage.StorageManager
import com.astron.app.data.HistoryRepository
import com.astron.app.data.PreferencesRepository
import com.astron.app.transfer.TransferEngine
import com.astron.app.ui.screens.receive.ReceiveViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiveViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val dummyContext = object : ContextWrapper(null) {
        override fun getSystemService(name: String): Any? = null
        override fun getApplicationContext(): android.content.Context = this
        override fun getPackageName(): String = "com.astron.app"
    }
    private val storageManager = StorageManager(dummyContext)
    private val preferencesRepository = PreferencesRepository(dummyContext)
    private val historyRepository = HistoryRepository(dummyContext)
    private var engine: TransferEngine? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        engine?.close()
        engine = null
        Dispatchers.resetMain()
    }

    @Test
    fun testStopReceiverDoesNotCancelActiveTransfer() = runTest {
        val currentEngine = TransferEngine(dummyContext, storageManager, preferencesRepository, historyRepository).also { engine = it }
        val viewModel = ReceiveViewModel(currentEngine, preferencesRepository)

        // Simulate active incoming transfer in progress
        val activeSession = TransferSession(
            sessionId = UUID.randomUUID().toString(),
            direction = TransferDirection.RECEIVE,
            state = SessionState.TRANSFERRING
        )
        val field = TransferEngine::class.java.getDeclaredField("_currentSession")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val sessionFlow = field.get(currentEngine) as MutableStateFlow<TransferSession?>
        sessionFlow.value = activeSession

        // Normal stopReceiver from ReceiveScreen disposal upon navigating to TransferScreen
        viewModel.stopReceiver(force = false)

        // Verify session was NOT cancelled
        assertEquals(SessionState.TRANSFERRING, currentEngine.currentSession.value?.state)
        currentEngine.close()
    }

    @Test
    fun testStopReceiverCancelsWhenForced() = runTest {
        val currentEngine = TransferEngine(dummyContext, storageManager, preferencesRepository, historyRepository).also { engine = it }
        val viewModel = ReceiveViewModel(currentEngine, preferencesRepository)

        val activeSession = TransferSession(
            sessionId = UUID.randomUUID().toString(),
            direction = TransferDirection.RECEIVE,
            state = SessionState.ADVERTISING
        )
        val field = TransferEngine::class.java.getDeclaredField("_currentSession")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val sessionFlow = field.get(currentEngine) as MutableStateFlow<TransferSession?>
        sessionFlow.value = activeSession

        viewModel.stopReceiver(force = true)

        assertEquals(SessionState.CANCELLED, currentEngine.currentSession.value?.state)
        currentEngine.close()
    }

    @Test
    fun testStartReceiverDoesNotResetActiveTransfer() = runTest {
        val currentEngine = TransferEngine(dummyContext, storageManager, preferencesRepository, historyRepository).also { engine = it }
        val viewModel = ReceiveViewModel(currentEngine, preferencesRepository)

        val activeSession = TransferSession(
            sessionId = "existing-session-123",
            direction = TransferDirection.RECEIVE,
            state = SessionState.TRANSFERRING
        )
        val field = TransferEngine::class.java.getDeclaredField("_currentSession")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val sessionFlow = field.get(currentEngine) as MutableStateFlow<TransferSession?>
        sessionFlow.value = activeSession

        viewModel.startReceiver()

        // Session must be preserved
        assertEquals("existing-session-123", currentEngine.currentSession.value?.sessionId)
        assertEquals(SessionState.TRANSFERRING, currentEngine.currentSession.value?.state)
        currentEngine.close()
    }
}
