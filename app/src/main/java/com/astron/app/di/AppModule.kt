package com.astron.app.di

import android.content.Context
import com.astron.app.core.storage.StorageManager
import com.astron.app.data.HistoryRepository
import com.astron.app.data.PreferencesRepository
import com.astron.app.transfer.TransferEngine
import com.astron.app.transport.lan.LanTransport
import com.astron.app.transport.lightdrop.LightDropManager
import com.astron.app.transport.webdrop.WebDropServer
import androidx.room.Room
import com.astron.app.data.db.AstronDatabase
import com.astron.app.data.db.TransferDao
import com.astron.app.data.db.TransferPersistenceManager
import com.astron.app.transport.CapabilityDetector
import com.astron.app.transport.TransportOrchestrator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAstronDatabase(
        @ApplicationContext context: Context
    ): AstronDatabase = Room.databaseBuilder(
        context,
        AstronDatabase::class.java,
        AstronDatabase.DATABASE_NAME
    ).fallbackToDestructiveMigration(true).build()

    @Provides
    @Singleton
    fun provideTransferDao(
        database: AstronDatabase
    ): TransferDao = database.transferDao()

    @Provides
    @Singleton
    fun provideCapabilityDetector(
        @ApplicationContext context: Context
    ): CapabilityDetector = CapabilityDetector

    @Provides
    @Singleton
    fun provideTransportOrchestrator(
        @ApplicationContext context: Context
    ): TransportOrchestrator = TransportOrchestrator(context)

    @Provides
    @Singleton
    fun provideStorageManager(
        @ApplicationContext context: Context
    ): StorageManager = StorageManager(context)

    @Provides
    @Singleton
    fun providePreferencesRepository(
        @ApplicationContext context: Context
    ): PreferencesRepository = PreferencesRepository(context)

    @Provides
    @Singleton
    fun provideHistoryRepository(
        @ApplicationContext context: Context
    ): HistoryRepository = HistoryRepository(context)

    @Provides
    @Singleton
    fun provideLanTransport(
        @ApplicationContext context: Context,
        storageManager: StorageManager
    ): LanTransport = LanTransport(context, storageManager)

    @Provides
    @Singleton
    fun provideWebDropServer(
        @ApplicationContext context: Context,
        storageManager: StorageManager
    ): WebDropServer = WebDropServer(context, storageManager)

    @Provides
    @Singleton
    fun provideLightDropManager(): LightDropManager = LightDropManager()

    @Provides
    @Singleton
    fun provideHapticFeedbackManager(
        @ApplicationContext context: Context
    ): com.astron.app.core.util.HapticFeedbackManager =
        com.astron.app.core.util.HapticFeedbackManager(context)

    @Provides
    @Singleton
    fun provideWifiDirectManager(
        @ApplicationContext context: Context
    ): com.astron.app.transport.p2p.WifiDirectManager =
        com.astron.app.transport.p2p.WifiDirectManager(context)

    @Provides
    @Singleton
    fun provideHotspotManager(
        @ApplicationContext context: Context
    ): com.astron.app.transport.hotspot.HotspotManager =
        com.astron.app.transport.hotspot.HotspotManager(context)

    @Provides
    @Singleton
    fun provideBluetoothClassicTransport(
        @ApplicationContext context: Context
    ): com.astron.app.transport.bluetooth.BluetoothClassicTransport =
        com.astron.app.transport.bluetooth.BluetoothClassicTransport(context)

    @Provides
    @Singleton
    fun provideInternetRelayTransport(
        @ApplicationContext context: Context,
        storageManager: StorageManager
    ): com.astron.app.transport.internet.InternetRelayTransport =
        com.astron.app.transport.internet.InternetRelayTransport(context, storageManager)

    @Provides
    @Singleton
    fun provideDuplicateDetector(
        storageManager: StorageManager
    ): com.astron.app.core.storage.DuplicateDetector =
        com.astron.app.core.storage.DuplicateDetector(storageManager)

    @Provides
    @Singleton
    fun provideTransferQueue(): com.astron.app.transfer.TransferQueue =
        com.astron.app.transfer.TransferQueue()

    @Provides
    @Singleton
    fun provideFileBrowserManager(
        @ApplicationContext context: Context
    ): com.astron.app.core.storage.FileBrowserManager =
        com.astron.app.core.storage.FileBrowserManager(context)

    @Provides
    @Singleton
    fun provideArchiveManager(
        @ApplicationContext context: Context
    ): com.astron.app.core.archive.ArchiveManager =
        com.astron.app.core.archive.ArchiveManager(context)

    @Provides
    @Singleton
    fun provideTransferEngine(
        @ApplicationContext context: Context,
        storageManager: StorageManager,
        preferencesRepository: PreferencesRepository,
        historyRepository: HistoryRepository,
        lanTransport: LanTransport,
        webDropServer: WebDropServer,
        lightDropManager: LightDropManager,
        hotspotManager: com.astron.app.transport.hotspot.HotspotManager,
        bluetoothTransport: com.astron.app.transport.bluetooth.BluetoothClassicTransport,
        internetRelayTransport: com.astron.app.transport.internet.InternetRelayTransport,
        transportOrchestrator: TransportOrchestrator,
        persistenceManager: TransferPersistenceManager,
        duplicateDetector: com.astron.app.core.storage.DuplicateDetector,
        transferQueue: com.astron.app.transfer.TransferQueue
    ): TransferEngine = TransferEngine(
        context = context,
        storageManager = storageManager,
        preferencesRepository = preferencesRepository,
        historyRepository = historyRepository,
        lanTransport = lanTransport,
        webDropServer = webDropServer,
        lightDropManager = lightDropManager,
        hotspotManager = hotspotManager,
        bluetoothTransport = bluetoothTransport,
        internetRelayTransport = internetRelayTransport,
        transportOrchestrator = transportOrchestrator,
        persistenceManager = persistenceManager,
        duplicateDetector = duplicateDetector,
        transferQueue = transferQueue
    )
}


