package com.adauto.marvel.feature.movies

import app.cash.turbine.test
import com.adauto.marvel.core.data.repository.AuthRepository
import com.adauto.marvel.core.data.repository.MoviesRepository
import com.adauto.marvel.core.model.MovieSummary
import com.adauto.marvel.core.model.Outcome
import com.adauto.marvel.core.model.SessionState
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

    private val sessionStateFlow = MutableStateFlow<SessionState>(SessionState.Guest)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { moviesRepository.movies(any()) } returns emptyFlow()
        every { moviesRepository.observeFavorites() } returns emptyFlow()
        every { moviesRepository.observeWatched() } returns emptyFlow()
        every { authRepository.sessionState } returns sessionStateFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun clicking_connect_triggers_OpenAuthUrl_effect_with_request_token() = runTest {
        coEvery { authRepository.createRequestToken() } returns Outcome.Success("tok_456")

        val viewModel = MovieListViewModel(moviesRepository, authRepository)

        viewModel.effects.test {
            viewModel.onAction(MovieListAction.ConnectTmdbClicked)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertThat(effect).isInstanceOf(MovieListEffect.LaunchTmdbAuth::class.java)
            assertThat((effect as MovieListEffect.LaunchTmdbAuth).authUrl)
                .contains("themoviedb.org/authenticate/tok_456")
            assertThat(effect.authUrl)
                .contains("redirect_to=marvelapp://tmdb-callback")
        }
    }

    @Test
    fun clicking_logout_calls_authRepository_logout() = runTest {
        val viewModel = MovieListViewModel(moviesRepository, authRepository)

        viewModel.onAction(MovieListAction.LogoutClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { authRepository.logout() }
    }

    @Test
    fun sessionState_updates_flow_into_uiState() = runTest {
        val viewModel = MovieListViewModel(moviesRepository, authRepository)
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
        val viewModel = MovieListViewModel(moviesRepository, authRepository)
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
        val viewModel = MovieListViewModel(moviesRepository, authRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAction(MovieListAction.RefreshRequested)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { moviesRepository.syncCloudWithLocal() }
    }

    @Test
    fun refreshRequested_when_guest_does_not_trigger_syncCloudWithLocal() = runTest {
        sessionStateFlow.value = SessionState.Guest
        val viewModel = MovieListViewModel(moviesRepository, authRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAction(MovieListAction.RefreshRequested)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { moviesRepository.syncCloudWithLocal() }
    }
}
