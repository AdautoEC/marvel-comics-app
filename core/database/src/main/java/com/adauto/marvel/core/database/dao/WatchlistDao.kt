package com.adauto.marvel.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.adauto.marvel.core.database.entity.WatchlistMovieEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchlistDao {

    @Upsert
    suspend fun upsert(watchlistMovie: WatchlistMovieEntity)

    @Query("DELETE FROM watched_movies WHERE movieId = :movieId")
    suspend fun delete(movieId: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM watched_movies WHERE movieId = :movieId)")
    fun isInWatchlist(movieId: Int): Flow<Boolean>

    @Query("SELECT * FROM watched_movies ORDER BY watchedAt DESC")
    fun getAllWatchlist(): Flow<List<WatchlistMovieEntity>>

    @Query("DELETE FROM watched_movies")
    suspend fun clearAll()
}
