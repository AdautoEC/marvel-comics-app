package com.adauto.marvel.core.data.repository

import com.adauto.marvel.core.database.MarvelDatabase
import com.adauto.marvel.core.database.dao.FavoriteDao
import com.adauto.marvel.core.database.dao.WatchedDao
import com.adauto.marvel.core.model.DomainError
import com.adauto.marvel.core.model.Outcome
import com.adauto.marvel.core.model.SessionState
import com.adauto.marvel.core.network.api.TmdbApi
import com.adauto.marvel.core.network.model.TmdbAccountDto
import com.adauto.marvel.core.network.model.TmdbAvatarDto
import com.adauto.marvel.core.network.model.TmdbAvatarPathDto
import com.adauto.marvel.core.network.model.TmdbCreateSessionRequestDto
import com.adauto.marvel.core.network.model.TmdbRequestTokenDto
import com.adauto.marvel.core.network.model.TmdbSessionResponseDto
import com.adauto.marvel.core.network.security.session.SessionManager
import com.google.common.truth.Truth.assertThat
import com.skydoves.sandwich.ApiResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class AuthRepositoryTest {

    private val api: TmdbApi = mockk()
    private val sessionManager: SessionManager = mockk(relaxed = true)
    private val database: MarvelDatabase = mockk()
    private val favoriteDao: FavoriteDao = mockk(relaxed = true)
    private val watchedDao: WatchedDao = mockk(relaxed = true)
    private val moviesRepository: MoviesRepository = mockk(relaxed = true)

    private var currentTimeMs: Long = 1_000_000L
    private lateinit var authRepository: AuthRepository

    @Before
    fun setUp() {
        currentTimeMs = 1_000_000L
        every { database.favoriteDao() } returns favoriteDao
        every { database.watchedDao() } returns watchedDao
        authRepository = AuthRepositoryImpl(
            api = api,
            sessionManager = sessionManager,
            database = database,
            moviesRepository = moviesRepository,
            timeProvider = { currentTimeMs }
        )
    }

    @Test
    fun sessionState_delegates_to_sessionManager() {
        val authState = SessionState.Authenticated(42, "user_marvel", "sess_123", null)
        every { sessionManager.sessionState } returns MutableStateFlow(authState)
        authRepository = AuthRepositoryImpl(
            api = api,
            sessionManager = sessionManager,
            database = database,
            moviesRepository = moviesRepository
        )

        assertThat(authRepository.sessionState.value).isEqualTo(authState)
    }

    @Test
    fun createRequestToken_returns_success_with_token() = runTest {
        coEvery { api.createRequestToken() } returns ApiResponse.Success(
            TmdbRequestTokenDto(
                success = true,
                request_token = "req_token_123",
                expires_at = "2026-10-23"
            )
        )

        val outcome = authRepository.createRequestToken()

        assertThat(outcome).isInstanceOf(Outcome.Success::class.java)
        assertThat((outcome as Outcome.Success).value).isEqualTo("req_token_123")
    }

    @Test
    fun createRequestToken_returns_failure_on_api_error() = runTest {
        val errorResponse = Response.error<TmdbRequestTokenDto>(
            401,
            "Unauthorized".toResponseBody()
        )
        coEvery { api.createRequestToken() } returns ApiResponse.Failure.Error(errorResponse)

        val outcome = authRepository.createRequestToken()

        assertThat(outcome).isInstanceOf(Outcome.Failure::class.java)
        assertThat((outcome as Outcome.Failure).error).isEqualTo(DomainError.InvalidKey)
    }

    @Test
    fun completeAuthentication_exchanges_token_fetches_account_and_saves_session() = runTest {
        coEvery { api.createRequestToken() } returns ApiResponse.Success(
            TmdbRequestTokenDto(
                success = true,
                request_token = "req_tok",
                expires_at = "2026-10-23"
            )
        )
        coEvery {
            api.createSession(TmdbCreateSessionRequestDto("req_tok"))
        } returns ApiResponse.Success(TmdbSessionResponseDto(success = true, session_id = "sess_123"))

        coEvery {
            api.getAccountDetails("sess_123")
        } returns ApiResponse.Success(
            TmdbAccountDto(
                id = 42,
                username = "user_marvel",
                name = "Stan",
                avatar = TmdbAvatarDto(tmdb = TmdbAvatarPathDto("/avatar.jpg"))
            )
        )
        coEvery { moviesRepository.syncCloudWithLocal() } returns Outcome.Success(Unit)

        authRepository.createRequestToken()
        val result = authRepository.completeAuthentication("req_tok")

        assertThat(result).isInstanceOf(Outcome.Success::class.java)
        val account = (result as Outcome.Success).value
        assertThat(account.id).isEqualTo(42)
        assertThat(account.username).isEqualTo("user_marvel")

        coVerify {
            sessionManager.saveSession(
                accountId = 42,
                username = "user_marvel",
                sessionId = "sess_123",
                avatarUrl = "https://image.tmdb.org/t/p/w200/avatar.jpg"
            )
        }
        coVerify(exactly = 1) { moviesRepository.syncCloudWithLocal() }
    }

    @Test
    fun completeAuthentication_returns_failure_when_createSession_fails() = runTest {
        coEvery { api.createRequestToken() } returns ApiResponse.Success(
            TmdbRequestTokenDto(
                success = true,
                request_token = "req_tok",
                expires_at = "2026-10-23"
            )
        )
        val errorResponse = Response.error<TmdbSessionResponseDto>(
            401,
            "Invalid token".toResponseBody()
        )
        coEvery {
            api.createSession(any())
        } returns ApiResponse.Failure.Error(errorResponse)

        authRepository.createRequestToken()
        val result = authRepository.completeAuthentication("req_tok")

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        assertThat((result as Outcome.Failure).error).isEqualTo(DomainError.InvalidKey)

        coVerify(exactly = 0) { sessionManager.saveSession(any(), any(), any(), any()) }
        coVerify(exactly = 0) { moviesRepository.syncCloudWithLocal() }
    }

    @Test
    fun completeAuthentication_returns_failure_when_getAccountDetails_fails() = runTest {
        coEvery { api.createRequestToken() } returns ApiResponse.Success(
            TmdbRequestTokenDto(
                success = true,
                request_token = "req_tok",
                expires_at = "2026-10-23"
            )
        )
        coEvery {
            api.createSession(any())
        } returns ApiResponse.Success(TmdbSessionResponseDto(success = true, session_id = "sess_123"))

        val errorResponse = Response.error<TmdbAccountDto>(
            500,
            "Internal Server Error".toResponseBody()
        )
        coEvery {
            api.getAccountDetails("sess_123")
        } returns ApiResponse.Failure.Error(errorResponse)

        authRepository.createRequestToken()
        val result = authRepository.completeAuthentication("req_tok")

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        assertThat((result as Outcome.Failure).error).isEqualTo(DomainError.Server(500))

        coVerify(exactly = 0) { sessionManager.saveSession(any(), any(), any(), any()) }
        coVerify(exactly = 0) { moviesRepository.syncCloudWithLocal() }
    }

    @Test
    fun completeAuthentication_withForgedToken_failsWithInvalidKeyAndDoesNotCallApi() = runTest {
        coEvery { api.createRequestToken() } returns ApiResponse.Success(
            TmdbRequestTokenDto(
                success = true,
                request_token = "legitimate_token",
                expires_at = "2026-10-23"
            )
        )
        coEvery {
            api.createSession(any())
        } returns ApiResponse.Success(TmdbSessionResponseDto(success = true, session_id = "sess_123"))

        authRepository.createRequestToken()
        val result = authRepository.completeAuthentication("attacker_forged_token")

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        assertThat((result as Outcome.Failure).error).isEqualTo(DomainError.InvalidKey)
        coVerify(exactly = 0) { api.createSession(any()) }
    }

    @Test
    fun completeAuthentication_withoutPriorRequestToken_failsWithInvalidKey() = runTest {
        coEvery {
            api.createSession(any())
        } returns ApiResponse.Success(TmdbSessionResponseDto(success = true, session_id = "sess_123"))

        val result = authRepository.completeAuthentication("any_token")

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        assertThat((result as Outcome.Failure).error).isEqualTo(DomainError.InvalidKey)
        coVerify(exactly = 0) { api.createSession(any()) }
    }

    @Test
    fun completeAuthentication_withExpiredToken_failsWithInvalidKey() = runTest {
        coEvery { api.createRequestToken() } returns ApiResponse.Success(
            TmdbRequestTokenDto(
                success = true,
                request_token = "valid_token",
                expires_at = "2026-10-23"
            )
        )
        coEvery {
            api.createSession(any())
        } returns ApiResponse.Success(TmdbSessionResponseDto(success = true, session_id = "sess_123"))

        authRepository.createRequestToken()

        // Advance clock past 10-minute TTL (10 * 60 * 1000 = 600,000 ms)
        currentTimeMs += 10 * 60 * 1000L + 1L

        val result = authRepository.completeAuthentication("valid_token")

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        assertThat((result as Outcome.Failure).error).isEqualTo(DomainError.InvalidKey)
        coVerify(exactly = 0) { api.createSession(any()) }
    }

    @Test
    fun completeAuthentication_tokenReplay_failsOnSecondAttempt() = runTest {
        coEvery { api.createRequestToken() } returns ApiResponse.Success(
            TmdbRequestTokenDto(
                success = true,
                request_token = "valid_token",
                expires_at = "2026-10-23"
            )
        )
        coEvery {
            api.createSession(TmdbCreateSessionRequestDto("valid_token"))
        } returns ApiResponse.Success(TmdbSessionResponseDto(success = true, session_id = "sess_123"))
        coEvery {
            api.getAccountDetails("sess_123")
        } returns ApiResponse.Success(
            TmdbAccountDto(
                id = 42,
                username = "user_marvel",
                name = "Stan",
                avatar = null
            )
        )
        coEvery { moviesRepository.syncCloudWithLocal() } returns Outcome.Success(Unit)

        authRepository.createRequestToken()

        val firstAttempt = authRepository.completeAuthentication("valid_token")
        assertThat(firstAttempt).isInstanceOf(Outcome.Success::class.java)

        val secondAttempt = authRepository.completeAuthentication("valid_token")
        assertThat(secondAttempt).isInstanceOf(Outcome.Failure::class.java)
        assertThat((secondAttempt as Outcome.Failure).error).isEqualTo(DomainError.InvalidKey)

        coVerify(exactly = 1) { api.createSession(any()) }
    }

    @Test
    fun completeAuthentication_afterLogout_failsWithInvalidKey() = runTest {
        coEvery { api.createRequestToken() } returns ApiResponse.Success(
            TmdbRequestTokenDto(
                success = true,
                request_token = "valid_token",
                expires_at = "2026-10-23"
            )
        )
        coEvery {
            api.createSession(any())
        } returns ApiResponse.Success(TmdbSessionResponseDto(success = true, session_id = "sess_123"))

        authRepository.createRequestToken()
        authRepository.logout()

        val result = authRepository.completeAuthentication("valid_token")

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        assertThat((result as Outcome.Failure).error).isEqualTo(DomainError.InvalidKey)
        coVerify(exactly = 0) { api.createSession(any()) }
    }

    @Test
    fun logout_clears_session_and_purges_room_tables() = runTest {
        authRepository.logout()

        coVerify(exactly = 1) { sessionManager.clearSession() }
        coVerify(exactly = 1) { favoriteDao.clearAll() }
        coVerify(exactly = 1) { watchedDao.clearAll() }
    }
}
