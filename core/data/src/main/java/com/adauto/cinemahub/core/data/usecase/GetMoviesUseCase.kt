package com.adauto.cinemahub.core.data.usecase

import androidx.paging.PagingData
import androidx.paging.map
import com.adauto.cinemahub.core.data.repository.MoviesRepository
import com.adauto.cinemahub.core.model.MovieCategory
import com.adauto.cinemahub.core.model.MovieSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Resolves the movie source (category, search, favorites, watchlist) and
 * enriches each item with its favorite/watchlist flags.
 */
class GetMoviesUseCase @Inject constructor(
    private val moviesRepository: MoviesRepository
) {
    operator fun invoke(category: MovieCategory, query: String): Flow<PagingData<MovieSummary>> {
        val trimmed = query.trim()
        val source = when (category) {
            MovieCategory.FAVORITES -> moviesRepository.observeFavorites().map { it.filterByTitle(trimmed) }
                .map { PagingData.from(it) }
            MovieCategory.WATCHLIST -> moviesRepository.observeWatchlist().map { it.filterByTitle(trimmed) }
                .map { PagingData.from(it) }
            else -> moviesRepository.movies(category, trimmed)
        }
        return combine(source, favoriteIds(), watchlistIds()) { paging, favIds, watchIds ->
            paging.map { movie ->
                val isFav = movie.id in favIds
                val isWatch = movie.id in watchIds
                if (movie.isFavorite == isFav && movie.isInWatchlist == isWatch) {
                    movie
                } else {
                    movie.copy(isFavorite = isFav, isInWatchlist = isWatch)
                }
            }
        }
    }

    private fun List<MovieSummary>.filterByTitle(query: String): List<MovieSummary> =
        if (query.isBlank()) this else filter { it.title.contains(query, ignoreCase = true) }

    private fun favoriteIds(): Flow<Set<Int>> = moviesRepository.observeFavorites()
        .map { list -> list.mapTo(HashSet()) { it.id } as Set<Int> }
        .distinctUntilChanged()

    private fun watchlistIds(): Flow<Set<Int>> = moviesRepository.observeWatchlist()
        .map { list -> list.mapTo(HashSet()) { it.id } as Set<Int> }
        .distinctUntilChanged()
}
