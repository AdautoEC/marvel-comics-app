package com.adauto.cinemahub.core.data.usecase

import com.adauto.cinemahub.core.data.repository.MoviesRepository
import com.adauto.cinemahub.core.model.MovieDetail
import com.adauto.cinemahub.core.model.MovieSummary
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ToggleFavoriteUseCaseTest {

    private val moviesRepository: MoviesRepository = mockk(relaxed = true)
    private val useCase = ToggleFavoriteUseCase(moviesRepository)

    @Test
    fun invoke_withMovieSummary_callsRepositoryToggleFavorite() = runTest {
        val summary = MovieSummary(
            id = 42,
            title = "Inception",
            posterUrl = "/path.jpg",
            voteAverage = 8.8,
            releaseDate = "2010-07-16",
            isFavorite = false
        )

        useCase(summary)

        coVerify(exactly = 1) { moviesRepository.toggleFavorite(summary) }
    }

    @Test
    fun invoke_withMovieDetail_mapsToSummaryAndCallsRepository() = runTest {
        val detail = MovieDetail(
            id = 42,
            title = "Inception",
            overview = "Dream within a dream",
            posterUrl = "/path.jpg",
            backdropUrl = "/backdrop.jpg",
            voteAverage = 8.8,
            releaseDate = "2010-07-16",
            runtime = 148,
            genres = listOf("Sci-Fi", "Action"),
            cast = emptyList(),
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

        coVerify(exactly = 1) { moviesRepository.toggleFavorite(expectedSummary) }
    }
}
