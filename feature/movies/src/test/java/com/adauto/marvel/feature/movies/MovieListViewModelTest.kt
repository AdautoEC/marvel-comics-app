package com.adauto.marvel.feature.movies

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.testing.asSnapshot
import app.cash.turbine.test
import com.adauto.marvel.core.data.repository.AuthRepository
import com.adauto.marvel.core.data.repository.MoviesRepository
import com.adauto.marvel.core.data.util.NetworkMonitor
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
    private val isOnlineFlow = MutableStateFlow(true)
    private val networkMonitor: NetworkMonitor = mockk {
        every { isOnline } returns isOnlineFlow
    }

    private fun createViewModel() = MovieListViewModel(moviesRepository, authRepository, networkMonitor)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        isOnlineFlow.value = true
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
        val viewModel = createViewModel()
        val state = viewModel.uiState.value
        assertThat(state.isOffline).isFalse()
        assertThat(state.isRefreshing).isFalse()
        assertThat(state.selectedCategory).isEqualTo(MovieCategory.POPULAR)
    }

    @Test
    fun categorySelected_updatesUiState() = runTest {
        val viewModel = createViewModel()
        viewModel.onAction(MovieListAction.CategorySelected(MovieCategory.TOP_RATED))
        assertThat(viewModel.uiState.value.selectedCategory).isEqualTo(MovieCategory.TOP_RATED)
    }

    @Test
    fun movieClickedAction_emitsOpenDetailEffect_viaBufferedChannel() = runTest {
        val viewModel = createViewModel()
        viewModel.effects.test {
            viewModel.onAction(MovieListAction.MovieClicked(101))
            val effect = awaitItem()
            assertEquals(MovieListEffect.OpenDetail(101), effect)
        }
    }

    @Test
    fun favoriteToggled_callsRepositoryToggleFavorite() = runTest {
        val viewModel = createViewModel()
        val sampleMovie = MovieSummary(id = 1, title = "Iron Man", posterUrl = null, voteAverage = 8.0)
        viewModel.onAction(MovieListAction.FavoriteToggled(sampleMovie))
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { moviesRepository.toggleFavorite(sampleMovie) }
    }

    @Test
    fun watchedToggled_callsRepositoryToggleWatched() = runTest {
        val viewModel = createViewModel()
        val sampleMovie = MovieSummary(id = 1, title = "Iron Man", posterUrl = null, voteAverage = 8.0)
        viewModel.onAction(MovieListAction.WatchedToggled(sampleMovie))
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { moviesRepository.toggleWatched(sampleMovie) }
    }

    @Test
    fun refreshRequested_togglesIsRefreshingState() = runTest {
        val viewModel = createViewModel()
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
        val viewModel = createViewModel()
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
        val viewModel = createViewModel()
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
        val viewModel = createViewModel()
        viewModel.onAction(MovieListAction.SearchQueryChanged("Duna"))
        assertThat(viewModel.uiState.value.searchQuery).isEqualTo("Duna")
    }

    @Test
    fun toggleSearchClicked_togglesSearchActiveStateAndClearsQueryWhenDeactivating() = runTest {
        val viewModel = createViewModel()

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
        val viewModel = createViewModel()

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

        val viewModel = createViewModel()

        // Search for "clube" (lowercase, partial)
        viewModel.onAction(MovieListAction.SearchQueryChanged("clube"))

        val filteredMovies = viewModel.moviesFlow.take(1).asSnapshot()
        assertThat(filteredMovies).hasSize(2)
        assertThat(filteredMovies.map { it.title }).containsExactly("Clube da Luta", "Clube dos Cinco")
    }

    @Test
    fun networkDisconnect_updatesIsOfflineState() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.isOffline).isFalse()

        isOnlineFlow.value = false
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.isOffline).isTrue()

        isOnlineFlow.value = true
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.isOffline).isFalse()
    }

    @Test
    fun movies_whenSearchQueryChanges_doesNotCollectSamePagingDataTwice() = runTest {
        val movie1 = MovieSummary(id = 1, title = "Iron Man", posterUrl = null, voteAverage = 8.0)
        val movie2 = MovieSummary(id = 2, title = "Thor", posterUrl = null, voteAverage = 7.5)
        fun createPager() = Pager(PagingConfig(pageSize = 2)) {
            object : PagingSource<Int, MovieSummary>() {
                override fun getRefreshKey(state: PagingState<Int, MovieSummary>): Int? = null
                override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MovieSummary> {
                    return LoadResult.Page(data = listOf(movie1, movie2), prevKey = null, nextKey = null)
                }
            }
        }
        every { moviesRepository.movies(MovieCategory.POPULAR) } answers { createPager().flow }

        val viewModel = createViewModel()

        viewModel.movies.test {
            val firstPagingData = awaitItem()
            val items1 = flowOf(firstPagingData).asSnapshot()
            assertThat(items1).hasSize(2)

            viewModel.onAction(MovieListAction.SearchQueryChanged("Thor"))

            val secondPagingData = awaitItem()
            val items2 = flowOf(secondPagingData).asSnapshot()
            assertThat(items2).hasSize(1)
            assertThat(items2.first().title).isEqualTo("Thor")
        }
    }
}
