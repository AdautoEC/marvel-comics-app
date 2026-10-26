package com.adauto.cinemahub.feature.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.adauto.cinemahub.core.data.usecase.ToggleFavoriteUseCase
import com.adauto.cinemahub.core.data.usecase.ToggleWatchlistUseCase
import com.adauto.cinemahub.core.model.Outcome
import com.adauto.cinemahub.core.testing.data.MovieTestData
import com.adauto.cinemahub.core.testing.repository.FakeMoviesRepository
import com.adauto.cinemahub.core.testing.util.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MovieDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private lateinit var moviesRepository: FakeMoviesRepository
    private lateinit var toggleFavoriteUseCase: ToggleFavoriteUseCase
    private lateinit var toggleWatchlistUseCase: ToggleWatchlistUseCase
    private val sampleMovie = MovieTestData.createMovieDetail(
        id = 101,
        title = "Inception",
        overview = "Dream within a dream.",
        posterUrl = "https://image.tmdb.org/t/p/w500/inception.jpg",
        voteAverage = 8.8,
        runtime = 148,
        releaseDate = "2010-07-16",
        genres = listOf("Ficção Científica", "Ação"),
        cast = listOf("Leonardo DiCaprio"),
        isFavorite = false,
        isInWatchlist = false,
    )

    @Before
    fun setUp() {
        moviesRepository = FakeMoviesRepository()
        toggleFavoriteUseCase = ToggleFavoriteUseCase(moviesRepository)
        toggleWatchlistUseCase = ToggleWatchlistUseCase(moviesRepository)
        moviesRepository.movieDetails[101] = Outcome.Success(sampleMovie)
    }

    private fun createViewModel(movieId: Int = 101): MovieDetailViewModel {
        return MovieDetailViewModel(
            moviesRepository = moviesRepository,
            toggleFavoriteUseCase = toggleFavoriteUseCase,
            toggleWatchlistUseCase = toggleWatchlistUseCase,
            savedStateHandle = SavedStateHandle(mapOf("movieId" to movieId))
        )
    }

    @Test
    fun loadDetail_onInit_updatesUiStateWithMovie() = runTest {
        val viewModel = createViewModel()

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.movie).isEqualTo(sampleMovie)
        assertThat(state.errorMessage).isNull()
    }

    @Test
    fun backClicked_emitsNavigateBackEffect() = runTest {
        val viewModel = createViewModel()

        viewModel.effects.test {
            viewModel.onAction(MovieDetailAction.BackClicked)
            val effect = awaitItem()
            assertEquals(MovieDetailEffect.NavigateBack, effect)
        }
    }

    @Test
    fun favoriteToggled_callsRepositoryToggleFavorite_andUpdatesState() = runTest {
        val viewModel = createViewModel()

        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAction(MovieDetailAction.FavoriteToggled)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.movie?.isFavorite).isTrue()
        // Initial repository map wasn't mutated directly
        val initialDetail = moviesRepository.movieDetails[101]?.getOrNull()
        assertThat(initialDetail?.isFavorite).isFalse()
    }

    @Test
    fun watchlistToggled_callsRepositoryToggleWatchlist_andUpdatesState() = runTest {
        val viewModel = createViewModel()

        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAction(MovieDetailAction.WatchlistToggled)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.movie?.isInWatchlist).isTrue()
    }
}
