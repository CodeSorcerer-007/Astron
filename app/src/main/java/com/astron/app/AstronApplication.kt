package com.astron.app

import android.app.Application
import com.astron.app.core.storage.StorageManager
import com.astron.app.data.HistoryRepository
import com.astron.app.data.PreferencesRepository
import com.astron.app.transfer.TransferEngine

class AstronApplication : Application() {

    lateinit var storageManager: StorageManager
        private set

    lateinit var preferencesRepository: PreferencesRepository
        private set

    lateinit var historyRepository: HistoryRepository
        private set

    lateinit var transferEngine: TransferEngine
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        storageManager = StorageManager(this)
        preferencesRepository = PreferencesRepository(this)
        historyRepository = HistoryRepository(this)
        transferEngine = TransferEngine(
            context = this,
            storageManager = storageManager,
            preferencesRepository = preferencesRepository,
            historyRepository = historyRepository
        )
    }

    companion object {
        lateinit var instance: AstronApplication
            private set
    }
}
