package com.adauto.cinemahub.core.data.worker

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.ListenableWorker.Result
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import app.cash.turbine.test
import com.adauto.cinemahub.core.data.fake.FakeSessionManager
import com.adauto.cinemahub.core.data.fake.FakeSyncFailureStorage
import com.adauto.cinemahub.core.data.fake.FakeTmdbApi
import com.adauto.cinemahub.core.data.sync.SyncCoordinator
import com.adauto.cinemahub.core.data.sync.SyncEventBus
import com.adauto.cinemahub.core.database.CinemaHubDatabase
import com.adauto.cinemahub.core.database.entity.FavoriteMovieEntity
import com.adauto.cinemahub.core.database.entity.SyncOutboxEntity
import com.adauto.cinemahub.core.database.entity.WatchlistMovieEntity
import com.adauto.cinemahub.core.model.SessionState
import com.adauto.cinemahub.core.network.model.TmdbActionResponseDto
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
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class SyncOutboxWorkerTest {

    private lateinit var context: Context
    private lateinit var database: CinemaHubDatabase
    private lateinit var fakeApi: FakeTmdbApi
    private lateinit var fakeSessionManager: FakeSessionManager
    private lateinit var fakeFailureStorage: FakeSyncFailureStorage
    private lateinit var syncCoordinator: SyncCoordinator
    private lateinit var syncEventBus: SyncEventBus
    private lateinit var worker: SyncOutboxWorker

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        database = Room.inMemoryDatabaseBuilder(context, CinemaHubDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        fakeApi = FakeTmdbApi()
        fakeSessionManager = FakeSessionManager(
            SessionState.Authenticated(
                accountId = TEST_ACCOUNT_ID,
                username = "tony",
                sessionId = TEST_SESSION_ID
            )
        )
        fakeFailureStorage = FakeSyncFailureStorage()
        syncCoordinator = SyncCoordinator()
        syncEventBus = SyncEventBus(storage = fakeFailureStorage)

        val workerFactory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker {
                return SyncOutboxWorker(
                    appContext = appContext,
                    workerParams = workerParameters,
                    database = database,
                    syncCoordinator = syncCoordinator,
                    syncEventBus = syncEventBus,
                    api = fakeApi,
                    sessionManager = fakeSessionManager
                )
            }
        }

        worker = TestListenableWorkerBuilder<SyncOutboxWorker>(context)
            .setWorkerFactory(workerFactory)
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test
    fun whenSessionIsGuest_returnsSuccessWithoutProcessingOutbox() = runTest {
        fakeSessionManager.setSessionState(SessionState.Guest)
        database.syncOutboxDao().upsert(
            SyncOutboxEntity(
                movieId = TEST_MOVIE_ID,
                target = SyncOutboxWorker.TARGET_FAVORITE,
                desired = true,
                version = TEST_VERSION
            )
        )

        val result = worker.doWork()

        assertThat(result).isEqualTo(Result.success())
        assertThat(database.syncOutboxDao().getAllPending()).hasSize(1)
        assertThat(fakeApi.favorites).isEmpty()
    }

    @Test
    fun whenNoPendingItems_returnsSuccess() = runTest {
        val result = worker.doWork()

        assertThat(result).isEqualTo(Result.success())
        assertThat(database.syncOutboxDao().getAllPending()).isEmpty()
        assertThat(fakeApi.favorites).isEmpty()
    }

    @Test
    fun whenSyncFavoriteSucceeds_updatesRemoteAndDeletesOutboxRow() = runTest {
        database.syncOutboxDao().upsert(
            SyncOutboxEntity(
                movieId = TEST_MOVIE_ID,
                target = SyncOutboxWorker.TARGET_FAVORITE,
                desired = true,
                version = TEST_VERSION
            )
        )

        val result = worker.doWork()

        assertThat(result).isEqualTo(Result.success())
        assertThat(fakeApi.favorites.map { it.id }).containsExactly(TEST_MOVIE_ID)
        assertThat(database.syncOutboxDao().getAllPending()).isEmpty()
    }

    @Test
    fun whenSyncWatchlistSucceeds_updatesRemoteAndDeletesOutboxRow() = runTest {
        fakeApi.watchlist.add(
            com.adauto.cinemahub.core.network.model.TmdbMovieDto(id = TEST_MOVIE_ID, title = "Movie")
        )
        database.syncOutboxDao().upsert(
            SyncOutboxEntity(
                movieId = TEST_MOVIE_ID,
                target = SyncOutboxWorker.TARGET_WATCHLIST,
                desired = false,
                version = TEST_VERSION
            )
        )

        val result = worker.doWork()

        assertThat(result).isEqualTo(Result.success())
        assertThat(fakeApi.watchlist).isEmpty()
        assertThat(database.syncOutboxDao().getAllPending()).isEmpty()
    }

    @Test
    fun whenNewerMutationArrivesDuringPush_preservesNewerOutboxRowAndSyncsDesiredState() = runTest {
        database.syncOutboxDao().upsert(
            SyncOutboxEntity(
                movieId = TEST_MOVIE_ID,
                target = SyncOutboxWorker.TARGET_FAVORITE,
                desired = true,
                version = 1
            )
        )

        var callCount = 0
        fakeApi.markAsFavoriteHandler = { _, _, body ->
            callCount++
            if (callCount == 1) {
                // Simulate concurrent user toggle (version 1 -> 2, desired = false) while push 1 is in flight
                database.syncOutboxDao().upsert(
                    SyncOutboxEntity(
                        movieId = TEST_MOVIE_ID,
                        target = SyncOutboxWorker.TARGET_FAVORITE,
                        desired = false,
                        version = 2
                    )
                )
            }
            if (body.favorite) {
                fakeApi.favorites.add(
                    com.adauto.cinemahub.core.network.model.TmdbMovieDto(id = body.media_id, title = "M")
                )
            } else {
                fakeApi.favorites.removeAll { it.id == body.media_id }
            }
            ApiResponse.Success(TmdbActionResponseDto(status_code = 1, status_message = "Success"))
        }

        val result = worker.doWork()

        assertThat(result).isEqualTo(Result.success())
        // Because deleteVersion(1) did not erase version 2, the worker loop drained version 2 in the next pass!
        assertThat(callCount).isEqualTo(2)
        assertThat(fakeApi.favorites).isEmpty()
        assertThat(database.syncOutboxDao().getAllPending()).isEmpty()
    }

    @Test
    fun whenVersionMismatchOnSinglePass_newerVersionIsNotDeletedByOldVersionDelete() = runTest {
        database.syncOutboxDao().upsert(
            SyncOutboxEntity(
                movieId = TEST_MOVIE_ID,
                target = SyncOutboxWorker.TARGET_FAVORITE,
                desired = true,
                version = 1
            )
        )

        var callCount = 0
        fakeApi.markAsFavoriteHandler = { _, _, _ ->
            callCount++
            if (callCount == 1) {
                // User modifies row to version 2 while version 1 is in flight
                database.syncOutboxDao().upsert(
                    SyncOutboxEntity(
                        movieId = TEST_MOVIE_ID,
                        target = SyncOutboxWorker.TARGET_FAVORITE,
                        desired = false,
                        version = 2
                    )
                )
                ApiResponse.Success(TmdbActionResponseDto(status_code = 1, status_message = "Success"))
            } else {
                // On second pass, simulate transient network failure so version 2 remains in DB to inspect
                ApiResponse.Failure.Exception(IOException("Offline on second pass"))
            }
        }

        val result = worker.doWork()

        assertThat(result).isEqualTo(Result.retry())
        val remaining = database.syncOutboxDao().getAllPending().single()
        assertThat(remaining.version).isEqualTo(2)
        assertThat(remaining.desired).isFalse()
        assertThat(remaining.attempts).isEqualTo(1)
    }

    @Test
    fun handleHttpError_429WithRetryAfterUnder10s_delaysCooperativelyAndRetriesInPass() = runTest {
        database.syncOutboxDao().upsert(
            SyncOutboxEntity(
                movieId = TEST_MOVIE_ID,
                target = SyncOutboxWorker.TARGET_FAVORITE,
                desired = true,
                attempts = 0,
                version = TEST_VERSION
            )
        )

        val headers = okhttp3.Headers.Builder().add("Retry-After", "2").build()
        val rawResponse = okhttp3.Response.Builder()
            .code(HTTP_TOO_MANY_REQUESTS)
            .message("Rate limited")
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .request(okhttp3.Request.Builder().url("https://api.themoviedb.org/3").build())
            .headers(headers)
            .build()
        val errorResponse = Response.error<TmdbActionResponseDto>(
            "Rate limited".toResponseBody(),
            rawResponse
        )

        var callCount = 0
        fakeApi.markAsFavoriteHandler = { _, _, _ ->
            callCount++
            if (callCount == 1) {
                ApiResponse.Failure.Error(errorResponse)
            } else {
                ApiResponse.Success(TmdbActionResponseDto(status_code = 1, status_message = "Success", success = true))
            }
        }

        var delayedMs = 0L
        worker.delayProvider = { delay: Long -> delayedMs += delay }

        val result = worker.doWork()

        assertThat(result).isEqualTo(Result.success())
        assertThat(delayedMs).isEqualTo(2000L)
        assertThat(callCount).isEqualTo(2)
        assertThat(database.syncOutboxDao().getAllPending()).isEmpty()
    }

    @Test
    fun whenTransientError_http429_incrementsAttemptsAndRetries() = runTest {
        database.syncOutboxDao().upsert(
            SyncOutboxEntity(
                movieId = TEST_MOVIE_ID,
                target = SyncOutboxWorker.TARGET_FAVORITE,
                desired = true,
                attempts = 0,
                version = TEST_VERSION
            )
        )
        val errorResponse = Response.error<TmdbActionResponseDto>(
            HTTP_TOO_MANY_REQUESTS,
            "Rate limited".toResponseBody()
        )
        fakeApi.failureResponse = ApiResponse.Failure.Error(errorResponse)
        worker.delayProvider = { }

        val result = worker.doWork()

        assertThat(result).isEqualTo(Result.retry())
        val updated = database.syncOutboxDao().find(TEST_MOVIE_ID, SyncOutboxWorker.TARGET_FAVORITE)
        assertThat(updated).isNotNull()
        assertThat(updated?.attempts).isEqualTo(1)
        assertThat(updated?.lastError).isNotEmpty()
    }

    @Test
    fun whenTransientError_ioException_incrementsAttemptsAndRetries() = runTest {
        database.syncOutboxDao().upsert(
            SyncOutboxEntity(
                movieId = TEST_MOVIE_ID,
                target = SyncOutboxWorker.TARGET_FAVORITE,
                desired = true,
                attempts = 1,
                version = TEST_VERSION
            )
        )
        fakeApi.failureResponse = ApiResponse.Failure.Exception(IOException("Network failure"))

        val result = worker.doWork()

        assertThat(result).isEqualTo(Result.retry())
        val updated = database.syncOutboxDao().find(TEST_MOVIE_ID, SyncOutboxWorker.TARGET_FAVORITE)
        assertThat(updated?.attempts).isEqualTo(2)
        assertThat(updated?.lastError).isEqualTo("Network failure")
    }

    @Test
    fun whenTransientError_maxAttemptsReached_treatsAsPermanentFailure_andPreservesLocalFavorite() = runTest {
        database.favoriteDao().upsert(
            FavoriteMovieEntity(
                movieId = TEST_MOVIE_ID,
                title = "Iron Man",
                posterPath = null,
                voteAverage = 8.0,
                releaseDate = "2008-05-02",
                addedAt = 1000L
            )
        )
        database.syncOutboxDao().upsert(
            SyncOutboxEntity(
                movieId = TEST_MOVIE_ID,
                target = SyncOutboxWorker.TARGET_FAVORITE,
                desired = true,
                attempts = SyncOutboxWorker.MAX_ATTEMPTS - 1,
                version = TEST_VERSION
            )
        )
        val errorResponse = Response.error<TmdbActionResponseDto>(
            HTTP_INTERNAL_SERVER_ERROR,
            "Internal Server Error".toResponseBody()
        )
        fakeApi.failureResponse = ApiResponse.Failure.Error(errorResponse)

        syncEventBus.events.test {
            val result = worker.doWork()

            assertThat(result).isEqualTo(Result.success())
            assertThat(database.favoriteDao().isFavorite(TEST_MOVIE_ID).first()).isTrue()
            assertThat(database.syncOutboxDao().getAllPending()).isEmpty()

            val event = awaitItem()
            assertThat(event.movieId).isEqualTo(TEST_MOVIE_ID)
            assertThat(event.target).isEqualTo(SyncOutboxWorker.TARGET_FAVORITE)
            assertThat(fakeFailureStorage.unconsumedFailures.first()).hasSize(1)
        }
    }

    @Test
    fun whenPermanentFailure_desiredTrueWatchlist_preservesLocalWatchlistEntry() = runTest {
        database.watchlistDao().upsert(
            WatchlistMovieEntity(
                movieId = TEST_MOVIE_ID,
                title = "Interstellar",
                posterPath = null,
                voteAverage = 8.7,
                releaseDate = "2014-11-05",
                addedAt = 2000L
            )
        )
        database.syncOutboxDao().upsert(
            SyncOutboxEntity(
                movieId = TEST_MOVIE_ID,
                target = SyncOutboxWorker.TARGET_WATCHLIST,
                desired = true,
                attempts = 0,
                version = TEST_VERSION
            )
        )
        val errorResponse = Response.error<TmdbActionResponseDto>(
            HTTP_BAD_REQUEST,
            "Bad Request".toResponseBody()
        )
        fakeApi.failureResponse = ApiResponse.Failure.Error(errorResponse)

        syncEventBus.events.test {
            val result = worker.doWork()

            assertThat(result).isEqualTo(Result.success())
            assertThat(database.watchlistDao().isInWatchlist(TEST_MOVIE_ID).first()).isTrue()
            assertThat(database.syncOutboxDao().getAllPending()).isEmpty()

            val event = awaitItem()
            assertThat(event.movieId).isEqualTo(TEST_MOVIE_ID)
            assertThat(event.target).isEqualTo(SyncOutboxWorker.TARGET_WATCHLIST)
        }
    }

    @Test
    fun whenPermanentFailure_desiredFalse_requestsPullInsteadOfDeletingLocal() = runTest {
        database.syncOutboxDao().upsert(
            SyncOutboxEntity(
                movieId = TEST_MOVIE_ID,
                target = SyncOutboxWorker.TARGET_FAVORITE,
                desired = false,
                attempts = 0,
                version = TEST_VERSION
            )
        )
        val errorResponse = Response.error<TmdbActionResponseDto>(
            HTTP_BAD_REQUEST,
            "Bad Request".toResponseBody()
        )
        fakeApi.failureResponse = ApiResponse.Failure.Error(errorResponse)

        syncCoordinator.pullRequests.test {
            val result = worker.doWork()

            assertThat(result).isEqualTo(Result.success())
            assertThat(database.syncOutboxDao().getAllPending()).isEmpty()
            assertThat(awaitItem()).isEqualTo(Unit)
        }
    }

    @Test
    fun whenPermanentFailure_versionMismatch_doesNotRollbackNewerMutation() = runTest {
        database.favoriteDao().upsert(
            FavoriteMovieEntity(
                movieId = TEST_MOVIE_ID,
                title = "Iron Man",
                posterPath = null,
                voteAverage = 8.0,
                releaseDate = "2008-05-02",
                addedAt = 1000L
            )
        )
        database.syncOutboxDao().upsert(
            SyncOutboxEntity(
                movieId = TEST_MOVIE_ID,
                target = SyncOutboxWorker.TARGET_FAVORITE,
                desired = true,
                attempts = 0,
                version = 1
            )
        )

        var callCount = 0
        fakeApi.markAsFavoriteHandler = { _, _, _ ->
            callCount++
            if (callCount == 1) {
                // Concurrent user mutation bumps version to 2 while HTTP 400 is returning
                database.syncOutboxDao().upsert(
                    SyncOutboxEntity(
                        movieId = TEST_MOVIE_ID,
                        target = SyncOutboxWorker.TARGET_FAVORITE,
                        desired = true,
                        attempts = 0,
                        version = 2
                    )
                )
                val errorResponse = Response.error<TmdbActionResponseDto>(
                    HTTP_BAD_REQUEST,
                    "Bad Request".toResponseBody()
                )
                ApiResponse.Failure.Error(errorResponse)
            } else {
                ApiResponse.Success(TmdbActionResponseDto(status_code = 1, status_message = "Success"))
            }
        }

        syncEventBus.events.test {
            val result = worker.doWork()

            assertThat(result).isEqualTo(Result.success())
            // Favorite was NOT rolled back because version 1 mismatch was detected, and version 2 succeeded!
            assertThat(database.favoriteDao().isFavorite(TEST_MOVIE_ID).first()).isTrue()
            expectNoEvents()
        }
    }

    @Test
    fun whenHttp401_clearsSessionCancelsWorkRetainsOutboxAndReturnsFailure() = runTest {
        val workManager = WorkManager.getInstance(context)
        val delayedRequest = OneTimeWorkRequestBuilder<SyncOutboxWorker>()
            .setInitialDelay(1, java.util.concurrent.TimeUnit.HOURS)
            .build()
        workManager.enqueueUniqueWork(
            SyncOutboxWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            delayedRequest
        ).result.get()

        database.syncOutboxDao().upsert(
            SyncOutboxEntity(
                movieId = TEST_MOVIE_ID,
                target = SyncOutboxWorker.TARGET_FAVORITE,
                desired = true,
                attempts = 0,
                version = TEST_VERSION
            )
        )
        val errorResponse = Response.error<TmdbActionResponseDto>(
            HTTP_UNAUTHORIZED,
            "Unauthorized".toResponseBody()
        )
        fakeApi.failureResponse = ApiResponse.Failure.Error(errorResponse)

        val result = worker.doWork()

        assertThat(result).isEqualTo(Result.failure())
        assertThat(fakeSessionManager.sessionState.value).isEqualTo(SessionState.Guest)
        assertThat(database.syncOutboxDao().getAllPending()).hasSize(1)

        val workInfos = workManager.getWorkInfosForUniqueWork(SyncOutboxWorker.WORK_NAME).get()
        assertThat(workInfos.map { it.state }).containsExactly(WorkInfo.State.CANCELLED)
    }

    @Test
    fun whenHttp404_deletesOutboxRowWithoutRollback() = runTest {
        database.favoriteDao().upsert(
            FavoriteMovieEntity(
                movieId = TEST_MOVIE_ID,
                title = "Iron Man",
                posterPath = null,
                voteAverage = 8.0,
                releaseDate = "2008-05-02",
                addedAt = 1000L
            )
        )
        database.syncOutboxDao().upsert(
            SyncOutboxEntity(
                movieId = TEST_MOVIE_ID,
                target = SyncOutboxWorker.TARGET_FAVORITE,
                desired = true,
                attempts = 0,
                version = TEST_VERSION
            )
        )
        val errorResponse = Response.error<TmdbActionResponseDto>(
            HTTP_NOT_FOUND,
            "Not Found".toResponseBody()
        )
        fakeApi.failureResponse = ApiResponse.Failure.Error(errorResponse)

        syncEventBus.events.test {
            val result = worker.doWork()

            assertThat(result).isEqualTo(Result.success())
            assertThat(database.syncOutboxDao().getAllPending()).isEmpty()
            assertThat(database.favoriteDao().isFavorite(TEST_MOVIE_ID).first()).isTrue()
            expectNoEvents()
        }
    }

    @Test
    fun whenHttp403_clearsSessionCancelsWorkRetainsOutboxAndReturnsFailure() = runTest {
        database.favoriteDao().upsert(
            FavoriteMovieEntity(
                movieId = TEST_MOVIE_ID,
                title = "Iron Man",
                posterPath = null,
                voteAverage = 8.0,
                releaseDate = "2008-05-02",
                addedAt = 1000L
            )
        )
        database.syncOutboxDao().upsert(
            SyncOutboxEntity(
                movieId = TEST_MOVIE_ID,
                target = SyncOutboxWorker.TARGET_FAVORITE,
                desired = true,
                attempts = 0,
                version = TEST_VERSION
            )
        )
        val errorResponse = Response.error<TmdbActionResponseDto>(
            HTTP_FORBIDDEN,
            "Forbidden".toResponseBody()
        )
        fakeApi.failureResponse = ApiResponse.Failure.Error(errorResponse)

        val result = worker.doWork()

        assertThat(result).isEqualTo(Result.failure())
        assertThat(fakeSessionManager.sessionState.value).isEqualTo(SessionState.Guest)
        assertThat(database.favoriteDao().isFavorite(TEST_MOVIE_ID).first()).isTrue()
        assertThat(database.syncOutboxDao().getAllPending()).hasSize(1)
    }

    @Test
    fun whenHttp401WithSessionId_retriesWithoutSessionIdAndSucceedsWithBearerToken() = runTest {
        database.syncOutboxDao().upsert(
            SyncOutboxEntity(
                movieId = TEST_MOVIE_ID,
                target = SyncOutboxWorker.TARGET_FAVORITE,
                desired = true,
                attempts = 0,
                version = TEST_VERSION
            )
        )

        val observedSessionIds = mutableListOf<String?>()
        fakeApi.markAsFavoriteHandler = { _, sessionId, body ->
            observedSessionIds.add(sessionId)
            if (sessionId != null) {
                val errorResponse = Response.error<TmdbActionResponseDto>(
                    HTTP_UNAUTHORIZED,
                    "Unauthorized session_id".toResponseBody()
                )
                ApiResponse.Failure.Error(errorResponse)
            } else {
                fakeApi.favorites.add(
                    com.adauto.cinemahub.core.network.model.TmdbMovieDto(id = body.media_id, title = "M")
                )
                ApiResponse.Success(TmdbActionResponseDto(status_code = 1, status_message = "Success"))
            }
        }

        val result = worker.doWork()

        assertThat(result).isEqualTo(Result.success())
        assertThat(observedSessionIds).containsExactly(TEST_SESSION_ID, null).inOrder()
        assertThat(fakeApi.favorites.map { it.id }).containsExactly(TEST_MOVIE_ID)
        assertThat(database.syncOutboxDao().getAllPending()).isEmpty()
        val updatedSession = fakeSessionManager.sessionState.value as SessionState.Authenticated
        assertThat(updatedSession.sessionId).isEmpty()
    }

    private companion object {
        const val TEST_ACCOUNT_ID = 42
        const val TEST_SESSION_ID = "session_42"
        const val TEST_MOVIE_ID = 101
        const val TEST_VERSION = 1
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_INTERNAL_SERVER_ERROR = 500
    }
}
