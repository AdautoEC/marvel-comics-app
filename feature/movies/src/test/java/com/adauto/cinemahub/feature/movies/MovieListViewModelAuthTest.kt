package com.adauto.cinemahub.feature.movies

import app.cash.turbine.test
import com.adauto.cinemahub.core.data.repository.AuthRepository
import com.adauto.cinemahub.core.data.repository.MoviesRepository
import com.adauto.cinemahub.core.data.usecase.ToggleFavoriteUseCase
import com.adauto.cinemahub.core.data.usecase.ToggleWatchlistUseCase
import com.adauto.cinemahub.core.data.util.NetworkMonitor
import com.adauto.cinemahub.core.model.MovieSummary
import com.adauto.cinemahub.core.model.Outcome
import com.adauto.cinemahub.core.model.SessionState
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MovieListViewModelAuthTest {

    private val testDispatcher = StandardTestDispatcher()
    private val moviesRepository: MoviesRepository = mockk(relaxed = true)
    private val authRepository: AuthRepository = mockk(relaxed = true)
    private val networkMonitor: NetworkMonitor = mockk {
        every { isOnline } returns MutableStateFlow(true)
    }

    private val sessionStateFlow = MutableStateFlow<SessionState>(SessionState.Guest)
    private val toggleFavoriteUseCase = ToggleFavoriteUseCase(moviesRepository)
    private val toggleWatchlistUseCase = ToggleWatchlistUseCase(moviesRepository)

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
        every { moviesRepository.movies(any()) } returns emptyFlow()
        every { moviesRepository.observeFavorites() } returns emptyFlow()
        every { moviesRepository.observeWatchlist() } returns emptyFlow()
        every { authRepository.sessionState } returns sessionStateFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun clicking_connect_triggers_OpenAuthUrl_effect_with_request_token() = runTest {
        val expectedUrl = "https://www.themoviedb.org/authenticate/tok_456?redirect_to=cinemahub%3A%2F%2Fauth"
        coEvery { authRepository.buildAuthorizationUrl() } returns Outcome.Success(expectedUrl)

        val viewModel = createViewModel()

        viewModel.effects.test {
            viewModel.onAction(MovieListAction.ConnectTmdbClicked)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertThat(effect).isInstanceOf(MovieListEffect.LaunchTmdbAuth::class.java)
            assertThat((effect as MovieListEffect.LaunchTmdbAuth).authUrl).isEqualTo(expectedUrl)
        }
    }

    @Test
    fun clicking_logout_calls_authRepository_logout() = runTest {
        val viewModel = createViewModel()

        viewModel.onAction(MovieListAction.LogoutClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { authRepository.logout() }
    }

    @Test
    fun sessionState_updates_flow_into_uiState() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.sessionState).isEqualTo(SessionState.Guest)

        val authenticatedState = SessionState.Authenticated(
            accountId = 100,
            username = "spiderfan",
            sessionId = "sess_789",
            avatarUrl = "https://image.tmdb.org/t/p/w200/avatar.jpg"
        )
        sessionStateFlow.value = authenticatedState
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.sessionState).isEqualTo(authenticatedState)
    }

    @Test
    fun watchlistToggled_calls_moviesRepository_toggleWatchlist() = runTest {
        val viewModel = createViewModel()
        val movie = MovieSummary(id = 42, title = "Spider-Man", posterUrl = null)

        viewModel.onAction(MovieListAction.WatchlistToggled(movie))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { moviesRepository.toggleWatchlist(movie) }
    }

    @Test
    fun refreshRequested_when_authenticated_triggers_syncCloudWithLocal() = runTest {
        sessionStateFlow.value = SessionState.Authenticated(
            accountId = 100,
            username = "spiderfan",
            sessionId = "sess_789"
        )
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAction(MovieListAction.RefreshRequested)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { moviesRepository.syncCloudWithLocal() }
    }

    @Test
    fun refreshRequested_when_guest_does_not_trigger_syncCloudWithLocal() = runTest {
        sessionStateFlow.value = SessionState.Guest
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAction(MovieListAction.RefreshRequested)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { moviesRepository.syncCloudWithLocal() }
    }
}
