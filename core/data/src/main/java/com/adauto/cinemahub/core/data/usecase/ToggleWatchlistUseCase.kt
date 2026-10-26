package com.adauto.cinemahub.core.data.usecase

import com.adauto.cinemahub.core.data.repository.MoviesRepository
import com.adauto.cinemahub.core.model.MovieDetail
import com.adauto.cinemahub.core.model.MovieSummary
import javax.inject.Inject

class ToggleWatchlistUseCase @Inject constructor(
    private val moviesRepository: MoviesRepository
) {
    suspend operator fun invoke(movie: MovieSummary) {
        moviesRepository.toggleWatchlist(movie)
    }

    suspend operator fun invoke(detail: MovieDetail) {
        val summary = MovieSummary(
            id = detail.id,
            title = detail.title,
            posterUrl = detail.posterUrl,
            voteAverage = detail.voteAverage,
            releaseDate = detail.releaseDate,
            isFavorite = detail.isFavorite,
            isInWatchlist = detail.isInWatchlist
        )
        moviesRepository.toggleWatchlist(summary)
    }
}
