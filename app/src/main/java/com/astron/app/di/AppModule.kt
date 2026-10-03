package com.astron.app.di

import android.content.Context
import com.astron.app.core.storage.StorageManager
import com.astron.app.data.HistoryRepository
import com.astron.app.data.PreferencesRepository
import com.astron.app.transfer.TransferEngine
import com.astron.app.transport.lan.LanTransport
import com.astron.app.transport.lightdrop.LightDropManager
import com.astron.app.transport.webdrop.WebDropServer
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
    fun provideTransferEngine(
        @ApplicationContext context: Context,
        storageManager: StorageManager,
        preferencesRepository: PreferencesRepository,
        historyRepository: HistoryRepository,
        lanTransport: LanTransport,
        webDropServer: WebDropServer,
        lightDropManager: LightDropManager
    ): TransferEngine = TransferEngine(
        context = context,
        storageManager = storageManager,
        preferencesRepository = preferencesRepository,
        historyRepository = historyRepository,
        lanTransport = lanTransport,
        webDropServer = webDropServer,
        lightDropManager = lightDropManager
    )
}
