package com.adauto.marvel.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.adauto.marvel.core.database.entity.MovieEntity
import com.adauto.marvel.core.database.model.MovieWithUserData

@Dao
interface MovieDao {

    @Upsert
    suspend fun upsertAll(movies: List<MovieEntity>)

    @Query("""
        SELECT m.*, 
               (f.movieId IS NOT NULL) AS isFavorite, 
               (w.movieId IS NOT NULL) AS isWatched
        FROM movies m
        LEFT JOIN favorite_movies f ON m.id = f.movieId
        LEFT JOIN watched_movies w ON m.id = w.movieId
        WHERE m.category = :category
        ORDER BY m.position ASC
    """)
    fun pagingSource(category: String): PagingSource<Int, MovieWithUserData>

    @Query("""
        SELECT m.*, 
               (f.movieId IS NOT NULL) AS isFavorite, 
               (w.movieId IS NOT NULL) AS isWatched
        FROM movies m
        LEFT JOIN favorite_movies f ON m.id = f.movieId
        LEFT JOIN watched_movies w ON m.id = w.movieId
        WHERE m.category = :category AND m.title LIKE '%' || :query || '%'
        ORDER BY m.position ASC
    """)
    fun searchPagingSource(category: String, query: String): PagingSource<Int, MovieWithUserData>

    @Query("SELECT * FROM movies WHERE id = :id LIMIT 1")
    suspend fun getMovieById(id: Int): MovieEntity?

    @Query("SELECT MAX(position) FROM movies WHERE category = :category")
    suspend fun getLastPosition(category: String): Int?

    @Query("DELETE FROM movies WHERE category = :category")
    suspend fun clearCategory(category: String)

    @Query("SELECT MIN(fetchedAt) FROM movies WHERE category = :category")
    suspend fun lastFetchedAt(category: String): Long?
}
