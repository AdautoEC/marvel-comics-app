package com.adauto.marvel.core.data.repository

import androidx.room.withTransaction
import androidx.work.WorkManager
import com.adauto.marvel.core.data.storage.AuthPreferencesStorage
import com.adauto.marvel.core.data.sync.SyncCoordinator
import com.adauto.marvel.core.data.worker.SyncOutboxWorker
import com.adauto.marvel.core.database.MarvelDatabase
import com.adauto.marvel.core.database.dao.FavoriteDao
import com.adauto.marvel.core.database.dao.SyncOutboxDao
import com.adauto.marvel.core.database.dao.WatchedDao
import com.adauto.marvel.core.database.entity.SyncOutboxEntity
import com.adauto.marvel.core.model.SessionState
import com.adauto.marvel.core.network.api.TmdbApi
import com.adauto.marvel.core.network.model.TmdbActionResponseDto
import com.adauto.marvel.core.network.model.TmdbDeleteSessionRequestDto
import com.adauto.marvel.core.network.model.TmdbDeleteSessionResponseDto
import com.adauto.marvel.core.network.model.TmdbFavoriteRequestDto
import com.adauto.marvel.core.network.security.session.SessionManager
import com.google.common.truth.Truth.assertThat
import com.skydoves.sandwich.ApiResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class AuthRepositoryLogoutTest {

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

    private lateinit var authRepository: AuthRepository

    @Before
    fun setUp() {
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
        every { sessionManager.sessionState } returns MutableStateFlow<SessionState>(SessionState.Guest)

        authRepository = AuthRepositoryImpl(
            api = api,
            sessionManager = sessionManager,
            database = database,
            moviesRepository = moviesRepository,
            authPreferences = authPreferences,
            workManager = workManager,
            syncCoordinator = syncCoordinator,
            timeProvider = { 1_000_000L }
        )
    }

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

    @Test
    fun logout_whenAuthenticated_revokesRemoteSessionOnTmdb() = runTest {
        val authState = SessionState.Authenticated(
            accountId = 42,
            username = "stark",
            sessionId = "sess_iron",
            avatarUrl = null
        )
        every { sessionManager.sessionState } returns MutableStateFlow(authState)
        coEvery { api.deleteSession(any()) } returns ApiResponse.Success(
            TmdbDeleteSessionResponseDto(success = true)
        )

        authRepository.logout()

        coVerify(exactly = 1) {
            api.deleteSession(TmdbDeleteSessionRequestDto(sessionId = "sess_iron"))
        }
        coVerify(exactly = 1) { sessionManager.clearSession() }
        coVerify(exactly = 1) { authPreferences.clearPendingRequestToken() }
        coVerify(exactly = 1) { authPreferences.clearLastAccountId() }
    }

    @Test
    fun logout_whenPendingOutboxExists_attemptsFlushBeforeClearing() = runTest {
        val authState = SessionState.Authenticated(
            accountId = 42,
            username = "stark",
            sessionId = "sess_iron",
            avatarUrl = null
        )
        every { sessionManager.sessionState } returns MutableStateFlow(authState)
        val outboxItem = SyncOutboxEntity(
            movieId = 101,
            target = SyncOutboxWorker.TARGET_FAVORITE,
            desired = true,
            version = 1
        )
        coEvery { syncOutboxDao.getAllPending() } returns listOf(outboxItem)
        coEvery {
            api.markAsFavorite(42, "sess_iron", any())
        } returns ApiResponse.Success(TmdbActionResponseDto(status_code = 1, status_message = "Success"))
        coEvery { api.deleteSession(any()) } returns ApiResponse.Success(
            TmdbDeleteSessionResponseDto(success = true)
        )

        authRepository.logout()

        coVerify(exactly = 1) {
            api.markAsFavorite(42, "sess_iron", TmdbFavoriteRequestDto(media_id = 101, favorite = true))
        }
        coVerify(exactly = 1) {
            syncOutboxDao.deleteVersion(101, SyncOutboxWorker.TARGET_FAVORITE, 1)
        }
        coVerify(exactly = 1) {
            api.deleteSession(TmdbDeleteSessionRequestDto("sess_iron"))
        }
    }

    @Test
    fun flushPendingOutbox_rethrowsCancellationException() = runTest {
        val authState = SessionState.Authenticated(
            accountId = 42,
            username = "stark",
            sessionId = "sess_iron",
            avatarUrl = null
        )
        every { sessionManager.sessionState } returns MutableStateFlow(authState)
        val outboxItem = SyncOutboxEntity(
            movieId = 101,
            target = SyncOutboxWorker.TARGET_FAVORITE,
            desired = true,
            version = 1
        )
        coEvery { syncOutboxDao.getAllPending() } returns listOf(outboxItem)
        coEvery {
            api.markAsFavorite(42, "sess_iron", any())
        } throws CancellationException("Timeout reached")

        try {
            authRepository.logout()
            fail("Expected CancellationException to be thrown")
        } catch (e: CancellationException) {
            assertThat(e.message).isEqualTo("Timeout reached")
        }
    }
}
