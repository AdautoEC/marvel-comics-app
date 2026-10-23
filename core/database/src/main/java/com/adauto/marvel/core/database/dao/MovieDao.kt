package com.adauto.marvel.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.adauto.marvel.core.database.entity.MovieEntity

@Dao
interface MovieDao {

    @Upsert
    suspend fun upsertAll(movies: List<MovieEntity>)

    @Query("SELECT * FROM movies WHERE category = :category ORDER BY position ASC")
    fun pagingSource(category: String): PagingSource<Int, MovieEntity>

    @Query("SELECT * FROM movies WHERE id = :id LIMIT 1")
    suspend fun getMovieById(id: Int): MovieEntity?

    @Query("SELECT MAX(position) FROM movies WHERE category = :category")
    suspend fun getLastPosition(category: String): Int?

    @Query("DELETE FROM movies WHERE category = :category")
    suspend fun clearCategory(category: String)

    @Query("SELECT MIN(fetchedAt) FROM movies WHERE category = :category")
    suspend fun lastFetchedAt(category: String): Long?
}
