package com.adauto.marvel.core.data.worker

import android.content.Context
import androidx.room.withTransaction
import androidx.work.ListenableWorker.Result
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.cash.turbine.test
import com.adauto.marvel.core.data.sync.SyncCoordinator
import com.adauto.marvel.core.data.sync.SyncEventBus
import com.adauto.marvel.core.database.MarvelDatabase
import com.adauto.marvel.core.database.dao.FavoriteDao
import com.adauto.marvel.core.database.dao.SyncOutboxDao
import com.adauto.marvel.core.database.dao.WatchedDao
import com.adauto.marvel.core.database.entity.SyncOutboxEntity
import com.adauto.marvel.core.model.SessionState
import com.adauto.marvel.core.network.api.TmdbApi
import com.adauto.marvel.core.network.model.TmdbActionResponseDto
import com.adauto.marvel.core.network.model.TmdbFavoriteRequestDto
import com.adauto.marvel.core.network.model.TmdbWatchlistRequestDto
import com.adauto.marvel.core.network.security.session.SessionManager
import com.google.common.truth.Truth.assertThat
import com.skydoves.sandwich.ApiResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class SyncOutboxWorkerTest {

    private val context: Context = mockk(relaxed = true)
    private val workerParams: WorkerParameters = mockk(relaxed = true)
    private val database: MarvelDatabase = mockk()
    private val syncOutboxDao: SyncOutboxDao = mockk(relaxed = true)
    private val favoriteDao: FavoriteDao = mockk(relaxed = true)
    private val watchedDao: WatchedDao = mockk(relaxed = true)
    private val api: TmdbApi = mockk()
    private val sessionManager: SessionManager = mockk(relaxed = true)
    private val workManager: WorkManager = mockk(relaxed = true)

    private val sessionStateFlow = MutableStateFlow<SessionState>(
        SessionState.Authenticated(
            accountId = TEST_ACCOUNT_ID,
            username = "tony",
            sessionId = TEST_SESSION_ID
        )
    )

    private val syncCoordinator = SyncCoordinator()
    private val syncEventBus = SyncEventBus()

    private lateinit var worker: SyncOutboxWorker

    @Before
    fun setUp() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        mockkStatic(WorkManager::class)

        every { context.applicationContext } returns context
        every { WorkManager.getInstance(any()) } returns workManager
        every { database.syncOutboxDao() } returns syncOutboxDao
        every { database.favoriteDao() } returns favoriteDao
        every { database.watchedDao() } returns watchedDao
        every { sessionManager.sessionState } returns sessionStateFlow

        coEvery { database.withTransaction(any<suspend () -> Any>()) } coAnswers {
            @Suppress("UNCHECKED_CAST")
            val block = it.invocation.args[1] as suspend () -> Any
            block()
        }

        worker = SyncOutboxWorker(
            appContext = context,
            workerParams = workerParams,
            database = database,
            syncCoordinator = syncCoordinator,
            syncEventBus = syncEventBus,
            api = api,
            sessionManager = sessionManager
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun whenSessionIsGuest_returnsSuccessWithoutProcessingOutbox() = runTest {
        sessionStateFlow.value = SessionState.Guest

        val result = worker.doWork()

        assertThat(result).isEqualTo(Result.success())
        coVerify(exactly = 0) { syncOutboxDao.getAllPending() }
        coVerify(exactly = 0) { api.markAsFavorite(any(), any(), any()) }
    }

    @Test
    fun whenNoPendingItems_returnsSuccess() = runTest {
        coEvery { syncOutboxDao.getAllPending() } returns emptyList()

        val result = worker.doWork()

        assertThat(result).isEqualTo(Result.success())
        coVerify(exactly = 0) { api.markAsFavorite(any(), any(), any()) }
    }

    @Test
    fun whenSyncFavoriteSucceeds_callsApiAndDeletesVersion() = runTest {
        val outboxItem = SyncOutboxEntity(
            movieId = TEST_MOVIE_ID,
            target = SyncOutboxWorker.TARGET_FAVORITE,
            desired = true,
            version = TEST_VERSION
        )
        coEvery { syncOutboxDao.getAllPending() } returns listOf(outboxItem) andThen emptyList()
        coEvery {
            api.markAsFavorite(
                accountId = TEST_ACCOUNT_ID,
                sessionId = TEST_SESSION_ID,
                body = TmdbFavoriteRequestDto(media_id = TEST_MOVIE_ID, favorite = true)
            )
        } returns ApiResponse.Success(TmdbActionResponseDto(status_code = 1, status_message = "Success"))
        coEvery { syncOutboxDao.deleteVersion(TEST_MOVIE_ID, SyncOutboxWorker.TARGET_FAVORITE, TEST_VERSION) } returns 1

        val result = worker.doWork()

        assertThat(result).isEqualTo(Result.success())
        coVerify(exactly = 1) {
            api.markAsFavorite(
                accountId = TEST_ACCOUNT_ID,
                sessionId = TEST_SESSION_ID,
                body = TmdbFavoriteRequestDto(media_id = TEST_MOVIE_ID, favorite = true)
            )
        }
        coVerify(exactly = 1) {
            syncOutboxDao.deleteVersion(TEST_MOVIE_ID, SyncOutboxWorker.TARGET_FAVORITE, TEST_VERSION)
        }
    }

    @Test
    fun whenSyncWatchlistSucceeds_callsApiAndDeletesVersion() = runTest {
        val outboxItem = SyncOutboxEntity(
            movieId = TEST_MOVIE_ID,
            target = SyncOutboxWorker.TARGET_WATCHLIST,
            desired = false,
            version = TEST_VERSION
        )
        coEvery { syncOutboxDao.getAllPending() } returns listOf(outboxItem) andThen emptyList()
        coEvery {
            api.addToWatchlist(
                accountId = TEST_ACCOUNT_ID,
                sessionId = TEST_SESSION_ID,
                body = TmdbWatchlistRequestDto(media_id = TEST_MOVIE_ID, watchlist = false)
            )
        } returns ApiResponse.Success(TmdbActionResponseDto(status_code = 1, status_message = "Success"))
        coEvery {
            syncOutboxDao.deleteVersion(TEST_MOVIE_ID, SyncOutboxWorker.TARGET_WATCHLIST, TEST_VERSION)
        } returns 1

        val result = worker.doWork()

        assertThat(result).isEqualTo(Result.success())
        coVerify(exactly = 1) {
            api.addToWatchlist(
                accountId = TEST_ACCOUNT_ID,
                sessionId = TEST_SESSION_ID,
                body = TmdbWatchlistRequestDto(media_id = TEST_MOVIE_ID, watchlist = false)
            )
        }
        coVerify(exactly = 1) {
            syncOutboxDao.deleteVersion(TEST_MOVIE_ID, SyncOutboxWorker.TARGET_WATCHLIST, TEST_VERSION)
        }
    }

    @Test
    fun whenVersionMismatchOnSuccess_deleteVersionAffectsZeroRows() = runTest {
        val outboxItem = SyncOutboxEntity(
            movieId = TEST_MOVIE_ID,
            target = SyncOutboxWorker.TARGET_FAVORITE,
            desired = true,
            version = TEST_VERSION
        )
        coEvery { syncOutboxDao.getAllPending() } returns listOf(outboxItem) andThen emptyList()
        coEvery {
            api.markAsFavorite(any(), any(), any())
        } returns ApiResponse.Success(TmdbActionResponseDto(status_code = 1, status_message = "Success"))
        coEvery { syncOutboxDao.deleteVersion(TEST_MOVIE_ID, SyncOutboxWorker.TARGET_FAVORITE, TEST_VERSION) } returns 0

        val result = worker.doWork()

        assertThat(result).isEqualTo(Result.success())
        coVerify(exactly = 1) {
            syncOutboxDao.deleteVersion(TEST_MOVIE_ID, SyncOutboxWorker.TARGET_FAVORITE, TEST_VERSION)
        }
    }

    @Test
    fun whenTransientError_http429_incrementsAttemptsAndRetries() = runTest {
        val outboxItem = SyncOutboxEntity(
            movieId = TEST_MOVIE_ID,
            target = SyncOutboxWorker.TARGET_FAVORITE,
            desired = true,
            attempts = 0,
            version = TEST_VERSION
        )
        coEvery { syncOutboxDao.getAllPending() } returns listOf(outboxItem)
        val errorResponse = Response.error<TmdbActionResponseDto>(
            HTTP_TOO_MANY_REQUESTS,
            "Rate limited".toResponseBody()
        )
        coEvery {
            api.markAsFavorite(any(), any(), any())
        } returns ApiResponse.Failure.Error(errorResponse)
        coEvery {
            syncOutboxDao.updateFailure(TEST_MOVIE_ID, SyncOutboxWorker.TARGET_FAVORITE, TEST_VERSION, any())
        } returns 1

        val result = worker.doWork()

        assertThat(result).isEqualTo(Result.retry())
        coVerify(exactly = 1) {
            syncOutboxDao.updateFailure(TEST_MOVIE_ID, SyncOutboxWorker.TARGET_FAVORITE, TEST_VERSION, any())
        }
        coVerify(exactly = 0) {
            syncOutboxDao.deleteVersion(any(), any(), any())
        }
    }

    @Test
    fun whenTransientError_ioException_incrementsAttemptsAndRetries() = runTest {
        val outboxItem = SyncOutboxEntity(
            movieId = TEST_MOVIE_ID,
            target = SyncOutboxWorker.TARGET_FAVORITE,
            desired = true,
            attempts = 1,
            version = TEST_VERSION
        )
        coEvery { syncOutboxDao.getAllPending() } returns listOf(outboxItem)
        coEvery {
            api.markAsFavorite(any(), any(), any())
        } returns ApiResponse.Failure.Exception(IOException("Network failure"))
        coEvery {
            syncOutboxDao.updateFailure(TEST_MOVIE_ID, SyncOutboxWorker.TARGET_FAVORITE, TEST_VERSION, any())
        } returns 1

        val result = worker.doWork()

        assertThat(result).isEqualTo(Result.retry())
        coVerify(exactly = 1) {
            syncOutboxDao.updateFailure(TEST_MOVIE_ID, SyncOutboxWorker.TARGET_FAVORITE, TEST_VERSION, any())
        }
    }

    @Test
    fun whenTransientError_maxAttemptsReached_treatsAsPermanentFailure_andRollsBackDesiredTrue() = runTest {
        val outboxItem = SyncOutboxEntity(
            movieId = TEST_MOVIE_ID,
            target = SyncOutboxWorker.TARGET_FAVORITE,
            desired = true,
            attempts = SyncOutboxWorker.MAX_ATTEMPTS - 1,
            version = TEST_VERSION
        )
        coEvery { syncOutboxDao.getAllPending() } returns listOf(outboxItem) andThen emptyList()
        val errorResponse = Response.error<TmdbActionResponseDto>(
            HTTP_INTERNAL_SERVER_ERROR,
            "Internal Server Error".toResponseBody()
        )
        coEvery {
            api.markAsFavorite(any(), any(), any())
        } returns ApiResponse.Failure.Error(errorResponse)
        coEvery { syncOutboxDao.deleteVersion(TEST_MOVIE_ID, SyncOutboxWorker.TARGET_FAVORITE, TEST_VERSION) } returns 1

        syncEventBus.events.test {
            val result = worker.doWork()

            assertThat(result).isEqualTo(Result.success())
            coVerify(exactly = 1) {
                favoriteDao.delete(TEST_MOVIE_ID)
            }
            val event = awaitItem()
            assertThat(event.movieId).isEqualTo(TEST_MOVIE_ID)
            assertThat(event.target).isEqualTo(SyncOutboxWorker.TARGET_FAVORITE)
        }
    }

    @Test
    fun whenPermanentFailure_desiredTrueWatchlist_rollsBackWatchedEntry() = runTest {
        val outboxItem = SyncOutboxEntity(
            movieId = TEST_MOVIE_ID,
            target = SyncOutboxWorker.TARGET_WATCHLIST,
            desired = true,
            attempts = 0,
            version = TEST_VERSION
        )
        coEvery { syncOutboxDao.getAllPending() } returns listOf(outboxItem) andThen emptyList()
        val errorResponse = Response.error<TmdbActionResponseDto>(
            HTTP_BAD_REQUEST,
            "Bad Request".toResponseBody()
        )
        coEvery {
            api.addToWatchlist(any(), any(), any())
        } returns ApiResponse.Failure.Error(errorResponse)
        coEvery {
            syncOutboxDao.deleteVersion(TEST_MOVIE_ID, SyncOutboxWorker.TARGET_WATCHLIST, TEST_VERSION)
        } returns 1

        syncEventBus.events.test {
            val result = worker.doWork()

            assertThat(result).isEqualTo(Result.success())
            coVerify(exactly = 1) {
                watchedDao.delete(TEST_MOVIE_ID)
            }
            val event = awaitItem()
            assertThat(event.movieId).isEqualTo(TEST_MOVIE_ID)
            assertThat(event.target).isEqualTo(SyncOutboxWorker.TARGET_WATCHLIST)
        }
    }

    @Test
    fun whenPermanentFailure_desiredFalse_requestsPullInsteadOfDeletingLocal() = runTest {
        val outboxItem = SyncOutboxEntity(
            movieId = TEST_MOVIE_ID,
            target = SyncOutboxWorker.TARGET_FAVORITE,
            desired = false,
            attempts = 0,
            version = TEST_VERSION
        )
        coEvery { syncOutboxDao.getAllPending() } returns listOf(outboxItem) andThen emptyList()
        val errorResponse = Response.error<TmdbActionResponseDto>(
            HTTP_BAD_REQUEST,
            "Bad Request".toResponseBody()
        )
        coEvery {
            api.markAsFavorite(any(), any(), any())
        } returns ApiResponse.Failure.Error(errorResponse)
        coEvery { syncOutboxDao.deleteVersion(TEST_MOVIE_ID, SyncOutboxWorker.TARGET_FAVORITE, TEST_VERSION) } returns 1

        syncCoordinator.pullRequests.test {
            val result = worker.doWork()

            assertThat(result).isEqualTo(Result.success())
            coVerify(exactly = 0) { favoriteDao.delete(any()) }
            assertThat(awaitItem()).isEqualTo(Unit)
        }
    }

    @Test
    fun whenPermanentFailure_versionMismatch_doesNotRollbackOrEmitEvent() = runTest {
        val outboxItem = SyncOutboxEntity(
            movieId = TEST_MOVIE_ID,
            target = SyncOutboxWorker.TARGET_FAVORITE,
            desired = true,
            attempts = 0,
            version = TEST_VERSION
        )
        coEvery { syncOutboxDao.getAllPending() } returns listOf(outboxItem) andThen emptyList()
        val errorResponse = Response.error<TmdbActionResponseDto>(
            HTTP_BAD_REQUEST,
            "Bad Request".toResponseBody()
        )
        coEvery {
            api.markAsFavorite(any(), any(), any())
        } returns ApiResponse.Failure.Error(errorResponse)
        // Optimistic lock mismatch: 0 rows deleted
        coEvery { syncOutboxDao.deleteVersion(TEST_MOVIE_ID, SyncOutboxWorker.TARGET_FAVORITE, TEST_VERSION) } returns 0

        syncEventBus.events.test {
            val result = worker.doWork()

            assertThat(result).isEqualTo(Result.success())
            coVerify(exactly = 0) { favoriteDao.delete(any()) }
            expectNoEvents()
        }
    }

    @Test
    fun whenMultiplePendingBatchesArriveConcurrently_drainsAllBatchesBeforeTerminating() = runTest {
        val firstItem = SyncOutboxEntity(
            movieId = 101,
            target = SyncOutboxWorker.TARGET_FAVORITE,
            desired = true,
            version = 1
        )
        val secondItem = SyncOutboxEntity(
            movieId = 202,
            target = SyncOutboxWorker.TARGET_WATCHLIST,
            desired = true,
            version = 1
        )

        // First loop returns firstItem, second loop returns secondItem (added in-flight), third loop empty
        coEvery { syncOutboxDao.getAllPending() } returnsMany listOf(
            listOf(firstItem),
            listOf(secondItem),
            emptyList()
        )
        coEvery { api.markAsFavorite(any(), any(), any()) } returns ApiResponse.Success(
            TmdbActionResponseDto(status_code = 1, status_message = "Success")
        )
        coEvery { api.addToWatchlist(any(), any(), any()) } returns ApiResponse.Success(
            TmdbActionResponseDto(status_code = 1, status_message = "Success")
        )
        coEvery { syncOutboxDao.deleteVersion(any(), any(), any()) } returns 1

        val result = worker.doWork()

        assertThat(result).isEqualTo(Result.success())
        coVerify(exactly = 1) { syncOutboxDao.deleteVersion(101, SyncOutboxWorker.TARGET_FAVORITE, 1) }
        coVerify(exactly = 1) { syncOutboxDao.deleteVersion(202, SyncOutboxWorker.TARGET_WATCHLIST, 1) }
        coVerify(exactly = 3) { syncOutboxDao.getAllPending() }
    }

    @Test
    fun whenHttp401_clearsSessionCancelsWorkRetainsOutboxAndReturnsFailure() = runTest {
        val outboxItem = SyncOutboxEntity(
            movieId = TEST_MOVIE_ID,
            target = SyncOutboxWorker.TARGET_FAVORITE,
            desired = true,
            attempts = 0,
            version = TEST_VERSION
        )
        coEvery { syncOutboxDao.getAllPending() } returns listOf(outboxItem)
        val errorResponse = Response.error<TmdbActionResponseDto>(
            HTTP_UNAUTHORIZED,
            "Unauthorized".toResponseBody()
        )
        coEvery {
            api.markAsFavorite(any(), any(), any())
        } returns ApiResponse.Failure.Error(errorResponse)

        val result = worker.doWork()

        assertThat(result).isEqualTo(Result.failure())
        coVerify(exactly = 1) { sessionManager.clearSession() }
        verify(exactly = 1) { workManager.cancelUniqueWork(SyncOutboxWorker.WORK_NAME) }
        coVerify(exactly = 0) { syncOutboxDao.deleteVersion(any(), any(), any()) }
        coVerify(exactly = 0) { favoriteDao.delete(any()) }
    }

    @Test
    fun whenHttp404_deletesOutboxRowWithoutRollback() = runTest {
        val outboxItem = SyncOutboxEntity(
            movieId = TEST_MOVIE_ID,
            target = SyncOutboxWorker.TARGET_FAVORITE,
            desired = true,
            attempts = 0,
            version = TEST_VERSION
        )
        coEvery { syncOutboxDao.getAllPending() } returns listOf(outboxItem) andThen emptyList()
        val errorResponse = Response.error<TmdbActionResponseDto>(
            HTTP_NOT_FOUND,
            "Not Found".toResponseBody()
        )
        coEvery {
            api.markAsFavorite(any(), any(), any())
        } returns ApiResponse.Failure.Error(errorResponse)
        coEvery { syncOutboxDao.deleteVersion(TEST_MOVIE_ID, SyncOutboxWorker.TARGET_FAVORITE, TEST_VERSION) } returns 1

        syncEventBus.events.test {
            val result = worker.doWork()

            assertThat(result).isEqualTo(Result.success())
            coVerify(exactly = 1) {
                syncOutboxDao.deleteVersion(TEST_MOVIE_ID, SyncOutboxWorker.TARGET_FAVORITE, TEST_VERSION)
            }
            coVerify(exactly = 0) { favoriteDao.delete(any()) }
            expectNoEvents()
        }
    }

    private companion object {
        const val TEST_ACCOUNT_ID = 42
        const val TEST_SESSION_ID = "session_42"
        const val TEST_MOVIE_ID = 101
        const val TEST_VERSION = 1
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_NOT_FOUND = 404
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_INTERNAL_SERVER_ERROR = 500
    }
}
