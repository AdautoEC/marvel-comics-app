package com.adauto.marvel.core.database.di

import android.content.Context
import androidx.room.Room
import com.adauto.marvel.core.database.MarvelDatabase
import com.adauto.marvel.core.database.dao.FavoriteDao
import com.adauto.marvel.core.database.dao.MovieDao
import com.adauto.marvel.core.database.dao.MovieRemoteKeysDao
import com.adauto.marvel.core.database.dao.SyncOutboxDao
import com.adauto.marvel.core.database.dao.WatchedDao
import com.adauto.marvel.core.database.migrations.MIGRATION_2_3
import com.adauto.marvel.core.database.migrations.MIGRATION_3_4
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
    fun provideMarvelDatabase(
        @ApplicationContext context: Context
    ): MarvelDatabase {
        return Room.databaseBuilder(
            context,
            MarvelDatabase::class.java,
            "marvel.db"
        )
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
            .fallbackToDestructiveMigrationFrom(1)
            .build()
    }

    @Provides
    fun provideMovieDao(database: MarvelDatabase): MovieDao = database.movieDao()

    @Provides
    fun provideFavoriteDao(database: MarvelDatabase): FavoriteDao = database.favoriteDao()

    @Provides
    fun provideWatchedDao(database: MarvelDatabase): WatchedDao = database.watchedDao()

    @Provides
    fun provideSyncOutboxDao(database: MarvelDatabase): SyncOutboxDao = database.syncOutboxDao()

    @Provides
    fun provideMovieRemoteKeysDao(database: MarvelDatabase): MovieRemoteKeysDao = database.movieRemoteKeysDao()
}
