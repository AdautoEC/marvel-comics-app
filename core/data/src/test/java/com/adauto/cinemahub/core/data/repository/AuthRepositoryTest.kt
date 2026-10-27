package com.adauto.cinemahub.core.data.repository

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.adauto.cinemahub.core.data.fake.FakeAuthPreferencesStorage
import com.adauto.cinemahub.core.data.fake.FakeSessionManager
import com.adauto.cinemahub.core.data.fake.FakeTmdbApi
import com.adauto.cinemahub.core.data.sync.SyncCoordinator
import com.adauto.cinemahub.core.data.worker.SyncOutboxWorker
import com.adauto.cinemahub.core.database.CinemaHubDatabase
import com.adauto.cinemahub.core.database.entity.FavoriteMovieEntity
import com.adauto.cinemahub.core.database.entity.SyncOutboxEntity
import com.adauto.cinemahub.core.database.entity.WatchlistMovieEntity
import com.adauto.cinemahub.core.model.AccountProfile
import com.adauto.cinemahub.core.model.DomainError
import com.adauto.cinemahub.core.model.Outcome
import com.adauto.cinemahub.core.model.SessionState
import com.adauto.cinemahub.core.network.model.TmdbAccountDto
import com.adauto.cinemahub.core.network.model.TmdbAvatarDto
import com.adauto.cinemahub.core.network.model.TmdbAvatarPathDto
import com.adauto.cinemahub.core.network.model.TmdbRequestTokenDto
import com.adauto.cinemahub.core.network.model.TmdbSessionResponseDto
import com.adauto.cinemahub.core.testing.repository.FakeMoviesRepository
import com.google.common.truth.Truth.assertThat
import com.skydoves.sandwich.ApiResponse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
class AuthRepositoryTest {

    private lateinit var context: Context
    private lateinit var database: CinemaHubDatabase
    private lateinit var fakeApi: FakeTmdbApi
    private lateinit var fakeSessionManager: FakeSessionManager
    private lateinit var fakeMoviesRepository: FakeMoviesRepository
    private lateinit var fakeAuthPreferences: FakeAuthPreferencesStorage
    private lateinit var workManager: WorkManager
    private lateinit var syncCoordinator: SyncCoordinator

    private var currentTimeMs: Long = 1_000_000L
    private lateinit var authRepository: AuthRepository

    @Before
    fun setUp() {
        currentTimeMs = 1_000_000L
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)

        database = Room.inMemoryDatabaseBuilder(context, CinemaHubDatabase::class.java)
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .allowMainThreadQueries()
            .build()

        fakeApi = FakeTmdbApi()
        fakeSessionManager = FakeSessionManager()
        fakeMoviesRepository = FakeMoviesRepository()
        fakeAuthPreferences = FakeAuthPreferencesStorage()
        syncCoordinator = SyncCoordinator()

