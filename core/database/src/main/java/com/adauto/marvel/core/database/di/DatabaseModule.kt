package com.adauto.marvel.core.database.di

import android.content.Context
import androidx.room.Room
import com.adauto.marvel.core.database.MarvelDatabase
import com.adauto.marvel.core.database.dao.ComicDao
import com.adauto.marvel.core.database.dao.RemoteKeyDao
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
        ).build()
    }

    @Provides
    fun provideComicDao(database: MarvelDatabase): ComicDao = database.comicDao()

    @Provides
    fun provideRemoteKeyDao(database: MarvelDatabase): RemoteKeyDao = database.remoteKeyDao()
}
