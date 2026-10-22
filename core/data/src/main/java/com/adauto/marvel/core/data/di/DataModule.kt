package com.adauto.marvel.core.data.di

import com.adauto.marvel.core.data.repository.ComicsRepository
import com.adauto.marvel.core.data.repository.ComicsRepositoryImpl
import com.adauto.marvel.core.data.repository.MoviesRepository
import com.adauto.marvel.core.data.repository.MoviesRepositoryImpl
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
    fun bindComicsRepository(impl: ComicsRepositoryImpl): ComicsRepository

    @Binds
    @Singleton
    fun bindMoviesRepository(impl: MoviesRepositoryImpl): MoviesRepository

    companion object {
        @Provides
        @Singleton
        fun provideClock(): Clock = Clock.systemUTC()
    }
}
