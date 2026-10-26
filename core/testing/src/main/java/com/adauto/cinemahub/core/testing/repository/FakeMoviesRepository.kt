package com.adauto.cinemahub.core.testing.repository

import androidx.paging.PagingData
import com.adauto.cinemahub.core.data.repository.MoviesRepository
import com.adauto.cinemahub.core.model.MovieCategory
import com.adauto.cinemahub.core.model.MovieDetail
import com.adauto.cinemahub.core.model.MovieSummary
import com.adauto.cinemahub.core.model.Outcome
import com.adauto.cinemahub.core.testing.data.MovieTestData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class FakeMoviesRepository : MoviesRepository {

    var moviesList: List<MovieSummary> = emptyList()
    var customMoviesFlow: Flow<PagingData<MovieSummary>>? = null

    val favorites = MutableStateFlow<List<MovieSummary>>(emptyList())
    val watchlist = MutableStateFlow<List<MovieSummary>>(emptyList())

    val movieDetails = mutableMapOf<Int, Outcome<MovieDetail>>()
    var syncCloudResult: Outcome<Unit> = Outcome.Success(Unit)

    override fun movies(category: MovieCategory, query: String): Flow<PagingData<MovieSummary>> {
        val custom = customMoviesFlow
        if (custom != null) return custom

        val filtered = when {
            query.isNotBlank() -> moviesList.filter {
                it.title.contains(query, ignoreCase = true)
            }
            category == MovieCategory.FAVORITES -> favorites.value
            category == MovieCategory.WATCHLIST -> watchlist.value
            else -> moviesList
        }
        return flowOf(PagingData.from(filtered))
    }

    override suspend fun getMovieDetail(id: Int): Outcome<MovieDetail> {
        return movieDetails[id] ?: Outcome.Success(
            MovieTestData.createMovieDetail(
                id = id,
                isFavorite = favorites.value.any { it.id == id },
                isInWatchlist = watchlist.value.any { it.id == id },
            )
        )
    }

    override suspend fun toggleFavorite(movie: MovieSummary) {
        val current = favorites.value.toMutableList()
        val index = current.indexOfFirst { it.id == movie.id }
        if (index >= 0) {
            current.removeAt(index)
        } else {
            current.add(movie.copy(isFavorite = true))
        }
        favorites.value = current
    }

    override suspend fun toggleWatchlist(movie: MovieSummary) {
        val current = watchlist.value.toMutableList()
        val index = current.indexOfFirst { it.id == movie.id }
        if (index >= 0) {
            current.removeAt(index)
        } else {
            current.add(movie.copy(isInWatchlist = true))
        }
        watchlist.value = current
    }

    override fun observeFavorites(): Flow<List<MovieSummary>> = favorites.asStateFlow()

    override fun observeWatchlist(): Flow<List<MovieSummary>> = watchlist.asStateFlow()

    override fun isFavorite(id: Int): Flow<Boolean> = favorites.map { list ->
        list.any { it.id == id }
    }

    override fun isInWatchlist(id: Int): Flow<Boolean> = watchlist.map { list ->
        list.any { it.id == id }
    }

    override suspend fun syncCloudWithLocal(): Outcome<Unit> = syncCloudResult
}
