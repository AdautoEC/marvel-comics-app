package com.adauto.cinemahub.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.adauto.cinemahub.core.database.entity.MovieRemoteKeysEntity

@Dao
interface MovieRemoteKeysDao {
    @Query("SELECT * FROM movie_remote_keys WHERE label = :label")
    suspend fun getKey(label: String): MovieRemoteKeysEntity?

    @Upsert
    suspend fun insertOrReplace(key: MovieRemoteKeysEntity)

    @Query("DELETE FROM movie_remote_keys WHERE label = :label")
    suspend fun deleteKey(label: String)

    @Query("DELETE FROM movie_remote_keys")
    suspend fun clearAll()
}
