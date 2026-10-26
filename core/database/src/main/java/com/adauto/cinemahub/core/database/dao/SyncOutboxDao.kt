package com.adauto.cinemahub.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.adauto.cinemahub.core.database.entity.SyncOutboxEntity

@Dao
interface SyncOutboxDao {
    @Query("SELECT * FROM sync_outbox WHERE movieId = :movieId AND target = :target")
    suspend fun find(movieId: Int, target: String): SyncOutboxEntity?

    @Upsert
    suspend fun upsert(entity: SyncOutboxEntity)

    @Query("DELETE FROM sync_outbox WHERE movieId = :movieId AND target = :target AND version = :version")
    suspend fun deleteVersion(movieId: Int, target: String, version: Int): Int

    @Query(
        "UPDATE sync_outbox SET attempts = attempts + 1, lastError = :error " +
            "WHERE movieId = :movieId AND target = :target AND version = :version"
    )
    suspend fun updateFailure(movieId: Int, target: String, version: Int, error: String?): Int

    @Query("SELECT * FROM sync_outbox ORDER BY createdAt ASC, version ASC")
    suspend fun getAllPending(): List<SyncOutboxEntity>

    @Query("SELECT movieId FROM sync_outbox WHERE target = :target")
    suspend fun getPendingMovieIds(target: String): List<Int>

    @Query("DELETE FROM sync_outbox")
    suspend fun clearAll()
}
