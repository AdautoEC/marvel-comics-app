package com.adauto.marvel.feature.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.adauto.marvel.core.data.repository.MoviesRepository
import com.adauto.marvel.core.model.MovieDetail
import com.adauto.marvel.core.model.Outcome
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MovieDetailViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val moviesRepository: MoviesRepository = mockk(relaxed = true)

    private val sampleMovie = MovieDetail(
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
        isWatched = false
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { moviesRepository.getMovieDetail(101) } returns Outcome.Success(sampleMovie)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadDetail_onInit_updatesUiStateWithMovie() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("movieId" to 101))
        val viewModel = MovieDetailViewModel(moviesRepository, savedStateHandle)

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.movie).isEqualTo(sampleMovie)
        assertThat(state.errorMessage).isNull()
    }

    @Test
    fun backClicked_emitsNavigateBackEffect() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("movieId" to 101))
        val viewModel = MovieDetailViewModel(moviesRepository, savedStateHandle)

        viewModel.effects.test {
            viewModel.onAction(MovieDetailAction.BackClicked)
            val effect = awaitItem()
            assertEquals(MovieDetailEffect.NavigateBack, effect)
        }
    }

    @Test
    fun favoriteToggled_callsRepositoryToggleFavorite_andUpdatesState() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("movieId" to 101))
        val viewModel = MovieDetailViewModel(moviesRepository, savedStateHandle)

        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAction(MovieDetailAction.FavoriteToggled)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { moviesRepository.toggleFavorite(any()) }
        assertThat(viewModel.uiState.value.movie?.isFavorite).isTrue()
    }

    @Test
    fun watchlistToggled_callsRepositoryToggleWatchlist_andUpdatesState() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("movieId" to 101))
        val viewModel = MovieDetailViewModel(moviesRepository, savedStateHandle)

        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAction(MovieDetailAction.WatchlistToggled)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { moviesRepository.toggleWatchlist(any()) }
        assertThat(viewModel.uiState.value.movie?.isWatched).isTrue()
    }

    @Test
    fun watchedToggled_callsRepositoryToggleWatchlist_andUpdatesState() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("movieId" to 101))
        val viewModel = MovieDetailViewModel(moviesRepository, savedStateHandle)

        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAction(MovieDetailAction.WatchedToggled)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { moviesRepository.toggleWatchlist(any()) }
        assertThat(viewModel.uiState.value.movie?.isWatched).isTrue()
    }
}
