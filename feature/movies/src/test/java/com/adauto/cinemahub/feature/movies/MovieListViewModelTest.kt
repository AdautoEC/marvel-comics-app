package com.adauto.cinemahub.feature.movies

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.testing.asSnapshot
import app.cash.turbine.test
import com.adauto.cinemahub.core.data.usecase.ToggleFavoriteUseCase
import com.adauto.cinemahub.core.data.usecase.ToggleWatchlistUseCase
import com.adauto.cinemahub.core.model.DomainError
import com.adauto.cinemahub.core.model.MovieCategory
import com.adauto.cinemahub.core.model.MovieSummary
import com.adauto.cinemahub.core.model.Outcome
import com.adauto.cinemahub.core.model.SessionState
import com.adauto.cinemahub.core.testing.network.FakeNetworkMonitor
import com.adauto.cinemahub.core.testing.repository.FakeAuthRepository
import com.adauto.cinemahub.core.testing.repository.FakeMoviesRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private lateinit var moviesRepository: FakeMoviesRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var networkMonitor: FakeNetworkMonitor
    private lateinit var toggleFavoriteUseCase: ToggleFavoriteUseCase
    private lateinit var toggleWatchlistUseCase: ToggleWatchlistUseCase

    private fun createViewModel() = MovieListViewModel(
        moviesRepository,
        authRepository,
        networkMonitor,
        toggleFavoriteUseCase,
        toggleWatchlistUseCase
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        moviesRepository = FakeMoviesRepository()
        authRepository = FakeAuthRepository()
        networkMonitor = FakeNetworkMonitor(initialOnline = true)
        toggleFavoriteUseCase = ToggleFavoriteUseCase(moviesRepository)
        toggleWatchlistUseCase = ToggleWatchlistUseCase(moviesRepository)
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
    fun favoriteToggled_updatesRepositoryFavoritesState() = runTest {
        val viewModel = createViewModel()
        val sampleMovie = MovieSummary(id = 1, title = "Iron Man", posterUrl = null, voteAverage = 8.0)
        viewModel.onAction(MovieListAction.FavoriteToggled(sampleMovie))
        testDispatcher.scheduler.advanceUntilIdle()
        assertThat(moviesRepository.favorites.value.map { it.id }).containsExactly(1)
    }

    @Test
    fun watchlistToggled_updatesRepositoryWatchlistState() = runTest {
        val viewModel = createViewModel()
        val sampleMovie = MovieSummary(id = 1, title = "Iron Man", posterUrl = null, voteAverage = 8.0)
        viewModel.onAction(MovieListAction.WatchlistToggled(sampleMovie))
        testDispatcher.scheduler.advanceUntilIdle()
        assertThat(moviesRepository.watchlist.value.map { it.id }).containsExactly(1)
    }

    @Test
    fun refreshRequested_togglesIsRefreshingState() = runTest {
        val viewModel = createViewModel()
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
    }

    @Test
    fun refreshRequested_whenAuthenticated_callsSyncCloudWithLocal() = runTest {
        authRepository.setSessionState(
            SessionState.Authenticated(accountId = 42, username = "adauto", sessionId = "sess_123")
        )
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAction(MovieListAction.RefreshRequested)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(moviesRepository.syncCloudCallCount).isEqualTo(1)
    }

    @Test
    fun refreshRequested_whenAuthenticated_triggersSyncAndResetsIsRefreshingWithoutDelay() = runTest {
        authRepository.setSessionState(
            SessionState.Authenticated(accountId = 42, username = "adauto", sessionId = "sess_123")
        )
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

        assertThat(moviesRepository.syncCloudCallCount).isEqualTo(1)
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

        assertThat(viewModel.uiState.value.isSearchActive).isFalse()

        viewModel.onAction(MovieListAction.ToggleSearchClicked)
        assertThat(viewModel.uiState.value.isSearchActive).isTrue()

        viewModel.onAction(MovieListAction.SearchQueryChanged("Duna"))
        assertThat(viewModel.uiState.value.searchQuery).isEqualTo("Duna")

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
        moviesRepository.moviesList = listOf(movie1, movie2, movie3)

        val viewModel = createViewModel()

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

        networkMonitor.setOnline(false)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.isOffline).isTrue()

        networkMonitor.setOnline(true)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.isOffline).isFalse()
    }

    @Test
    fun movies_whenSearchQueryChanges_doesNotCollectSamePagingDataTwice() = runTest {
        val movie1 = MovieSummary(id = 1, title = "Iron Man", posterUrl = null, voteAverage = 8.0)
        val movie2 = MovieSummary(id = 2, title = "Thor", posterUrl = null, voteAverage = 7.5)
        moviesRepository.customMoviesFlowProvider = { _, query ->
            val data = if (query == "Thor") listOf(movie2) else listOf(movie1, movie2)
            Pager(PagingConfig(pageSize = 2)) {
                object : PagingSource<Int, MovieSummary>() {
                    override fun getRefreshKey(state: PagingState<Int, MovieSummary>): Int? = null
                    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MovieSummary> {
                        return LoadResult.Page(data = data, prevKey = null, nextKey = null)
                    }
                }
            }.flow
        }

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

    @Test
    fun movies_whenSearchQueryActive_delegatesToRepositoryMoviesWithQuery() = runTest {
        val thorMovie = MovieSummary(id = 2, title = "Thor", posterUrl = null, voteAverage = 7.5)
        moviesRepository.moviesList = listOf(
            MovieSummary(id = 1, title = "Iron Man", posterUrl = null, voteAverage = 8.0),
            thorMovie
        )

        val viewModel = createViewModel()
        viewModel.onAction(MovieListAction.SearchQueryChanged("Thor"))
        testScheduler.advanceTimeBy(300)

        viewModel.movies.test {
            val pagingData = awaitItem()
            val items = flowOf(pagingData).asSnapshot()
            assertThat(items).hasSize(1)
            assertThat(items.first().title).isEqualTo("Thor")
        }

        assertThat(moviesRepository.moviesQueries).contains(MovieCategory.POPULAR to "Thor")
    }

    @Test
    fun searchQueryChanged_rapidKeystrokes_debouncesPagerQueriesBy300ms() = runTest {
        val viewModel = createViewModel()

        viewModel.movies.test {
            awaitItem() // Initial load for empty query

            viewModel.onAction(MovieListAction.SearchQueryChanged("D"))
            testScheduler.advanceTimeBy(20)
            viewModel.onAction(MovieListAction.SearchQueryChanged("Du"))
            testScheduler.advanceTimeBy(20)
            viewModel.onAction(MovieListAction.SearchQueryChanged("Dun"))
            testScheduler.advanceTimeBy(20)
            viewModel.onAction(MovieListAction.SearchQueryChanged("Duna"))
            testScheduler.advanceTimeBy(20)
            viewModel.onAction(MovieListAction.SearchQueryChanged("Dunas"))

            testScheduler.advanceTimeBy(150)
            assertThat(moviesRepository.moviesQueries).doesNotContain(MovieCategory.POPULAR to "D")
            assertThat(moviesRepository.moviesQueries).doesNotContain(MovieCategory.POPULAR to "Du")
            assertThat(moviesRepository.moviesQueries).doesNotContain(MovieCategory.POPULAR to "Dun")
            assertThat(moviesRepository.moviesQueries).doesNotContain(MovieCategory.POPULAR to "Duna")
            assertThat(moviesRepository.moviesQueries).doesNotContain(MovieCategory.POPULAR to "Dunas")

            testScheduler.advanceTimeBy(160)

            awaitItem() // PagingData for debounced query "Dunas"

            assertThat(moviesRepository.moviesQueries).contains(MovieCategory.POPULAR to "Dunas")
            assertThat(moviesRepository.moviesQueries).doesNotContain(MovieCategory.POPULAR to "D")
            assertThat(moviesRepository.moviesQueries).doesNotContain(MovieCategory.POPULAR to "Du")
            assertThat(moviesRepository.moviesQueries).doesNotContain(MovieCategory.POPULAR to "Dun")
            assertThat(moviesRepository.moviesQueries).doesNotContain(MovieCategory.POPULAR to "Duna")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun connectTmdbClicked_whenFailure_emitsShowMessageEffect() = runTest {
        authRepository.authorizationUrlResult = Outcome.Failure(DomainError.NoConnection)
        val viewModel = createViewModel()

        viewModel.effects.test {
            viewModel.onAction(MovieListAction.ConnectTmdbClicked)
            val effect = awaitItem()
            assertThat(effect).isInstanceOf(MovieListEffect.ShowMessage::class.java)
        }
    }
}
