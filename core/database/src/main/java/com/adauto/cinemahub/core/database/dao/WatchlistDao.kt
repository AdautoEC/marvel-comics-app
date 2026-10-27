package com.adauto.cinemahub.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.adauto.cinemahub.core.database.entity.WatchlistMovieEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchlistDao {

    @Upsert
    suspend fun upsert(watchlistMovie: WatchlistMovieEntity)

    @Query("DELETE FROM watchlist_movies WHERE movieId = :movieId")
    suspend fun delete(movieId: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist_movies WHERE movieId = :movieId)")
    fun isInWatchlist(movieId: Int): Flow<Boolean>

    @Query("SELECT * FROM watchlist_movies ORDER BY addedAt DESC")
    fun getAllWatchlist(): Flow<List<WatchlistMovieEntity>>

    @Query("DELETE FROM watchlist_movies")
    suspend fun clearAll()
}
