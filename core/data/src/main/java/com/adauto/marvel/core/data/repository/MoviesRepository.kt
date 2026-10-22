package com.adauto.marvel.core.data.repository

import androidx.paging.PagingData
import com.adauto.marvel.core.model.MovieCategory
import com.adauto.marvel.core.model.MovieDetail
import com.adauto.marvel.core.model.MovieSummary
import com.adauto.marvel.core.model.Outcome
import kotlinx.coroutines.flow.Flow

interface MoviesRepository {
    fun movies(category: MovieCategory): Flow<PagingData<MovieSummary>>
    suspend fun getMovieDetail(id: Int): Outcome<MovieDetail>
    suspend fun toggleFavorite(movie: MovieSummary)
    suspend fun toggleWatchlist(movie: MovieSummary)
    suspend fun toggleWatched(movie: MovieSummary)
    fun observeFavorites(): Flow<List<MovieSummary>>
    fun observeWatched(): Flow<List<MovieSummary>>
    fun isFavorite(id: Int): Flow<Boolean>
    fun isWatched(id: Int): Flow<Boolean>
    suspend fun syncCloudWithLocal(): Outcome<Unit>
}
