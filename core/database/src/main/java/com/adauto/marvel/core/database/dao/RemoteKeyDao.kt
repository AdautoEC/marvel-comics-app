package com.adauto.marvel.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.adauto.marvel.core.database.entity.RemoteKeyEntity

@Dao
interface RemoteKeyDao {
    @Upsert
    suspend fun upsert(keys: List<RemoteKeyEntity>)

    @Query("SELECT nextOffset FROM remote_keys ORDER BY nextOffset DESC LIMIT 1")
    suspend fun lastNextOffset(): Int?

    @Query("DELETE FROM remote_keys")
    suspend fun clearAll()
}
