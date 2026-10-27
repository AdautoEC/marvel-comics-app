package com.adauto.cinemahub.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.adauto.cinemahub.core.database.dao.FavoriteDao
import com.adauto.cinemahub.core.database.dao.MovieDao
import com.adauto.cinemahub.core.database.dao.MovieRemoteKeysDao
import com.adauto.cinemahub.core.database.dao.SyncOutboxDao
import com.adauto.cinemahub.core.database.dao.WatchlistDao
import com.adauto.cinemahub.core.database.entity.FavoriteMovieEntity
import com.adauto.cinemahub.core.database.entity.MovieEntity
import com.adauto.cinemahub.core.database.entity.MovieRemoteKeysEntity
import com.adauto.cinemahub.core.database.entity.SyncOutboxEntity
import com.adauto.cinemahub.core.database.entity.WatchlistMovieEntity

@Database(
    entities = [
        MovieEntity::class,
        FavoriteMovieEntity::class,
        WatchlistMovieEntity::class,
        SyncOutboxEntity::class,
        MovieRemoteKeysEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class CinemaHubDatabase : RoomDatabase() {
    abstract fun movieDao(): MovieDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun syncOutboxDao(): SyncOutboxDao
    abstract fun movieRemoteKeysDao(): MovieRemoteKeysDao
}
