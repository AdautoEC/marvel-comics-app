package com.adauto.cinemahub.core.data.usecase

import com.adauto.cinemahub.core.model.MovieDetail
import com.adauto.cinemahub.core.model.MovieSummary
import com.adauto.cinemahub.core.testing.repository.FakeMoviesRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ToggleFavoriteUseCaseTest {

    private val moviesRepository = FakeMoviesRepository()
    private val useCase = ToggleFavoriteUseCase(moviesRepository)

    @Test
    fun invoke_withMovieSummary_togglesFavoriteStateInRepository() = runTest {
        val summary = MovieSummary(
            id = 42,
            title = "Inception",
            posterUrl = "/path.jpg",
            voteAverage = 8.8,
            releaseDate = "2010-07-16",
            isFavorite = false
        )

        useCase(summary)

        assertThat(moviesRepository.toggledFavorites).containsExactly(summary)
        assertThat(moviesRepository.favorites.value).containsExactly(summary.copy(isFavorite = true))
    }

    @Test
    fun invoke_withMovieDetail_mapsToSummaryAndTogglesFavoriteStateInRepository() = runTest {
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
            isFavorite = false,
            isInWatchlist = true
        )

        useCase(detail)

        val expectedSummary = MovieSummary(
            id = 42,
            title = "Inception",
            posterUrl = "/path.jpg",
            voteAverage = 8.8,
            releaseDate = "2010-07-16",
            isFavorite = false,
            isInWatchlist = true
        )

        assertThat(moviesRepository.toggledFavorites).containsExactly(expectedSummary)
        assertThat(moviesRepository.favorites.value).containsExactly(expectedSummary.copy(isFavorite = true))
    }
}
