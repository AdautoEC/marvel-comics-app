package com.adauto.marvel.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.adauto.marvel.core.database.dao.ComicDao
import com.adauto.marvel.core.database.dao.RemoteKeyDao
import com.adauto.marvel.core.database.entity.ComicEntity
import com.adauto.marvel.core.database.entity.RemoteKeyEntity

@Database(
    entities = [ComicEntity::class, RemoteKeyEntity::class],
    version = 1,
    exportSchema = true
)
abstract class MarvelDatabase : RoomDatabase() {
    abstract fun comicDao(): ComicDao
    abstract fun remoteKeyDao(): RemoteKeyDao
}
