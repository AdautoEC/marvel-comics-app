package com.adauto.marvel.core.data.repository

import androidx.room.withTransaction
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.adauto.marvel.core.data.sync.SyncCoordinator
import com.adauto.marvel.core.data.worker.SyncOutboxWorker
import com.adauto.marvel.core.database.MarvelDatabase
import com.adauto.marvel.core.database.dao.FavoriteDao
import com.adauto.marvel.core.database.dao.MovieDao
import com.adauto.marvel.core.database.dao.SyncOutboxDao
import com.adauto.marvel.core.database.dao.WatchedDao
import com.adauto.marvel.core.database.entity.FavoriteMovieEntity
import com.adauto.marvel.core.database.entity.SyncOutboxEntity
import com.adauto.marvel.core.database.entity.WatchedMovieEntity
import com.adauto.marvel.core.model.DomainError
import com.adauto.marvel.core.model.MovieSummary
import com.adauto.marvel.core.model.Outcome
import com.adauto.marvel.core.model.SessionState
import com.adauto.marvel.core.network.api.TmdbApi
import com.adauto.marvel.core.network.model.TmdbMovieDto
import com.adauto.marvel.core.network.model.TmdbPageResponseDto
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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class MoviesRepositoryImplTest {

    private val api: TmdbApi = mockk()
    private val database: MarvelDatabase = mockk()
    private val favoriteDao: FavoriteDao = mockk(relaxed = true)
    private val watchedDao: WatchedDao = mockk(relaxed = true)
    private val movieDao: MovieDao = mockk(relaxed = true)
    private val syncOutboxDao: SyncOutboxDao = mockk(relaxed = true)
    private val sessionManager: SessionManager = mockk(relaxed = true)
    private val workManager: WorkManager = mockk(relaxed = true)
    private val syncCoordinator = SyncCoordinator()
    private val fixedClock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC)

    private val sessionStateFlow = MutableStateFlow<SessionState>(SessionState.Guest)
    private val testScope = TestScope()
    private lateinit var repository: MoviesRepositoryImpl

    private val sampleMovie = MovieSummary(
        id = 101,
        title = "Iron Man",
        posterUrl = "https://image.tmdb.org/t/p/w500/ironman.jpg",
        backdropUrl = null,
        voteAverage = 8.2,
        releaseDate = "2008-05-02"
    )

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
        every { database.movieDao() } returns movieDao
        every { database.syncOutboxDao() } returns syncOutboxDao
        every { sessionManager.sessionState } returns sessionStateFlow

        repository = MoviesRepositoryImpl(
            api = api,
            database = database,
            sessionManager = sessionManager,
            workManager = workManager,
            syncCoordinator = syncCoordinator,
            externalScope = testScope.backgroundScope,
            clock = fixedClock
        )
    }

    // region Gated Atomic Toggles Tests

    @Test
    fun toggleFavorite_whenGuest_updatesOnlyLocalTable_andDoesNotWriteOutbox() = runTest {
        sessionStateFlow.value = SessionState.Guest
        every { favoriteDao.isFavorite(101) } returns flowOf(false)

        repository.toggleFavorite(sampleMovie)

        coVerify(exactly = 1) {
            favoriteDao.upsert(
                FavoriteMovieEntity(
                    movieId = 101,
                    title = "Iron Man",
                    posterPath = "/ironman.jpg",
                    voteAverage = 8.2,
                    releaseDate = "2008-05-02",
                    addedAt = 1_700_000_000_000L
                )
            )
        }
        coVerify(exactly = 0) { syncOutboxDao.upsert(any()) }
        verify(exactly = 0) { workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>()) }
        coVerify(exactly = 0) { api.markAsFavorite(any(), any(), any()) }
    }

    @Test
    fun toggleFavorite_whenAuthenticated_updatesLocalTable_andUpsertsOutboxWithIncrementedVersion() = runTest {
        sessionStateFlow.value = SessionState.Authenticated(
            accountId = 42,
            username = "stark",
            sessionId = "sess_iron"
        )
        every { favoriteDao.isFavorite(101) } returns flowOf(false)
        coEvery { syncOutboxDao.find(101, SyncOutboxWorker.TARGET_FAVORITE) } returns null

        repository.toggleFavorite(sampleMovie)

        coVerify(exactly = 1) {
            favoriteDao.upsert(
                FavoriteMovieEntity(
                    movieId = 101,
                    title = "Iron Man",
                    posterPath = "/ironman.jpg",
                    voteAverage = 8.2,
                    releaseDate = "2008-05-02",
                    addedAt = 1_700_000_000_000L
                )
            )
        }
        coVerify(exactly = 1) {
            syncOutboxDao.upsert(
                SyncOutboxEntity(
                    movieId = 101,
                    target = SyncOutboxWorker.TARGET_FAVORITE,
                    desired = true,
                    attempts = 0,
                    version = 1
                )
            )
        }
        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                SyncOutboxWorker.WORK_NAME,
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>()
            )
        }
        coVerify(exactly = 0) { api.markAsFavorite(any(), any(), any()) }
    }

    @Test
    fun toggleFavorite_multipleRapidToggles_incrementsVersionMonotonically() = runTest {
        sessionStateFlow.value = SessionState.Authenticated(
            accountId = 42,
            username = "stark",
            sessionId = "sess_iron"
        )

        // 1st toggle: false -> true (existing version = 0)
        every { favoriteDao.isFavorite(101) } returns flowOf(false)
        coEvery { syncOutboxDao.find(101, SyncOutboxWorker.TARGET_FAVORITE) } returns null
        repository.toggleFavorite(sampleMovie)
        coVerify(exactly = 1) {
            syncOutboxDao.upsert(match { it.version == 1 && it.desired })
        }

        // 2nd toggle: true -> false (existing version = 1)
        every { favoriteDao.isFavorite(101) } returns flowOf(true)
        coEvery { syncOutboxDao.find(101, SyncOutboxWorker.TARGET_FAVORITE) } returns SyncOutboxEntity(
            movieId = 101,
            target = SyncOutboxWorker.TARGET_FAVORITE,
            desired = true,
            attempts = 0,
            version = 1
        )
        repository.toggleFavorite(sampleMovie)
        coVerify(exactly = 1) {
            syncOutboxDao.upsert(match { it.version == 2 && !it.desired })
        }

        // 3rd toggle: false -> true (existing version = 2)
        every { favoriteDao.isFavorite(101) } returns flowOf(false)
        coEvery { syncOutboxDao.find(101, SyncOutboxWorker.TARGET_FAVORITE) } returns SyncOutboxEntity(
            movieId = 101,
            target = SyncOutboxWorker.TARGET_FAVORITE,
            desired = false,
            attempts = 0,
            version = 2
        )
        repository.toggleFavorite(sampleMovie)
        coVerify(exactly = 1) {
            syncOutboxDao.upsert(match { it.version == 3 && it.desired })
        }
    }

    @Test
    fun toggleWatchlist_whenGuest_updatesOnlyLocalTable_andDoesNotWriteOutbox() = runTest {
        sessionStateFlow.value = SessionState.Guest
        every { watchedDao.isWatched(101) } returns flowOf(false)

        repository.toggleWatchlist(sampleMovie)

        coVerify(exactly = 1) {
            watchedDao.upsert(
                WatchedMovieEntity(
                    movieId = 101,
                    title = "Iron Man",
                    posterPath = "/ironman.jpg",
                    voteAverage = 8.2,
                    releaseDate = "2008-05-02",
                    watchedAt = 1_700_000_000_000L
                )
            )
        }
        coVerify(exactly = 0) { syncOutboxDao.upsert(any()) }
        verify(exactly = 0) { workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>()) }
        coVerify(exactly = 0) { api.addToWatchlist(any(), any(), any()) }
    }

    @Test
    fun toggleWatchlist_whenAuthenticated_updatesLocalTable_andUpsertsOutboxWithIncrementedVersion() = runTest {
        sessionStateFlow.value = SessionState.Authenticated(
            accountId = 42,
            username = "stark",
            sessionId = "sess_iron"
        )
        every { watchedDao.isWatched(101) } returns flowOf(false)
        coEvery { syncOutboxDao.find(101, SyncOutboxWorker.TARGET_WATCHLIST) } returns null

        repository.toggleWatchlist(sampleMovie)

        coVerify(exactly = 1) {
            watchedDao.upsert(
                WatchedMovieEntity(
                    movieId = 101,
                    title = "Iron Man",
                    posterPath = "/ironman.jpg",
                    voteAverage = 8.2,
                    releaseDate = "2008-05-02",
                    watchedAt = 1_700_000_000_000L
                )
            )
        }
        coVerify(exactly = 1) {
            syncOutboxDao.upsert(
                SyncOutboxEntity(
                    movieId = 101,
                    target = SyncOutboxWorker.TARGET_WATCHLIST,
                    desired = true,
                    attempts = 0,
                    version = 1
                )
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

    // endregion

    // region Authoritative Pull Reconciliation Tests

    @Test
    fun syncCloudWithLocal_skipsMoviesWithPendingOutboxMutations() = runTest {
        sessionStateFlow.value = SessionState.Authenticated(
            accountId = 42,
            username = "stark",
            sessionId = "sess_iron"
        )

        val localFav = FavoriteMovieEntity(
            movieId = 101,
            title = "Movie 101",
            posterPath = "/101.jpg",
            voteAverage = 8.0,
            releaseDate = "2020-01-01",
            addedAt = 1000L
        )
        every { favoriteDao.getAllFavorites() } returns flowOf(listOf(localFav))
        every { watchedDao.getAllWatched() } returns flowOf(emptyList())

        coEvery { syncOutboxDao.getPendingMovieIds(SyncOutboxWorker.TARGET_FAVORITE) } returns listOf(101)
        coEvery { syncOutboxDao.getPendingMovieIds(SyncOutboxWorker.TARGET_WATCHLIST) } returns emptyList()

        val remoteFav101 = createTmdbMovie(101, "Movie 101 Remote", 8.0)
        val remoteFav102 = createTmdbMovie(102, "Movie 102 Remote", 7.5)

        coEvery {
            api.getFavoriteMovies(42, "sess_iron", 1)
        } returns ApiResponse.Success(
            TmdbPageResponseDto(
                page = 1,
                results = listOf(remoteFav101, remoteFav102),
                totalPages = 1,
                totalResults = 2
            )
        )
        coEvery {
            api.getWatchlistMovies(42, "sess_iron", 1)
        } returns ApiResponse.Success(
            TmdbPageResponseDto(page = 1, results = emptyList(), totalPages = 1, totalResults = 0)
        )

        val outcome = repository.syncCloudWithLocal()

        assertThat(outcome).isInstanceOf(Outcome.Success::class.java)

        // Movie 102 was inserted because it's not pending and missing locally
        coVerify(exactly = 1) {
            favoriteDao.upsert(
                FavoriteMovieEntity(
                    movieId = 102,
                    title = "Movie 102 Remote",
                    posterPath = "/102.jpg",
                    voteAverage = 7.5,
                    releaseDate = "2020-01-01",
                    addedAt = 1_700_000_000_000L
                )
            )
        }
        // Movie 101 was skipped because it is in pendingFavs
        coVerify(exactly = 0) { favoriteDao.upsert(match { it.movieId == 101 }) }
        coVerify(exactly = 0) { favoriteDao.delete(101) }
    }

    private fun createTmdbMovie(id: Int, title: String, vote: Double) = TmdbMovieDto(
        id = id,
        title = title,
        overview = "Overview",
        posterPath = "/$id.jpg",
        backdropPath = null,
        voteAverage = vote,
        releaseDate = "2020-01-01"
    )

    @Test
    fun syncCloudWithLocal_reconcilesNonPendingMovies_insertingRemoteAndDeletingStaleLocal() = runTest {
        sessionStateFlow.value = SessionState.Authenticated(
            accountId = 42,
            username = "stark",
            sessionId = "sess_iron"
        )

        val localStaleFav = FavoriteMovieEntity(
            movieId = 103,
            title = "Movie 103 Stale",
            posterPath = "/103.jpg",
            voteAverage = 6.0,
            releaseDate = "2019-01-01",
            addedAt = 500L
        )
        every { favoriteDao.getAllFavorites() } returns flowOf(listOf(localStaleFav))
        every { watchedDao.getAllWatched() } returns flowOf(emptyList())

        coEvery { syncOutboxDao.getPendingMovieIds(SyncOutboxWorker.TARGET_FAVORITE) } returns emptyList()
        coEvery { syncOutboxDao.getPendingMovieIds(SyncOutboxWorker.TARGET_WATCHLIST) } returns emptyList()

        val remoteNewFav = TmdbMovieDto(
            id = 104,
            title = "Movie 104 New",
            overview = "Overview",
            posterPath = "/104.jpg",
            backdropPath = null,
            voteAverage = 9.0,
            releaseDate = "2023-01-01"
        )

        coEvery {
            api.getFavoriteMovies(42, "sess_iron", 1)
        } returns ApiResponse.Success(
            TmdbPageResponseDto(page = 1, results = listOf(remoteNewFav), totalPages = 1, totalResults = 1)
        )
        coEvery {
            api.getWatchlistMovies(42, "sess_iron", 1)
        } returns ApiResponse.Success(
            TmdbPageResponseDto(page = 1, results = emptyList(), totalPages = 1, totalResults = 0)
        )

        val outcome = repository.syncCloudWithLocal()

        assertThat(outcome).isInstanceOf(Outcome.Success::class.java)

        // Remote 104 inserted
        coVerify(exactly = 1) {
            favoriteDao.upsert(
                FavoriteMovieEntity(
                    movieId = 104,
                    title = "Movie 104 New",
                    posterPath = "/104.jpg",
                    voteAverage = 9.0,
                    releaseDate = "2023-01-01",
                    addedAt = 1_700_000_000_000L
                )
            )
        }
        // Stale 103 deleted
        coVerify(exactly = 1) { favoriteDao.delete(103) }
    }

    @Test
    fun syncCloudWithLocal_whenApiFails_abortsWithoutModifyingLocalDatabase() = runTest {
        sessionStateFlow.value = SessionState.Authenticated(
            accountId = 42,
            username = "stark",
            sessionId = "sess_iron"
        )

        val errorResponse = Response.error<TmdbPageResponseDto<TmdbMovieDto>>(
            500,
            "Internal Server Error".toResponseBody()
        )
        coEvery {
            api.getFavoriteMovies(any(), any(), any())
        } returns ApiResponse.Failure.Error(errorResponse)

        val result = repository.syncCloudWithLocal()

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        assertThat((result as Outcome.Failure).error).isEqualTo(DomainError.Server(500))

        coVerify(exactly = 0) { favoriteDao.upsert(any()) }
        coVerify(exactly = 0) { favoriteDao.delete(any()) }
        coVerify(exactly = 0) { watchedDao.upsert(any()) }
        coVerify(exactly = 0) { watchedDao.delete(any()) }
    }

    @Test
    fun syncCloudWithLocal_when401_clearsSessionAndReturnsInvalidKey() = runTest {
        sessionStateFlow.value = SessionState.Authenticated(
            accountId = 42,
            username = "stark",
            sessionId = "sess_iron"
        )

        val errorResponse = Response.error<TmdbPageResponseDto<TmdbMovieDto>>(
            401,
            "Unauthorized".toResponseBody()
        )
        coEvery {
            api.getFavoriteMovies(any(), any(), any())
        } returns ApiResponse.Failure.Error(errorResponse)

        val result = repository.syncCloudWithLocal()

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        assertThat((result as Outcome.Failure).error).isEqualTo(DomainError.InvalidKey)
        coVerify(exactly = 1) { sessionManager.clearSession() }

        coVerify(exactly = 0) { favoriteDao.upsert(any()) }
        coVerify(exactly = 0) { favoriteDao.delete(any()) }
    }

    @Test
    fun whenSyncCoordinatorPullRequestsEmits_triggersSyncCloudWithLocal() = testScope.runTest {
        sessionStateFlow.value = SessionState.Authenticated(
            accountId = 42,
            username = "stark",
            sessionId = "sess_iron"
        )
        coEvery { api.getFavoriteMovies(any(), any(), any()) } returns ApiResponse.Success(
            TmdbPageResponseDto(page = 1, results = emptyList(), totalPages = 1, totalResults = 0)
        )
        coEvery { api.getWatchlistMovies(any(), any(), any()) } returns ApiResponse.Success(
            TmdbPageResponseDto(page = 1, results = emptyList(), totalPages = 1, totalResults = 0)
        )
        every { favoriteDao.getAllFavorites() } returns flowOf(emptyList<FavoriteMovieEntity>())
        every { watchedDao.getAllWatched() } returns flowOf(emptyList<WatchedMovieEntity>())

        syncCoordinator.requestPull()

        testScheduler.advanceUntilIdle()

        coVerify(atLeast = 1) { api.getFavoriteMovies(42, "sess_iron", 1) }
        coVerify(atLeast = 1) { api.getWatchlistMovies(42, "sess_iron", 1) }
    }

    // endregion
}
