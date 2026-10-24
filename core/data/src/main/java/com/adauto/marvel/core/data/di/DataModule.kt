package com.adauto.marvel.core.data.di

import com.adauto.marvel.core.data.repository.AuthRepository
import com.adauto.marvel.core.data.repository.AuthRepositoryImpl
import com.adauto.marvel.core.data.repository.MoviesRepository
import com.adauto.marvel.core.data.repository.MoviesRepositoryImpl
import com.adauto.marvel.core.data.util.ConnectivityManagerNetworkMonitor
import com.adauto.marvel.core.data.util.NetworkMonitor
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
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
    }
}
