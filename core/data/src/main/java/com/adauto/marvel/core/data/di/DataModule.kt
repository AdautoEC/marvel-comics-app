package com.adauto.marvel.core.data.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.work.WorkManager
import com.adauto.marvel.core.data.repository.AuthRepository
import com.adauto.marvel.core.data.repository.AuthRepositoryImpl
import com.adauto.marvel.core.data.repository.MoviesRepository
import com.adauto.marvel.core.data.repository.MoviesRepositoryImpl
import com.adauto.marvel.core.data.storage.AuthPreferencesStorage
import com.adauto.marvel.core.data.storage.DataStoreAuthPreferencesStorage
import com.adauto.marvel.core.data.storage.DataStoreSyncFailureStorage
import com.adauto.marvel.core.data.storage.SyncFailureStorage
import com.adauto.marvel.core.data.util.ConnectivityManagerNetworkMonitor
import com.adauto.marvel.core.data.util.NetworkMonitor
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface DataModule {

    @Binds
    @Singleton
    fun bindMoviesRepository(impl: MoviesRepositoryImpl): MoviesRepository

    @Binds
    @Singleton
    fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    fun bindNetworkMonitor(impl: ConnectivityManagerNetworkMonitor): NetworkMonitor

    companion object {
        @Provides
        @Singleton
        fun provideClock(): Clock = Clock.systemUTC()

        @Provides
        @Singleton
        fun provideWorkManager(
            @ApplicationContext context: Context
        ): WorkManager = WorkManager.getInstance(context)

        @Provides
        @Singleton
        fun provideAuthPreferencesStorage(
            @ApplicationContext context: Context
        ): AuthPreferencesStorage {
            val dataStore = PreferenceDataStoreFactory.create(
                produceFile = { context.preferencesDataStoreFile("auth_preferences") }
            )
            return DataStoreAuthPreferencesStorage(dataStore)
        }

        @Provides
        @Singleton
        fun provideSyncFailureStorage(
            @ApplicationContext context: Context
        ): SyncFailureStorage {
            val dataStore = PreferenceDataStoreFactory.create(
                produceFile = { context.preferencesDataStoreFile("sync_failures_preferences") }
            )
            return DataStoreSyncFailureStorage(dataStore)
        }
    }
}
