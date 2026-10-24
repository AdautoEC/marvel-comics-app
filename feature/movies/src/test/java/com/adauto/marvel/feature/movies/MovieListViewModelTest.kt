package com.adauto.marvel.feature.movies

import androidx.paging.PagingData
import androidx.paging.testing.asSnapshot
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
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

    @Test
    fun refreshRequested_togglesIsRefreshingState() = runTest {
        val viewModel = MovieListViewModel(moviesRepository, authRepository)
        viewModel.uiState.test {
            // Initial state: not refreshing
            val initial = awaitItem()
            assertThat(initial.isRefreshing).isFalse()

            // Trigger refresh
            viewModel.onAction(MovieListAction.RefreshRequested)

            // Should transition to refreshing true
            val refreshing = awaitItem()
            assertThat(refreshing.isRefreshing).isTrue()

            // Advance scheduler until coroutine completes
            testDispatcher.scheduler.advanceUntilIdle()

            // Should reset to refreshing false
            val finished = awaitItem()
            assertThat(finished.isRefreshing).isFalse()
        }
    }

    @Test
    fun refreshRequested_whenAuthenticated_callsSyncCloudWithLocal() = runTest {
        val authState = MutableStateFlow<SessionState>(
            SessionState.Authenticated(accountId = 42, username = "adauto", sessionId = "sess_123")
        )
        every { authRepository.sessionState } returns authState
        val viewModel = MovieListViewModel(moviesRepository, authRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAction(MovieListAction.RefreshRequested)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { moviesRepository.syncCloudWithLocal() }
    }

    @Test
    fun refreshRequested_whenAuthenticated_triggersSyncAndResetsIsRefreshingWithoutDelay() = runTest {
        val authState = MutableStateFlow<SessionState>(
            SessionState.Authenticated(accountId = 42, username = "adauto", sessionId = "sess_123")
        )
        every { authRepository.sessionState } returns authState
        val viewModel = MovieListViewModel(moviesRepository, authRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val initial = awaitItem()
            assertThat(initial.isRefreshing).isFalse()

            viewModel.onAction(MovieListAction.RefreshRequested)

            val refreshing = awaitItem()
            assertThat(refreshing.isRefreshing).isTrue()

            testDispatcher.scheduler.advanceUntilIdle()

            val finished = awaitItem()
            assertThat(finished.isRefreshing).isFalse()
        }

        coVerify(exactly = 1) { moviesRepository.syncCloudWithLocal() }
        assertThat(testScheduler.currentTime).isEqualTo(0L)
    }

    @Test
    fun searchQueryChanged_updatesUiStateSearchQuery() = runTest {
        val viewModel = MovieListViewModel(moviesRepository, authRepository)
        viewModel.onAction(MovieListAction.SearchQueryChanged("Duna"))
        assertThat(viewModel.uiState.value.searchQuery).isEqualTo("Duna")
    }

    @Test
    fun toggleSearchClicked_togglesSearchActiveStateAndClearsQueryWhenDeactivating() = runTest {
        val viewModel = MovieListViewModel(moviesRepository, authRepository)

        // Initial: inactive
        assertThat(viewModel.uiState.value.isSearchActive).isFalse()

        // Toggle on
        viewModel.onAction(MovieListAction.ToggleSearchClicked)
        assertThat(viewModel.uiState.value.isSearchActive).isTrue()

        // Set query
        viewModel.onAction(MovieListAction.SearchQueryChanged("Duna"))
        assertThat(viewModel.uiState.value.searchQuery).isEqualTo("Duna")

        // Toggle off: should deactivate and reset query
        viewModel.onAction(MovieListAction.ToggleSearchClicked)
        assertThat(viewModel.uiState.value.isSearchActive).isFalse()
        assertThat(viewModel.uiState.value.searchQuery).isEmpty()
    }

    @Test
    fun categorySelected_resetsSearchQueryAndActiveState() = runTest {
        val viewModel = MovieListViewModel(moviesRepository, authRepository)

        viewModel.onAction(MovieListAction.ToggleSearchClicked)
        viewModel.onAction(MovieListAction.SearchQueryChanged("Duna"))
        assertThat(viewModel.uiState.value.isSearchActive).isTrue()
        assertThat(viewModel.uiState.value.searchQuery).isEqualTo("Duna")

        viewModel.onAction(MovieListAction.CategorySelected(MovieCategory.TOP_RATED))
        assertThat(viewModel.uiState.value.isSearchActive).isFalse()
        assertThat(viewModel.uiState.value.searchQuery).isEmpty()
    }

    @Test
    fun searchQuery_filtersMoviesStreamByTitleCaseInsensitive() = runTest {
        val movie1 = MovieSummary(id = 1, title = "Clube da Luta", posterUrl = null, voteAverage = 8.8)
        val movie2 = MovieSummary(id = 2, title = "Pulp Fiction", posterUrl = null, voteAverage = 8.9)
        val movie3 = MovieSummary(id = 3, title = "Clube dos Cinco", posterUrl = null, voteAverage = 7.8)

        every { moviesRepository.movies(MovieCategory.POPULAR) } returns flowOf(
            PagingData.from(listOf(movie1, movie2, movie3))
        )

        val viewModel = MovieListViewModel(moviesRepository, authRepository)

        // Search for "clube" (lowercase, partial)
        viewModel.onAction(MovieListAction.SearchQueryChanged("clube"))

        val filteredMovies = viewModel.moviesFlow.take(1).asSnapshot()
        assertThat(filteredMovies).hasSize(2)
        assertThat(filteredMovies.map { it.title }).containsExactly("Clube da Luta", "Clube dos Cinco")
    }
}
