package com.adauto.marvel.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.adauto.marvel.core.database.dao.FavoriteDao
import com.adauto.marvel.core.database.dao.MovieDao
import com.adauto.marvel.core.database.dao.WatchedDao
import com.adauto.marvel.core.database.entity.FavoriteMovieEntity
import com.adauto.marvel.core.database.entity.MovieEntity
import com.adauto.marvel.core.database.entity.WatchedMovieEntity

@Database(
    entities = [
        MovieEntity::class,
        FavoriteMovieEntity::class,
        WatchedMovieEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class MarvelDatabase : RoomDatabase() {
    abstract fun movieDao(): MovieDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun watchedDao(): WatchedDao
}
