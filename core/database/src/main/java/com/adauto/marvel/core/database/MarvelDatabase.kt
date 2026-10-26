package com.adauto.marvel.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.adauto.marvel.core.database.dao.FavoriteDao
import com.adauto.marvel.core.database.dao.MovieDao
import com.adauto.marvel.core.database.dao.MovieRemoteKeysDao
import com.adauto.marvel.core.database.dao.SyncOutboxDao
import com.adauto.marvel.core.database.dao.WatchedDao
import com.adauto.marvel.core.database.entity.FavoriteMovieEntity
import com.adauto.marvel.core.database.entity.MovieEntity
import com.adauto.marvel.core.database.entity.MovieRemoteKeysEntity
import com.adauto.marvel.core.database.entity.SyncOutboxEntity
import com.adauto.marvel.core.database.entity.WatchedMovieEntity

@Database(
    entities = [
        MovieEntity::class,
        FavoriteMovieEntity::class,
        WatchedMovieEntity::class,
        SyncOutboxEntity::class,
        MovieRemoteKeysEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class MarvelDatabase : RoomDatabase() {
    abstract fun movieDao(): MovieDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun watchedDao(): WatchedDao
    abstract fun syncOutboxDao(): SyncOutboxDao
    abstract fun movieRemoteKeysDao(): MovieRemoteKeysDao
}
