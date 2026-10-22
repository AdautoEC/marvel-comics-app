package com.adauto.marvel.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.adauto.marvel.core.database.entity.WatchedMovieEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchedDao {

    @Upsert
    suspend fun upsert(watched: WatchedMovieEntity)

    @Query("DELETE FROM watched_movies WHERE movieId = :movieId")
    suspend fun delete(movieId: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM watched_movies WHERE movieId = :movieId)")
    fun isWatched(movieId: Int): Flow<Boolean>

    @Query("SELECT * FROM watched_movies ORDER BY watchedAt DESC")
    fun getAllWatched(): Flow<List<WatchedMovieEntity>>
}
