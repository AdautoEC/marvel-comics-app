package com.adauto.marvel.feature.movies

import app.cash.turbine.test
import com.adauto.marvel.core.data.repository.AuthRepository
import com.adauto.marvel.core.data.repository.MoviesRepository
import com.adauto.marvel.core.model.MovieCategory
import com.adauto.marvel.core.model.MovieSummary
import com.adauto.marvel.core.model.SessionState
import com.google.common.truth.Truth.assertThat
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MovieListViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val moviesRepository: MoviesRepository = mockk(relaxed = true)
    private val authRepository: AuthRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { moviesRepository.movies(any()) } returns emptyFlow()
        every { moviesRepository.observeFavorites() } returns emptyFlow()
        every { moviesRepository.observeWatched() } returns emptyFlow()
        every { authRepository.sessionState } returns MutableStateFlow(SessionState.Guest)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialUiState_hasExpectedDefaults() = runTest {
        val viewModel = MovieListViewModel(moviesRepository, authRepository)
        val state = viewModel.uiState.value
        assertThat(state.isOffline).isFalse()
        assertThat(state.isRefreshing).isFalse()
        assertThat(state.selectedCategory).isEqualTo(MovieCategory.POPULAR)
    }

    @Test
    fun categorySelected_updatesUiState() = runTest {
        val viewModel = MovieListViewModel(moviesRepository, authRepository)
        viewModel.onAction(MovieListAction.CategorySelected(MovieCategory.TOP_RATED))
        assertThat(viewModel.uiState.value.selectedCategory).isEqualTo(MovieCategory.TOP_RATED)
    }

    @Test
    fun movieClickedAction_emitsOpenDetailEffect_viaBufferedChannel() = runTest {
        val viewModel = MovieListViewModel(moviesRepository, authRepository)
        viewModel.effects.test {
            viewModel.onAction(MovieListAction.MovieClicked(101))
            val effect = awaitItem()
            assertEquals(MovieListEffect.OpenDetail(101), effect)
        }
    }

    @Test
    fun favoriteToggled_callsRepositoryToggleFavorite() = runTest {
        val viewModel = MovieListViewModel(moviesRepository, authRepository)
        val sampleMovie = MovieSummary(id = 1, title = "Iron Man", posterUrl = null, voteAverage = 8.0)
        viewModel.onAction(MovieListAction.FavoriteToggled(sampleMovie))
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { moviesRepository.toggleFavorite(sampleMovie) }
    }

    @Test
    fun watchedToggled_callsRepositoryToggleWatched() = runTest {
        val viewModel = MovieListViewModel(moviesRepository, authRepository)
        val sampleMovie = MovieSummary(id = 1, title = "Iron Man", posterUrl = null, voteAverage = 8.0)
        viewModel.onAction(MovieListAction.WatchedToggled(sampleMovie))
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { moviesRepository.toggleWatched(sampleMovie) }
    }
}
