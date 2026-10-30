package com.adauto.cinemahub.core.data.usecase

import com.adauto.cinemahub.core.data.mapper.toMovieSummary
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
        moviesRepository.toggleWatchlist(detail.toMovieSummary())
    }
}