        authRepository = AuthRepositoryImpl(
            api = fakeApi,
            sessionManager = fakeSessionManager,
            database = database,
            moviesRepository = fakeMoviesRepository,
            authPreferences = fakeAuthPreferences,
            workManager = workManager,
            syncCoordinator = syncCoordinator,
            timeProvider = { currentTimeMs }
        )
    }

    @After
    fun tearDown() {
        database.close()
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test
    fun sessionState_delegates_to_sessionManager() {
        val authState = SessionState.Authenticated(42, "cinephile", "sess_123", null)
        fakeSessionManager.setSessionState(authState)

        assertThat(authRepository.sessionState.value).isEqualTo(authState)
    }

    @Test
    fun createRequestToken_returns_success_and_persists_pending_token() = runTest {
        fakeApi.createRequestTokenResponse = ApiResponse.Success(
            TmdbRequestTokenDto(
                success = true,
                request_token = "req_token_123",
                expires_at = "2026-10-23"
            )
        )

        val outcome = authRepository.createRequestToken()

        assertThat(outcome).isInstanceOf(Outcome.Success::class.java)
        assertThat((outcome as Outcome.Success).value).isEqualTo("req_token_123")
        assertThat(fakeAuthPreferences.getPendingRequestToken()).isEqualTo("req_token_123" to currentTimeMs)
    }

    @Test
    fun createRequestToken_returns_failure_on_api_error() = runTest {
        val errorResponse = Response.error<TmdbRequestTokenDto>(
            401,
            "Unauthorized".toResponseBody()
        )
        fakeApi.createRequestTokenResponse = ApiResponse.Failure.Error(errorResponse)

        val outcome = authRepository.createRequestToken()

        assertThat(outcome).isInstanceOf(Outcome.Failure::class.java)
        assertThat((outcome as Outcome.Failure).error).isEqualTo(DomainError.InvalidKey)
        assertThat(fakeAuthPreferences.getPendingRequestToken()).isNull()
    }

    @Test
    fun buildAuthorizationUrl_returns_encoded_url_with_token() = runTest {
        fakeApi.createRequestTokenResponse = ApiResponse.Success(
            TmdbRequestTokenDto(
                success = true,
                request_token = "req_token_123",
                expires_at = "2026-10-23"
            )
        )

        val outcome = authRepository.buildAuthorizationUrl()

        assertThat(outcome).isInstanceOf(Outcome.Success::class.java)
        val url = (outcome as Outcome.Success).value
        assertThat(url).isEqualTo(
            "https://www.themoviedb.org/authenticate/req_token_123?redirect_to=cinemahub%3A%2F%2Fauth"
        )
    }

    @Test
    fun buildAuthorizationUrl_returns_failure_when_token_fails() = runTest {
        val errorResponse = Response.error<TmdbRequestTokenDto>(
            401,
            "Unauthorized".toResponseBody()
        )
        fakeApi.createRequestTokenResponse = ApiResponse.Failure.Error(errorResponse)

        val outcome = authRepository.buildAuthorizationUrl()

        assertThat(outcome).isInstanceOf(Outcome.Failure::class.java)
        assertThat((outcome as Outcome.Failure).error).isEqualTo(DomainError.InvalidKey)
    }

    @Test
    fun completeAuthentication_exchanges_token_fetches_account_and_updates_session_state() = runTest {
        fakeApi.createRequestTokenResponse = ApiResponse.Success(
            TmdbRequestTokenDto(
                success = true,
                request_token = "req_tok",
                expires_at = "2026-10-23"
            )
        )
        fakeApi.createSessionResponse = ApiResponse.Success(
            TmdbSessionResponseDto(success = true, session_id = "sess_123")
        )
        fakeApi.getAccountDetailsResponse = ApiResponse.Success(
            TmdbAccountDto(
                id = 42,
                username = "cinephile",
                name = "Stan",
                avatar = TmdbAvatarDto(tmdb = TmdbAvatarPathDto("/avatar.jpg"))
            )
        )

        authRepository.createRequestToken()
        val result = authRepository.completeAuthentication("req_tok")

        assertThat(result).isInstanceOf(Outcome.Success::class.java)
        val account: AccountProfile = (result as Outcome.Success).value
        assertThat(account.id).isEqualTo(42)
        assertThat(account.username).isEqualTo("cinephile")
        assertThat(account.name).isEqualTo("Stan")
        assertThat(account.includeAdult).isFalse()

        assertThat(fakeSessionManager.sessionState.value).isEqualTo(
            SessionState.Authenticated(
                accountId = 42,
                username = "cinephile",
                sessionId = "sess_123",
                avatarUrl = "https://image.tmdb.org/t/p/w200/avatar.jpg"
            )
        )
        assertThat(fakeAuthPreferences.getLastAccountId()).isEqualTo("42")
        assertThat(fakeMoviesRepository.syncCloudCallCount).isEqualTo(1)
    }

    @Test
    fun completeAuthentication_returns_failure_when_createSession_fails() = runTest {
        fakeApi.createRequestTokenResponse = ApiResponse.Success(
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
        fakeApi.createSessionResponse = ApiResponse.Failure.Error(errorResponse)

        authRepository.createRequestToken()
        val result = authRepository.completeAuthentication("req_tok")

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        assertThat((result as Outcome.Failure).error).isEqualTo(DomainError.InvalidKey)
        assertThat(fakeSessionManager.sessionState.value).isEqualTo(SessionState.Guest)
        assertThat(fakeMoviesRepository.syncCloudCallCount).isEqualTo(0)
    }

    @Test
    fun completeAuthentication_returns_failure_when_getAccountDetails_fails() = runTest {
        fakeApi.createRequestTokenResponse = ApiResponse.Success(
            TmdbRequestTokenDto(
                success = true,
                request_token = "req_tok",
                expires_at = "2026-10-23"
            )
        )
        fakeApi.createSessionResponse = ApiResponse.Success(
            TmdbSessionResponseDto(success = true, session_id = "sess_123")
        )
        val errorResponse = Response.error<TmdbAccountDto>(
            500,
            "Internal Server Error".toResponseBody()
        )
        fakeApi.getAccountDetailsResponse = ApiResponse.Failure.Error(errorResponse)

        authRepository.createRequestToken()
        val result = authRepository.completeAuthentication("req_tok")

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        assertThat((result as Outcome.Failure).error).isEqualTo(DomainError.Server(500))
        assertThat(fakeSessionManager.sessionState.value).isEqualTo(SessionState.Guest)
        assertThat(fakeMoviesRepository.syncCloudCallCount).isEqualTo(0)
    }

    @Test
    fun completeAuthentication_withForgedToken_failsWithInvalidKeyAndLeavesSessionGuest() = runTest {
        fakeApi.createRequestTokenResponse = ApiResponse.Success(
            TmdbRequestTokenDto(
                success = true,
                request_token = "legitimate_token",
                expires_at = "2026-10-23"
            )
        )

        authRepository.createRequestToken()
        val result = authRepository.completeAuthentication("attacker_forged_token")

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        assertThat((result as Outcome.Failure).error).isEqualTo(DomainError.InvalidKey)
        assertThat(fakeSessionManager.sessionState.value).isEqualTo(SessionState.Guest)
    }

    @Test
    fun completeAuthentication_whenTokenDoesNotMatch_doesNotClearPendingToken() = runTest {
        fakeApi.createRequestTokenResponse = ApiResponse.Success(
            TmdbRequestTokenDto(
                success = true,
                request_token = "legitimate_token",
                expires_at = "2026-10-23"
            )
        )
        fakeApi.createSessionResponse = ApiResponse.Success(
            TmdbSessionResponseDto(success = true, session_id = "sess_123")
        )
        fakeApi.getAccountDetailsResponse = ApiResponse.Success(
            TmdbAccountDto(
                id = 42,
                username = "cinephile",
                name = "Stan",
                avatar = TmdbAvatarDto(tmdb = TmdbAvatarPathDto("/avatar.jpg"))
            )
        )

        authRepository.createRequestToken()

        val attackResult = authRepository.completeAuthentication("attacker_forged_token")
        assertThat(attackResult).isInstanceOf(Outcome.Failure::class.java)
        assertThat((attackResult as Outcome.Failure).error).isEqualTo(DomainError.InvalidKey)
        assertThat(fakeAuthPreferences.getPendingRequestToken()?.first).isEqualTo("legitimate_token")

        val legitimateResult = authRepository.completeAuthentication("legitimate_token")
        assertThat(legitimateResult).isInstanceOf(Outcome.Success::class.java)
        assertThat(fakeAuthPreferences.getPendingRequestToken()).isNull()
    }

    @Test
    fun completeAuthentication_survivesProcessDeath_whenTokenReadFromStorage() = runTest {
        fakeAuthPreferences.savePendingRequestToken("persisted_token", currentTimeMs)
        fakeApi.createSessionResponse = ApiResponse.Success(
            TmdbSessionResponseDto(success = true, session_id = "sess_survived")
        )
        fakeApi.getAccountDetailsResponse = ApiResponse.Success(
            TmdbAccountDto(
                id = 99,
                username = "survivor",
                name = "Survivor",
                avatar = null
            )
        )

        val freshRepository = AuthRepositoryImpl(
            api = fakeApi,
            sessionManager = fakeSessionManager,
            database = database,
            moviesRepository = fakeMoviesRepository,
            authPreferences = fakeAuthPreferences,
            workManager = workManager,
            syncCoordinator = syncCoordinator,
            timeProvider = { currentTimeMs }
        )

        val result = freshRepository.completeAuthentication("persisted_token")
        assertThat(result).isInstanceOf(Outcome.Success::class.java)
        assertThat(fakeAuthPreferences.getPendingRequestToken()).isNull()
        assertThat(fakeSessionManager.sessionState.value).isEqualTo(
            SessionState.Authenticated(accountId = 99, username = "survivor", sessionId = "sess_survived")
        )
    }

    @Test
    fun completeAuthentication_withoutPriorRequestToken_failsWithInvalidKey() = runTest {
        val result = authRepository.completeAuthentication("any_token")

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        assertThat((result as Outcome.Failure).error).isEqualTo(DomainError.InvalidKey)
        assertThat(fakeSessionManager.sessionState.value).isEqualTo(SessionState.Guest)
    }

    @Test
    fun completeAuthentication_withExpiredToken_failsWithInvalidKeyAndClearsPendingToken() = runTest {
        fakeApi.createRequestTokenResponse = ApiResponse.Success(
            TmdbRequestTokenDto(
                success = true,
                request_token = "valid_token",
                expires_at = "2026-10-23"
            )
        )

        authRepository.createRequestToken()

        // Advance clock past 10-minute TTL (10 * 60 * 1000 = 600,000 ms)
        currentTimeMs += 10 * 60 * 1000L + 1L

        val result = authRepository.completeAuthentication("valid_token")

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        assertThat((result as Outcome.Failure).error).isEqualTo(DomainError.InvalidKey)
        assertThat(fakeAuthPreferences.getPendingRequestToken()).isNull()
        assertThat(fakeSessionManager.sessionState.value).isEqualTo(SessionState.Guest)
    }

    @Test
    fun completeAuthentication_tokenReplay_failsOnSecondAttempt() = runTest {
        fakeApi.createRequestTokenResponse = ApiResponse.Success(
            TmdbRequestTokenDto(
                success = true,
                request_token = "valid_token",
                expires_at = "2026-10-23"
            )
        )
        fakeApi.createSessionResponse = ApiResponse.Success(
            TmdbSessionResponseDto(success = true, session_id = "sess_123")
        )
        fakeApi.getAccountDetailsResponse = ApiResponse.Success(
            TmdbAccountDto(id = 42, username = "cinephile", name = "Stan", avatar = null)
        )

        authRepository.createRequestToken()

        val firstAttempt = authRepository.completeAuthentication("valid_token")
        assertThat(firstAttempt).isInstanceOf(Outcome.Success::class.java)

        val secondAttempt = authRepository.completeAuthentication("valid_token")
        assertThat(secondAttempt).isInstanceOf(Outcome.Failure::class.java)
        assertThat((secondAttempt as Outcome.Failure).error).isEqualTo(DomainError.InvalidKey)
    }

    @Test
    fun completeAuthentication_afterLogout_failsWithInvalidKey() = runTest {
        fakeApi.createRequestTokenResponse = ApiResponse.Success(
            TmdbRequestTokenDto(
                success = true,
                request_token = "valid_token",
                expires_at = "2026-10-23"
            )
        )

        authRepository.createRequestToken()
        authRepository.logout()

        val result = authRepository.completeAuthentication("valid_token")

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        assertThat((result as Outcome.Failure).error).isEqualTo(DomainError.InvalidKey)
    }

    @Test
    fun completeAuthentication_whenSameAccount_retainsLocalDataAndStagesIntoOutbox() = runTest {
        fakeAuthPreferences.saveLastAccountId("42")
        database.favoriteDao().upsert(sampleFavEntity(101))

        fakeApi.createRequestTokenResponse = ApiResponse.Success(
            TmdbRequestTokenDto(success = true, request_token = "tok_same", expires_at = "2026-10-23")
        )
        fakeApi.createSessionResponse = ApiResponse.Success(
            TmdbSessionResponseDto(success = true, session_id = "sess_42")
        )
        fakeApi.getAccountDetailsResponse = ApiResponse.Success(
            TmdbAccountDto(id = 42, username = "tony", name = "Tony Stark", avatar = null)
        )

        authRepository.createRequestToken()
        val result = authRepository.completeAuthentication("tok_same")

        assertThat(result).isInstanceOf(Outcome.Success::class.java)
        assertThat(database.favoriteDao().getAllFavorites().first()).hasSize(1)
        assertThat(database.syncOutboxDao().find(101, SyncOutboxWorker.TARGET_FAVORITE)).isNotNull()
        assertThat(fakeAuthPreferences.getLastAccountId()).isEqualTo("42")
        assertThat(fakeMoviesRepository.syncCloudCallCount).isEqualTo(1)

        val workInfos = workManager.getWorkInfosForUniqueWork(SyncOutboxWorker.WORK_NAME).get()
        assertThat(workInfos).isNotEmpty()
    }

    @Test
    fun completeAuthentication_whenDifferentAccount_clearsPreviousLocalDataAndOutbox() = runTest {
        fakeAuthPreferences.saveLastAccountId("99")
        database.favoriteDao().upsert(sampleFavEntity(101))
        database.watchlistDao().upsert(sampleWatchEntity(202))
        database.syncOutboxDao().upsert(
            SyncOutboxEntity(
                movieId = 101,
                target = SyncOutboxWorker.TARGET_FAVORITE,
                desired = true,
                version = 1
            )
        )

        fakeApi.createRequestTokenResponse = ApiResponse.Success(
            TmdbRequestTokenDto(success = true, request_token = "tok_diff", expires_at = "2026-10-23")
        )
        fakeApi.createSessionResponse = ApiResponse.Success(
            TmdbSessionResponseDto(success = true, session_id = "sess_42")
        )
        fakeApi.getAccountDetailsResponse = ApiResponse.Success(
            TmdbAccountDto(id = 42, username = "tony", name = "Tony Stark", avatar = null)
        )

        authRepository.createRequestToken()
        val result = authRepository.completeAuthentication("tok_diff")

        assertThat(result).isInstanceOf(Outcome.Success::class.java)
        assertThat(database.favoriteDao().getAllFavorites().first()).isEmpty()
        assertThat(database.watchlistDao().getAllWatchlist().first()).isEmpty()
        assertThat(database.syncOutboxDao().getAllPending()).isEmpty()
        assertThat(fakeAuthPreferences.getLastAccountId()).isEqualTo("42")

        val workInfos = workManager.getWorkInfosForUniqueWork(SyncOutboxWorker.WORK_NAME).get()
        assertThat(workInfos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }).isTrue()
    }

    @Test
    fun completeAuthentication_stagesGuestFavoritesAndWatchlistIntoOutbox() = runTest {
        database.favoriteDao().upsert(sampleFavEntity(101))
        database.watchlistDao().upsert(sampleWatchEntity(202))

        fakeApi.createRequestTokenResponse = ApiResponse.Success(
            TmdbRequestTokenDto(success = true, request_token = "tok_guest", expires_at = "2026-10-23")
        )
        fakeApi.createSessionResponse = ApiResponse.Success(
            TmdbSessionResponseDto(success = true, session_id = "sess_42")
        )
        fakeApi.getAccountDetailsResponse = ApiResponse.Success(
            TmdbAccountDto(id = 42, username = "tony", name = "Tony Stark", avatar = null)
        )

        authRepository.createRequestToken()
        val result = authRepository.completeAuthentication("tok_guest")

        assertThat(result).isInstanceOf(Outcome.Success::class.java)
        val outboxItems = database.syncOutboxDao().getAllPending()
        assertThat(outboxItems).containsExactly(
            SyncOutboxEntity(
                movieId = 101,
                target = "FAVORITE",
                desired = true,
                attempts = 0,
                version = 1,
                createdAt = 1000L
            ),
            SyncOutboxEntity(
                movieId = 202,
                target = "WATCHLIST",
                desired = true,
                attempts = 0,
                version = 1,
                createdAt = 2000L
            )
        )
    }

    @Test
    fun completeAuthentication_stagesGuestDataWithMonotonicVersionIncrement() = runTest {
        database.favoriteDao().upsert(sampleFavEntity(101).copy(addedAt = 12345L))
        database.watchlistDao().upsert(sampleWatchEntity(202).copy(addedAt = 67890L))

        // Pre-existing outbox row for favorite 101 with version 3
        database.syncOutboxDao().upsert(
            SyncOutboxEntity(
                movieId = 101,
                target = SyncOutboxWorker.TARGET_FAVORITE,
                desired = false,
                version = 3,
                createdAt = 1000L
            )
        )

        fakeApi.createRequestTokenResponse = ApiResponse.Success(
            TmdbRequestTokenDto(success = true, request_token = "tok_mono", expires_at = "2026-10-23")
        )
        fakeApi.createSessionResponse = ApiResponse.Success(
            TmdbSessionResponseDto(success = true, session_id = "sess_mono")
        )
        fakeApi.getAccountDetailsResponse = ApiResponse.Success(
            TmdbAccountDto(id = 42, username = "tony", name = "Tony Stark", avatar = null)
        )

        authRepository.createRequestToken()
        val result = authRepository.completeAuthentication("tok_mono")

        assertThat(result).isInstanceOf(Outcome.Success::class.java)

        val favOutbox = database.syncOutboxDao().find(101, SyncOutboxWorker.TARGET_FAVORITE)
        assertThat(favOutbox).isEqualTo(
            SyncOutboxEntity(
                movieId = 101,
                target = "FAVORITE",
                desired = true,
                attempts = 0,
                version = 4,
                createdAt = 1000L
            )
        )

        val watchOutbox = database.syncOutboxDao().find(202, SyncOutboxWorker.TARGET_WATCHLIST)
        assertThat(watchOutbox).isEqualTo(
            SyncOutboxEntity(
                movieId = 202,
                target = "WATCHLIST",
                desired = true,
                attempts = 0,
                version = 1,
                createdAt = 67890L
            )
        )
    }

    private fun sampleFavEntity(id: Int) = FavoriteMovieEntity(
        movieId = id,
        title = "Fav $id",
        posterPath = null,
        voteAverage = 8.0,
        releaseDate = "2020-01-01",
        addedAt = 1000L
    )

    private fun sampleWatchEntity(id: Int) = WatchlistMovieEntity(
        movieId = id,
        title = "Watch $id",
        posterPath = null,
        voteAverage = 7.0,
        releaseDate = "2021-01-01",
        addedAt = 2000L
    )
}
