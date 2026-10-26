package com.adauto.cinemahub.core.network.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.adauto.cinemahub.core.network.integrity.HostileEnvironmentDetector
import com.adauto.cinemahub.core.network.security.DataStoreEncryptedKeyStorage
import com.adauto.cinemahub.core.network.security.EncryptedKeyStorage
import com.adauto.cinemahub.core.network.security.KeystoreCipher
import com.adauto.cinemahub.core.network.security.TmdbApiKeyProvider
import com.adauto.cinemahub.core.network.security.TmdbApiKeyProviderImpl
import com.adauto.cinemahub.core.network.security.session.DataStoreSessionStorage
import com.adauto.cinemahub.core.network.security.session.SessionManager
import com.adauto.cinemahub.core.network.security.session.SessionManagerImpl
import com.adauto.cinemahub.core.network.security.session.SessionStorage
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {

    @Binds
    @Singleton
    abstract fun bindTmdbApiKeyProvider(
        impl: TmdbApiKeyProviderImpl
    ): TmdbApiKeyProvider

    @Binds
    @Singleton
    abstract fun bindSessionManager(
        impl: SessionManagerImpl
    ): SessionManager

    companion object {
        @Provides
        @Singleton
        fun provideFirebaseRemoteConfig(): FirebaseRemoteConfig {
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            val configSettings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(3600L * 12)
                .build()
            remoteConfig.setConfigSettingsAsync(configSettings)
            return remoteConfig
        }

        @Provides
        @Singleton
        fun provideKeystoreCipher(): KeystoreCipher {
            return KeystoreCipher()
        }

        @Provides
        @Singleton
        fun provideEncryptedKeyStorage(
            @ApplicationContext context: Context
        ): EncryptedKeyStorage {
            val dataStore = PreferenceDataStoreFactory.create(
                produceFile = { context.preferencesDataStoreFile("tmdb_keys_storage") }
            )
            return DataStoreEncryptedKeyStorage(dataStore)
        }

        @Provides
        @Singleton
        fun provideSessionStorage(
            @ApplicationContext context: Context
        ): SessionStorage {
            val dataStore = PreferenceDataStoreFactory.create(
                produceFile = { context.preferencesDataStoreFile("tmdb_session_storage") }
            )
            return DataStoreSessionStorage(dataStore)
        }

        @Provides
        @Singleton
        fun provideHostileEnvironmentDetector(): HostileEnvironmentDetector {
            return HostileEnvironmentDetector()
        }
    }
}
