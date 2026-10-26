package com.adauto.cinemahub.core.data.repository

import androidx.paging.PagingData
import com.adauto.cinemahub.core.model.MovieCategory
import com.adauto.cinemahub.core.model.MovieDetail
import com.adauto.cinemahub.core.model.MovieSummary
import com.adauto.cinemahub.core.model.Outcome
import kotlinx.coroutines.flow.Flow

interface MoviesRepository {
    fun movies(category: MovieCategory, query: String = ""): Flow<PagingData<MovieSummary>>
    suspend fun getMovieDetail(id: Int): Outcome<MovieDetail>
    suspend fun toggleFavorite(movie: MovieSummary)
    suspend fun toggleWatchlist(movie: MovieSummary)
    fun observeFavorites(): Flow<List<MovieSummary>>
    fun observeWatchlist(): Flow<List<MovieSummary>>
    fun isFavorite(id: Int): Flow<Boolean>
    fun isInWatchlist(id: Int): Flow<Boolean>
    suspend fun syncCloudWithLocal(): Outcome<Unit>
}
