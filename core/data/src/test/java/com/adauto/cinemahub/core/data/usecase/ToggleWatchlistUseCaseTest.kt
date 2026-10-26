package com.adauto.cinemahub.core.data.usecase

import com.adauto.cinemahub.core.data.repository.MoviesRepository
import com.adauto.cinemahub.core.model.MovieDetail
import com.adauto.cinemahub.core.model.MovieSummary
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ToggleWatchlistUseCaseTest {

    private val moviesRepository: MoviesRepository = mockk(relaxed = true)
    private val useCase = ToggleWatchlistUseCase(moviesRepository)

    @Test
    fun invoke_withMovieSummary_callsRepositoryToggleWatchlist() = runTest {
        val summary = MovieSummary(
            id = 42,
            title = "Inception",
            posterUrl = "/path.jpg",
            voteAverage = 8.8,
            releaseDate = "2010-07-16",
            isInWatchlist = false
        )

        useCase(summary)

        coVerify(exactly = 1) { moviesRepository.toggleWatchlist(summary) }
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

        coVerify(exactly = 1) { moviesRepository.toggleWatchlist(expectedSummary) }
    }
}
