package com.adauto.marvel.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.adauto.marvel.core.database.entity.RemoteKeyEntity

@Dao
interface RemoteKeyDao {
    @Upsert
    suspend fun upsert(keys: List<RemoteKeyEntity>)

    @Query("SELECT remote_keys.nextOffset FROM remote_keys INNER JOIN comics ON remote_keys.comicId = comics.id ORDER BY comics.position DESC LIMIT 1")
    suspend fun lastNextOffset(): Int?

    @Query("DELETE FROM remote_keys")
    suspend fun clearAll()
}
