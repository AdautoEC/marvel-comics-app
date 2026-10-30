package com.adauto.cinemahub.feature.movies

import app.cash.turbine.test
import com.adauto.cinemahub.core.data.usecase.GetMoviesUseCase
import com.adauto.cinemahub.core.data.usecase.ToggleFavoriteUseCase
import com.adauto.cinemahub.core.data.usecase.ToggleWatchlistUseCase
import com.adauto.cinemahub.core.model.MovieSummary
import com.adauto.cinemahub.core.model.Outcome
import com.adauto.cinemahub.core.model.SessionState
import com.adauto.cinemahub.core.testing.network.FakeNetworkMonitor
import com.adauto.cinemahub.core.testing.repository.FakeAuthRepository
import com.adauto.cinemahub.core.testing.repository.FakeMoviesRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        toggleWatchlistUseCase,
        GetMoviesUseCase(moviesRepository)
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
    fun clicking_connect_triggers_OpenAuthUrl_effect_with_request_token() = runTest {
        val expectedUrl = "https://www.themoviedb.org/authenticate/tok_456?redirect_to=cinemahub%3A%2F%2Fauth"
        authRepository.authorizationUrlResult = Outcome.Success(expectedUrl)

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
    fun clicking_logout_calls_authRepository_logout_and_resets_session_to_guest() = runTest {
        authRepository.setSessionState(
            SessionState.Authenticated(accountId = 100, username = "spiderfan", sessionId = "sess_789")
        )
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAction(MovieListAction.LogoutClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(authRepository.logoutCallCount).isEqualTo(1)
        assertThat(viewModel.uiState.value.sessionState).isEqualTo(SessionState.Guest)
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
        authRepository.setSessionState(authenticatedState)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.sessionState).isEqualTo(authenticatedState)
    }

    @Test
    fun watchlistToggled_updates_moviesRepository_watchlist() = runTest {
        val viewModel = createViewModel()
        val movie = MovieSummary(id = 42, title = "Spider-Man", posterUrl = null)

        viewModel.onAction(MovieListAction.WatchlistToggled(movie))
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(moviesRepository.watchlist.value.map { it.id }).containsExactly(42)
    }

    @Test
    fun refreshRequested_when_authenticated_triggers_syncCloudWithLocal() = runTest {
        authRepository.setSessionState(
            SessionState.Authenticated(
                accountId = 100,
                username = "spiderfan",
                sessionId = "sess_789"
            )
        )
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAction(MovieListAction.RefreshRequested)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(moviesRepository.syncCloudCallCount).isEqualTo(1)
    }

    @Test
    fun refreshRequested_when_guest_does_not_trigger_syncCloudWithLocal() = runTest {
        authRepository.setSessionState(SessionState.Guest)
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAction(MovieListAction.RefreshRequested)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(moviesRepository.syncCloudCallCount).isEqualTo(0)
    }
}
