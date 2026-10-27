package com.adauto.cinemahub.core.data.usecase

import com.adauto.cinemahub.core.model.MovieDetail
import com.adauto.cinemahub.core.model.MovieSummary
import com.adauto.cinemahub.core.testing.repository.FakeMoviesRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ToggleWatchlistUseCaseTest {

    private val moviesRepository = FakeMoviesRepository()
    private val useCase = ToggleWatchlistUseCase(moviesRepository)

    @Test
    fun invoke_withMovieSummary_togglesWatchlistStateInRepository() = runTest {
        val summary = MovieSummary(
            id = 42,
            title = "Inception",
            posterUrl = "/path.jpg",
            voteAverage = 8.8,
            releaseDate = "2010-07-16",
            isInWatchlist = false
        )

        useCase(summary)

        assertThat(moviesRepository.toggledWatchlist).containsExactly(summary)
        assertThat(moviesRepository.watchlist.value).containsExactly(summary.copy(isInWatchlist = true))
    }

    @Test
    fun invoke_withMovieDetail_mapsToSummaryAndTogglesWatchlistStateInRepository() = runTest {
        val detail = MovieDetail(
            id = 42,
            title = "Inception",
            overview = "Dream within a dream",
            posterUrl = "/path.jpg",
            backdropUrl = "/backdrop.jpg",
            voteAverage = 8.8,
            releaseDate = "2010-07-16",
            runtime = 148,
            genres = persistentListOf("Sci-Fi", "Action"),
            cast = persistentListOf(),
            isFavorite = true,
            isInWatchlist = false
        )

        useCase(detail)

        val expectedSummary = MovieSummary(
            id = 42,
            title = "Inception",
            posterUrl = "/path.jpg",
            voteAverage = 8.8,
            releaseDate = "2010-07-16",
            isFavorite = true,
            isInWatchlist = false
        )

        assertThat(moviesRepository.toggledWatchlist).containsExactly(expectedSummary)
        assertThat(moviesRepository.watchlist.value).containsExactly(expectedSummary.copy(isInWatchlist = true))
    }
}
