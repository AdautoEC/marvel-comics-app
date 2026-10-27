package com.adauto.cinemahub.core.data.repository

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
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
import com.adauto.cinemahub.core.model.SessionState
import com.adauto.cinemahub.core.testing.repository.FakeMoviesRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class AuthRepositoryLogoutTest {

    private lateinit var context: Context
    private lateinit var database: CinemaHubDatabase
    private lateinit var fakeApi: FakeTmdbApi
    private lateinit var fakeSessionManager: FakeSessionManager
    private lateinit var fakeMoviesRepository: FakeMoviesRepository
    private lateinit var fakeAuthPreferences: FakeAuthPreferencesStorage
    private lateinit var workManager: WorkManager
    private lateinit var syncCoordinator: SyncCoordinator
    private lateinit var authRepository: AuthRepository

    @Before
    fun setUp() {
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
            timeProvider = { 1_000_000L }
        )
    }

    @After
    fun tearDown() {
        database.close()
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test
    fun logout_cancelsWorker_clearsOutboxAndLocalTables_andClearsSessionAndAccountId() = runTest {
        val delayedWork = OneTimeWorkRequestBuilder<SyncOutboxWorker>()
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
        workManager.enqueueUniqueWork(SyncOutboxWorker.WORK_NAME, ExistingWorkPolicy.REPLACE, delayedWork).result.get()

        fakeAuthPreferences.saveLastAccountId("42")
        fakeAuthPreferences.savePendingRequestToken("tok_pending", 1000L)
        database.favoriteDao().upsert(
            FavoriteMovieEntity(
                movieId = 101,
                title = "Iron Man",
                posterPath = null,
                voteAverage = 8.0,
                releaseDate = "2008-05-02",
                addedAt = 1000L
            )
        )
        database.watchlistDao().upsert(
            WatchlistMovieEntity(
                movieId = 202,
                title = "Interstellar",
                posterPath = null,
                voteAverage = 8.7,
                releaseDate = "2014-11-05",
                addedAt = 2000L
            )
        )
        database.syncOutboxDao().upsert(
            SyncOutboxEntity(
                movieId = 101,
                target = SyncOutboxWorker.TARGET_FAVORITE,
                desired = true,
                version = 1
            )
        )

        authRepository.logout()

        assertThat(fakeSessionManager.sessionState.value).isEqualTo(SessionState.Guest)
        assertThat(database.favoriteDao().getAllFavorites().first()).isEmpty()
        assertThat(database.watchlistDao().getAllWatchlist().first()).isEmpty()
        assertThat(database.syncOutboxDao().getAllPending()).isEmpty()
        assertThat(fakeAuthPreferences.getLastAccountId()).isNull()
        assertThat(fakeAuthPreferences.getPendingRequestToken()).isNull()

        val workInfos = workManager.getWorkInfosForUniqueWork(SyncOutboxWorker.WORK_NAME).get()
        assertThat(workInfos.map { it.state }).containsExactly(WorkInfo.State.CANCELLED)
    }

    @Test
    fun logout_whenAuthenticated_revokesRemoteSessionOnTmdb_andClearsLocalState() = runTest {
        fakeSessionManager.setSessionState(
            SessionState.Authenticated(
                accountId = 42,
                username = "stark",
                sessionId = "sess_iron",
                avatarUrl = null
            )
        )
        fakeAuthPreferences.saveLastAccountId("42")
        fakeAuthPreferences.savePendingRequestToken("tok_pending", 1000L)

        authRepository.logout()

        assertThat(fakeApi.deletedSessions).containsExactly("sess_iron")
        assertThat(fakeSessionManager.sessionState.value).isEqualTo(SessionState.Guest)
        assertThat(fakeAuthPreferences.getPendingRequestToken()).isNull()
        assertThat(fakeAuthPreferences.getLastAccountId()).isNull()
    }

    @Test
    fun logout_whenPendingOutboxExists_flushesToRemoteBeforeClearingLocalDatabase() = runTest {
        fakeSessionManager.setSessionState(
            SessionState.Authenticated(
                accountId = 42,
                username = "stark",
                sessionId = "sess_iron",
                avatarUrl = null
            )
        )
        database.syncOutboxDao().upsert(
            SyncOutboxEntity(
                movieId = 101,
                target = SyncOutboxWorker.TARGET_FAVORITE,
                desired = true,
                version = 1
            )
        )

        authRepository.logout()

        // Remote API received the flushed favorite before session deletion
        assertThat(fakeApi.favorites.map { it.id }).containsExactly(101)
        assertThat(fakeApi.deletedSessions).containsExactly("sess_iron")
        // Local database and session are completely empty after logout
        assertThat(database.syncOutboxDao().getAllPending()).isEmpty()
        assertThat(fakeSessionManager.sessionState.value).isEqualTo(SessionState.Guest)
    }

    @Test
    fun flushPendingOutbox_rethrowsExternalCancellationException() = runTest {
        fakeSessionManager.setSessionState(
            SessionState.Authenticated(
                accountId = 42,
                username = "stark",
                sessionId = "sess_iron",
                avatarUrl = null
            )
        )
        database.syncOutboxDao().upsert(
            SyncOutboxEntity(
                movieId = 101,
                target = SyncOutboxWorker.TARGET_FAVORITE,
                desired = true,
                version = 1
            )
        )
        fakeApi.markAsFavoriteHandler = { _, _, _ ->
            throw CancellationException("External cancellation")
        }

        try {
            authRepository.logout()
            fail("Expected CancellationException to be thrown")
        } catch (e: CancellationException) {
            assertThat(e.message).isEqualTo("External cancellation")
        }
    }
}
