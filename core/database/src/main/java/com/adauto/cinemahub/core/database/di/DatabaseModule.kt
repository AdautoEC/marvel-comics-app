package com.adauto.cinemahub.core.database.di

import android.content.Context
import androidx.room.Room
import com.adauto.cinemahub.core.database.CinemaHubDatabase
import com.adauto.cinemahub.core.database.dao.FavoriteDao
import com.adauto.cinemahub.core.database.dao.MovieDao
import com.adauto.cinemahub.core.database.dao.MovieRemoteKeysDao
import com.adauto.cinemahub.core.database.dao.SyncOutboxDao
import com.adauto.cinemahub.core.database.dao.WatchlistDao
import com.adauto.cinemahub.core.database.migrations.MIGRATION_2_3
import com.adauto.cinemahub.core.database.migrations.MIGRATION_3_4
import com.adauto.cinemahub.core.database.migrations.MIGRATION_4_5
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideCinemaHubDatabase(
        @ApplicationContext context: Context
    ): CinemaHubDatabase {
        return Room.databaseBuilder(
            context,
            CinemaHubDatabase::class.java,
            "cinemahub.db"
        )
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .fallbackToDestructiveMigrationFrom(1)
            .build()
    }

    @Provides
    fun provideMovieDao(database: CinemaHubDatabase): MovieDao = database.movieDao()

    @Provides
    fun provideFavoriteDao(database: CinemaHubDatabase): FavoriteDao = database.favoriteDao()

    @Provides
    fun provideWatchlistDao(database: CinemaHubDatabase): WatchlistDao = database.watchlistDao()

    @Provides
    fun provideSyncOutboxDao(database: CinemaHubDatabase): SyncOutboxDao = database.syncOutboxDao()

    @Provides
    fun provideMovieRemoteKeysDao(database: CinemaHubDatabase): MovieRemoteKeysDao = database.movieRemoteKeysDao()
}
