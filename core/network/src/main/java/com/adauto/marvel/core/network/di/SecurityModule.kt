package com.adauto.marvel.core.network.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.adauto.marvel.core.network.integrity.HostileEnvironmentDetector
import com.adauto.marvel.core.network.security.DataStoreEncryptedKeyStorage
import com.adauto.marvel.core.network.security.EncryptedKeyStorage
import com.adauto.marvel.core.network.security.FirebaseRemoteConfigKeySource
import com.adauto.marvel.core.network.security.KeystoreCipher
import com.adauto.marvel.core.network.security.MarvelKeyProvider
import com.adauto.marvel.core.network.security.MarvelKeyProviderImpl
import com.adauto.marvel.core.network.security.RemoteConfigKeySource
import com.adauto.marvel.core.network.security.TmdbApiKeyProvider
import com.adauto.marvel.core.network.security.TmdbApiKeyProviderImpl
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
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
    abstract fun bindMarvelKeyProvider(
        impl: MarvelKeyProviderImpl
    ): MarvelKeyProvider

    @Binds
    @Singleton
    abstract fun bindRemoteConfigKeySource(
        impl: FirebaseRemoteConfigKeySource
    ): RemoteConfigKeySource

    @Binds
    @Singleton
    abstract fun bindTmdbApiKeyProvider(
        impl: TmdbApiKeyProviderImpl
    ): TmdbApiKeyProvider

    companion object {
        @Provides
        @Singleton
        fun provideFirebaseRemoteConfig(): FirebaseRemoteConfig {
            return FirebaseRemoteConfig.getInstance()
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
                produceFile = { context.preferencesDataStoreFile("marvel_keys_storage") }
            )
            return DataStoreEncryptedKeyStorage(dataStore)
        }

        @Provides
        @Singleton
        fun provideHostileEnvironmentDetector(): HostileEnvironmentDetector {
            return HostileEnvironmentDetector()
        }
    }
}
