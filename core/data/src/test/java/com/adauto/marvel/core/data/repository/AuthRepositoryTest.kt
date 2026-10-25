package com.adauto.marvel.core.data.repository

import androidx.room.withTransaction
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.adauto.marvel.core.data.storage.AuthPreferencesStorage
import com.adauto.marvel.core.data.sync.SyncCoordinator
import com.adauto.marvel.core.data.worker.SyncOutboxWorker
import com.adauto.marvel.core.database.MarvelDatabase
import com.adauto.marvel.core.database.dao.FavoriteDao
import com.adauto.marvel.core.database.dao.SyncOutboxDao
import com.adauto.marvel.core.database.dao.WatchedDao
import com.adauto.marvel.core.database.entity.FavoriteMovieEntity
import com.adauto.marvel.core.database.entity.SyncOutboxEntity
import com.adauto.marvel.core.database.entity.WatchedMovieEntity
import com.adauto.marvel.core.model.AccountProfile
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
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
    private val syncOutboxDao: SyncOutboxDao = mockk(relaxed = true)
    private val moviesRepository: MoviesRepository = mockk(relaxed = true)
    private val authPreferences: AuthPreferencesStorage = mockk(relaxed = true)
    private val workManager: WorkManager = mockk(relaxed = true)
    private val syncCoordinator = SyncCoordinator()

    private var currentTimeMs: Long = 1_000_000L
    private lateinit var authRepository: AuthRepository

    @Before
    fun setUp() {
        currentTimeMs = 1_000_000L
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { database.withTransaction(any<suspend () -> Any>()) } coAnswers {
            @Suppress("UNCHECKED_CAST")
            val block = it.invocation.args[1] as suspend () -> Any
            block()
        }
        every { database.favoriteDao() } returns favoriteDao
        every { database.watchedDao() } returns watchedDao
        every { database.syncOutboxDao() } returns syncOutboxDao
        every { favoriteDao.getAllFavorites() } returns flowOf(emptyList())
        every { watchedDao.getAllWatched() } returns flowOf(emptyList())

        var pendingTokenStorage: Pair<String, Long>? = null
        coEvery { authPreferences.savePendingRequestToken(any(), any()) } coAnswers {
            pendingTokenStorage = firstArg<String>() to secondArg<Long>()
        }
        coEvery { authPreferences.getPendingRequestToken() } coAnswers { pendingTokenStorage }
        coEvery { authPreferences.clearPendingRequestToken() } coAnswers {
            pendingTokenStorage = null
        }

        authRepository = AuthRepositoryImpl(
            api = api,
            sessionManager = sessionManager,
            database = database,
            moviesRepository = moviesRepository,
            authPreferences = authPreferences,
            workManager = workManager,
            syncCoordinator = syncCoordinator,
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
            moviesRepository = moviesRepository,
            authPreferences = authPreferences,
            workManager = workManager,
            syncCoordinator = syncCoordinator
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
        val account: AccountProfile = (result as Outcome.Success).value
        assertThat(account.id).isEqualTo(42)
        assertThat(account.username).isEqualTo("user_marvel")
        assertThat(account.name).isEqualTo("Stan")
        assertThat(account.includeAdult).isFalse()

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
    fun completeAuthentication_whenTokenDoesNotMatch_doesNotClearPendingToken() = runTest {
        coEvery { api.createRequestToken() } returns ApiResponse.Success(
            TmdbRequestTokenDto(
                success = true,
                request_token = "legitimate_token",
                expires_at = "2026-10-23"
            )
        )
        coEvery {
            api.createSession(TmdbCreateSessionRequestDto("legitimate_token"))
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

        var storedPendingToken: Pair<String, Long>? = null
        coEvery { authPreferences.savePendingRequestToken(any(), any()) } answers {
            storedPendingToken = firstArg<String>() to secondArg<Long>()
        }
        coEvery { authPreferences.getPendingRequestToken() } answers { storedPendingToken }
        coEvery { authPreferences.clearPendingRequestToken() } answers { storedPendingToken = null }

        authRepository.createRequestToken()

        val attackResult = authRepository.completeAuthentication("attacker_forged_token")
        assertThat(attackResult).isInstanceOf(Outcome.Failure::class.java)
        assertThat((attackResult as Outcome.Failure).error).isEqualTo(DomainError.InvalidKey)

        assertThat(storedPendingToken?.first).isEqualTo("legitimate_token")

        val legitimateResult = authRepository.completeAuthentication("legitimate_token")
        assertThat(legitimateResult).isInstanceOf(Outcome.Success::class.java)
        assertThat(storedPendingToken).isNull()
    }

    @Test
    fun completeAuthentication_survivesProcessDeath_whenTokenReadFromStorage() = runTest {
        coEvery { authPreferences.getPendingRequestToken() } returns ("persisted_token" to currentTimeMs)
        coEvery {
            api.createSession(TmdbCreateSessionRequestDto("persisted_token"))
        } returns ApiResponse.Success(TmdbSessionResponseDto(success = true, session_id = "sess_survived"))
        coEvery {
            api.getAccountDetails("sess_survived")
        } returns ApiResponse.Success(
            TmdbAccountDto(
                id = 99,
                username = "survivor",
                name = "Survivor",
                avatar = null
            )
        )
        coEvery { moviesRepository.syncCloudWithLocal() } returns Outcome.Success(Unit)

        val freshRepository = AuthRepositoryImpl(
            api = api,
            sessionManager = sessionManager,
            database = database,
            moviesRepository = moviesRepository,
            authPreferences = authPreferences,
            workManager = workManager,
            syncCoordinator = syncCoordinator,
            timeProvider = { currentTimeMs }
        )

        val result = freshRepository.completeAuthentication("persisted_token")
        assertThat(result).isInstanceOf(Outcome.Success::class.java)
        coVerify(exactly = 1) { authPreferences.clearPendingRequestToken() }
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
    fun completeAuthentication_whenSameAccount_retainsLocalDataAndOutbox() = runTest {
        coEvery { authPreferences.getLastAccountId() } returns "42"
        coEvery { api.createRequestToken() } returns ApiResponse.Success(
            TmdbRequestTokenDto(success = true, request_token = "tok_same", expires_at = "2026-10-23")
        )
        coEvery {
            api.createSession(TmdbCreateSessionRequestDto("tok_same"))
        } returns ApiResponse.Success(TmdbSessionResponseDto(success = true, session_id = "sess_42"))
        coEvery {
            api.getAccountDetails("sess_42")
        } returns ApiResponse.Success(
            TmdbAccountDto(id = 42, username = "tony", name = "Tony Stark", avatar = null)
        )
        coEvery { moviesRepository.syncCloudWithLocal() } returns Outcome.Success(Unit)

        authRepository.createRequestToken()
        val result = authRepository.completeAuthentication("tok_same")

        assertThat(result).isInstanceOf(Outcome.Success::class.java)
        coVerify(exactly = 0) { favoriteDao.clearAll() }
        coVerify(exactly = 0) { watchedDao.clearAll() }
        coVerify(exactly = 0) { syncOutboxDao.clearAll() }
        coVerify(exactly = 1) { authPreferences.saveLastAccountId("42") }
        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                SyncOutboxWorker.WORK_NAME,
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>()
            )
        }
        coVerify(exactly = 1) { moviesRepository.syncCloudWithLocal() }
    }

    @Test
    fun completeAuthentication_whenDifferentAccount_clearsPreviousDataAndOutbox() = runTest {
        coEvery { authPreferences.getLastAccountId() } returns "99"
        coEvery { api.createRequestToken() } returns ApiResponse.Success(
            TmdbRequestTokenDto(success = true, request_token = "tok_diff", expires_at = "2026-10-23")
        )
        coEvery {
            api.createSession(TmdbCreateSessionRequestDto("tok_diff"))
        } returns ApiResponse.Success(TmdbSessionResponseDto(success = true, session_id = "sess_42"))
        coEvery {
            api.getAccountDetails("sess_42")
        } returns ApiResponse.Success(
            TmdbAccountDto(id = 42, username = "tony", name = "Tony Stark", avatar = null)
        )
        coEvery { moviesRepository.syncCloudWithLocal() } returns Outcome.Success(Unit)

        authRepository.createRequestToken()
        val result = authRepository.completeAuthentication("tok_diff")

        assertThat(result).isInstanceOf(Outcome.Success::class.java)
        coVerify(exactly = 1) { favoriteDao.clearAll() }
        coVerify(exactly = 1) { watchedDao.clearAll() }
        coVerify(exactly = 1) { syncOutboxDao.clearAll() }
        coVerify(exactly = 1) { authPreferences.saveLastAccountId("42") }
        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                SyncOutboxWorker.WORK_NAME,
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>()
            )
        }
    }

    @Test
    fun completeAuthentication_stagesGuestFavoritesAndWatchlistIntoOutbox() = runTest {
        coEvery { authPreferences.getLastAccountId() } returns null
        every { favoriteDao.getAllFavorites() } returns flowOf(listOf(sampleFavEntity(101)))
        every { watchedDao.getAllWatched() } returns flowOf(listOf(sampleWatchEntity(202)))

        coEvery { api.createRequestToken() } returns ApiResponse.Success(
            TmdbRequestTokenDto(success = true, request_token = "tok_guest", expires_at = "2026-10-23")
        )
        coEvery {
            api.createSession(TmdbCreateSessionRequestDto("tok_guest"))
        } returns ApiResponse.Success(TmdbSessionResponseDto(success = true, session_id = "sess_42"))
        coEvery {
            api.getAccountDetails("sess_42")
        } returns ApiResponse.Success(
            TmdbAccountDto(id = 42, username = "tony", name = "Tony Stark", avatar = null)
        )
        coEvery { moviesRepository.syncCloudWithLocal() } returns Outcome.Success(Unit)

        authRepository.createRequestToken()
        val result = authRepository.completeAuthentication("tok_guest")

        assertThat(result).isInstanceOf(Outcome.Success::class.java)
        coVerify(exactly = 1) {
            syncOutboxDao.upsert(
                SyncOutboxEntity(movieId = 101, target = "FAVORITE", desired = true, attempts = 0, version = 1)
            )
        }
        coVerify(exactly = 1) {
            syncOutboxDao.upsert(
                SyncOutboxEntity(movieId = 202, target = "WATCHLIST", desired = true, attempts = 0, version = 1)
            )
        }
        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                SyncOutboxWorker.WORK_NAME,
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>()
            )
        }
    }

    private fun sampleFavEntity(id: Int) = FavoriteMovieEntity(
        movieId = id,
        title = "Fav $id",
        posterPath = null,
        voteAverage = 8.0,
        releaseDate = "2020-01-01",
        addedAt = 1000L
    )

    private fun sampleWatchEntity(id: Int) = WatchedMovieEntity(
        movieId = id,
        title = "Watch $id",
        posterPath = null,
        voteAverage = 7.0,
        releaseDate = "2021-01-01",
        watchedAt = 2000L
    )

    @Test
    fun logout_cancelsWorker_clearsOutboxAndLocalTables_andClearsLastAccountId() = runTest {
        authRepository.logout()

        verify(exactly = 1) { workManager.cancelUniqueWork(SyncOutboxWorker.WORK_NAME) }
        coVerify(exactly = 1) { sessionManager.clearSession() }
        coVerify(exactly = 1) { syncOutboxDao.clearAll() }
        coVerify(exactly = 1) { favoriteDao.clearAll() }
        coVerify(exactly = 1) { watchedDao.clearAll() }
        coVerify(exactly = 1) { authPreferences.clearLastAccountId() }
    }
}
