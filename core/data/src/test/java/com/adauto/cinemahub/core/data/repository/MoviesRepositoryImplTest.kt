package com.adauto.cinemahub.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.adauto.cinemahub.core.data.fake.FakeSessionManager
import com.adauto.cinemahub.core.data.fake.FakeTmdbApi
import com.adauto.cinemahub.core.data.sync.SyncCoordinator
import com.adauto.cinemahub.core.data.worker.SyncOutboxWorker
import com.adauto.cinemahub.core.database.CinemaHubDatabase
import com.adauto.cinemahub.core.database.entity.FavoriteMovieEntity
import com.adauto.cinemahub.core.database.entity.MovieEntity
import com.adauto.cinemahub.core.database.entity.WatchlistMovieEntity
import com.adauto.cinemahub.core.model.MovieSummary
import com.adauto.cinemahub.core.model.Outcome
import com.adauto.cinemahub.core.model.SessionState
import com.adauto.cinemahub.core.network.model.TmdbMovieDetailDto
import com.adauto.cinemahub.core.network.model.TmdbMovieDto
import com.google.common.truth.Truth.assertThat
import com.skydoves.sandwich.ApiResponse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Response
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class MoviesRepositoryImplTest {

    private lateinit var db: CinemaHubDatabase
    private lateinit var fakeApi: FakeTmdbApi
    private lateinit var sessionManager: FakeSessionManager
    private lateinit var workManager: WorkManager
    private val syncCoordinator = SyncCoordinator()
    private val fixedClock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC)

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
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)

        db = Room.inMemoryDatabaseBuilder(context, CinemaHubDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        fakeApi = FakeTmdbApi()
        sessionManager = FakeSessionManager()

        repository = MoviesRepositoryImpl(
            api = fakeApi,
            database = db,
            sessionManager = sessionManager,
            workManager = workManager,
            syncCoordinator = syncCoordinator,
            externalScope = TestScope(),
            clock = fixedClock
        )
    }

    @After
    fun tearDown() {
        db.close()
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    // region Gated Atomic Toggles Tests

    @Test
    fun toggleFavorite_whenGuest_updatesOnlyLocalTable_andDoesNotWriteOutbox() = runTest {
        sessionManager.sessionStateFlow.value = SessionState.Guest

        repository.toggleFavorite(sampleMovie)

        val favorites = db.favoriteDao().getAllFavorites().first()
        assertThat(favorites.map { it.movieId }).containsExactly(101)

        val outbox = db.syncOutboxDao().getAllPending()
        assertThat(outbox).isEmpty()
    }

    @Test
    fun toggleFavorite_whenAuthenticated_updatesLocalTable_andWritesOutbox() = runTest {
        sessionManager.sessionStateFlow.value = SessionState.Authenticated(
            accountId = 42,
            username = "adauto",
            sessionId = "sess_123"
        )

        repository.toggleFavorite(sampleMovie)

        val favorites = db.favoriteDao().getAllFavorites().first()
        assertThat(favorites.map { it.movieId }).containsExactly(101)

        val outbox = db.syncOutboxDao().find(101, SyncOutboxWorker.TARGET_FAVORITE)
        assertThat(outbox).isNotNull()
        assertThat(outbox?.desired).isTrue()
        assertThat(outbox?.version).isEqualTo(1)

        val workInfos = workManager.getWorkInfosForUniqueWork(SyncOutboxWorker.WORK_NAME).get()
        assertThat(workInfos).isNotEmpty()
        assertThat(workInfos.first().state).isIn(listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING))
    }

    @Test
    fun toggleFavorite_twice_addsThenRemoves_fromLocalDatabase() = runTest {
        sessionManager.sessionStateFlow.value = SessionState.Guest

        repository.toggleFavorite(sampleMovie)
        assertThat(db.favoriteDao().isFavorite(101).first()).isTrue()

        repository.toggleFavorite(sampleMovie)
        assertThat(db.favoriteDao().isFavorite(101).first()).isFalse()
        assertThat(db.favoriteDao().getAllFavorites().first()).isEmpty()
    }

    @Test
    fun toggleWatchlist_whenGuest_updatesOnlyLocalTable_andDoesNotWriteOutbox() = runTest {
        sessionManager.sessionStateFlow.value = SessionState.Guest

        repository.toggleWatchlist(sampleMovie)

        val watchlist = db.watchlistDao().getAllWatchlist().first()
        assertThat(watchlist.map { it.movieId }).containsExactly(101)

        val outbox = db.syncOutboxDao().getAllPending()
        assertThat(outbox).isEmpty()
    }

    @Test
    fun toggleWatchlist_whenAuthenticated_updatesLocalTable_andWritesOutbox() = runTest {
        sessionManager.sessionStateFlow.value = SessionState.Authenticated(
            accountId = 42,
            username = "adauto",
            sessionId = "sess_123"
        )

        repository.toggleWatchlist(sampleMovie)

        val watchlist = db.watchlistDao().getAllWatchlist().first()
        assertThat(watchlist.map { it.movieId }).containsExactly(101)

        val outbox = db.syncOutboxDao().find(101, SyncOutboxWorker.TARGET_WATCHLIST)
        assertThat(outbox).isNotNull()
        assertThat(outbox?.desired).isTrue()
        assertThat(outbox?.version).isEqualTo(1)
    }

    @Test
    fun toggleWatchlist_twice_addsThenRemoves_fromLocalDatabase() = runTest {
        sessionManager.sessionStateFlow.value = SessionState.Guest

        repository.toggleWatchlist(sampleMovie)
        assertThat(db.watchlistDao().isInWatchlist(101).first()).isTrue()

        repository.toggleWatchlist(sampleMovie)
        assertThat(db.watchlistDao().isInWatchlist(101).first()).isFalse()
        assertThat(db.watchlistDao().getAllWatchlist().first()).isEmpty()
    }

    // endregion

    // region Cloud Sync & Reconciliation State Tests

    @Test
    fun syncCloudWithLocal_insertsRemoteMovies_intoLocalDatabase() = runTest {
        sessionManager.sessionStateFlow.value = SessionState.Authenticated(
            accountId = 42,
            username = "adauto",
            sessionId = "sess_123"
        )
        fakeApi.favorites.add(TmdbMovieDto(id = 201, title = "Thor", posterPath = "/thor.jpg"))
        fakeApi.watchlist.add(TmdbMovieDto(id = 301, title = "Iron Man 2", posterPath = "/im2.jpg"))

        val result = repository.syncCloudWithLocal()
        assertThat(result).isEqualTo(Outcome.Success(Unit))

        val favorites = db.favoriteDao().getAllFavorites().first()
        assertThat(favorites.map { it.movieId }).containsExactly(201)

        val watchlist = db.watchlistDao().getAllWatchlist().first()
        assertThat(watchlist.map { it.movieId }).containsExactly(301)
    }

    @Test
    fun syncCloudWithLocal_removesObsoleteLocalMovies_notPresentInRemote() = runTest {
        sessionManager.sessionStateFlow.value = SessionState.Authenticated(
            accountId = 42,
            username = "adauto",
            sessionId = "sess_123"
        )
        // Locally contains movie 501, but remote returns empty
        db.favoriteDao().upsert(
            FavoriteMovieEntity(
                movieId = 501,
                title = "Hulk",
                posterPath = null,
                voteAverage = 7.5,
                releaseDate = "2008-06-13",
                addedAt = 1000L
            )
        )
        db.watchlistDao().upsert(
            WatchlistMovieEntity(
                movieId = 502,
                title = "Ant-Man",
                posterPath = null,
                voteAverage = 7.0,
                releaseDate = "2015-07-17",
                addedAt = 1000L
            )
        )

        fakeApi.favorites.clear()
        fakeApi.watchlist.clear()

        val result = repository.syncCloudWithLocal()
        assertThat(result).isEqualTo(Outcome.Success(Unit))

        assertThat(db.favoriteDao().getAllFavorites().first()).isEmpty()
        assertThat(db.watchlistDao().getAllWatchlist().first()).isEmpty()
    }

    @Test
    fun pull_preservesLocalFavorite_withPendingOutboxMutation() = runTest {
        sessionManager.sessionStateFlow.value = SessionState.Authenticated(
            accountId = 42,
            username = "adauto",
            sessionId = "sess_123"
        )

        // User toggled favorite locally: creates row in favorite table + outbox mutation
        repository.toggleFavorite(sampleMovie)
        assertThat(db.favoriteDao().isFavorite(sampleMovie.id).first()).isTrue()

        // Remote does not yet have sampleMovie
        fakeApi.favorites.clear()

        repository.syncCloudWithLocal()

        // Local favorite must be preserved despite remote not having it!
        val favIds = db.favoriteDao().getAllFavorites().first().map { it.movieId }
        assertThat(favIds).contains(sampleMovie.id)
    }

    @Test
    fun pull_preservesLocalWatchlist_withPendingOutboxMutation() = runTest {
        sessionManager.sessionStateFlow.value = SessionState.Authenticated(
            accountId = 42,
            username = "adauto",
            sessionId = "sess_123"
        )

        repository.toggleWatchlist(sampleMovie)
        assertThat(db.watchlistDao().isInWatchlist(sampleMovie.id).first()).isTrue()

        fakeApi.watchlist.clear()

        repository.syncCloudWithLocal()

        val watchIds = db.watchlistDao().getAllWatchlist().first().map { it.movieId }
        assertThat(watchIds).contains(sampleMovie.id)
    }

    @Test
    fun syncCloudWithLocal_whenApiFails_doesNotCorruptOrWipeLocalDatabase() = runTest {
        sessionManager.sessionStateFlow.value = SessionState.Authenticated(
            accountId = 42,
            username = "adauto",
            sessionId = "sess_123"
        )
        db.favoriteDao().upsert(
            FavoriteMovieEntity(
                movieId = 101,
                title = "Iron Man",
                posterPath = null,
                voteAverage = 8.2,
                releaseDate = "2008-05-02",
                addedAt = 1000L
            )
        )

        fakeApi.failureResponse = ApiResponse.Failure.Error(
            Response.error<Any>(500, "Internal Server Error".toResponseBody())
        )

        val result = repository.syncCloudWithLocal()
        assertThat(result).isInstanceOf(Outcome.Failure::class.java)

        // Database still contains original favorite intact
        val favorites = db.favoriteDao().getAllFavorites().first()
        assertThat(favorites.map { it.movieId }).containsExactly(101)
    }

    @Test
    fun syncCloudWithLocal_whenApiReturns403_clearsInvalidSessionAndPreservesLocalDatabase() = runTest {
        sessionManager.sessionStateFlow.value = SessionState.Authenticated(
            accountId = 42,
            username = "adauto",
            sessionId = "sess_stale"
        )
        db.favoriteDao().upsert(
            FavoriteMovieEntity(
                movieId = 101,
                title = "Iron Man",
                posterPath = null,
                voteAverage = 8.2,
                releaseDate = "2008-05-02",
                addedAt = 1000L
            )
        )

        fakeApi.failureResponse = ApiResponse.Failure.Error(
            Response.error<Any>(403, "Forbidden".toResponseBody())
        )

        val result = repository.syncCloudWithLocal()
        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        assertThat(sessionManager.sessionState.value).isEqualTo(SessionState.Guest)

        val favorites = db.favoriteDao().getAllFavorites().first()
        assertThat(favorites.map { it.movieId }).containsExactly(101)
    }

    @Test
    fun syncCloudWithLocal_whenGuest_returnsSuccessWithoutMakingNetworkCalls() = runTest {
        sessionManager.sessionStateFlow.value = SessionState.Guest

        val result = repository.syncCloudWithLocal()
        assertThat(result).isEqualTo(Outcome.Success(Unit))
        assertThat(db.favoriteDao().getAllFavorites().first()).isEmpty()
    }

    // endregion

    // region Movie Detail & Cache Fallback Tests

    @Test
    fun getMovieDetail_whenApiSucceeds_returnsMovieDetail() = runTest {
        fakeApi.movieDetails[101] = TmdbMovieDetailDto(
            id = 101,
            title = "Iron Man",
            overview = "Billionaire engineer Tony Stark...",
            posterPath = "/ironman.jpg",
            backdropPath = "/backdrop.jpg",
            voteAverage = 8.2,
            runtime = 126,
            releaseDate = "2008-05-02"
        )

        val result = repository.getMovieDetail(101)
        assertThat(result).isInstanceOf(Outcome.Success::class.java)

        val detail = (result as Outcome.Success).value
        assertThat(detail.id).isEqualTo(101)
        assertThat(detail.title).isEqualTo("Iron Man")
        assertThat(detail.runtime).isEqualTo(126)
    }

    @Test
    fun getMovieDetail_whenApiFails_andMovieInDb_returnsCachedDetail() = runTest {
        db.movieDao().upsertAll(
            listOf(
                MovieEntity(
                    id = 101,
                    category = "POPULAR",
                    position = 0,
                    title = "Iron Man (Cached)",
                    overview = "Cached overview",
                    posterPath = "/ironman.jpg",
                    backdropPath = "/backdrop.jpg",
                    voteAverage = 8.2,
                    runtime = 126,
                    releaseDate = "2008-05-02",
                    fetchedAt = 1000L
                )
            )
        )

        fakeApi.failureResponse = ApiResponse.Failure.Error(
            Response.error<Any>(500, "Server Error".toResponseBody())
        )

        val result = repository.getMovieDetail(101)
        assertThat(result).isInstanceOf(Outcome.Success::class.java)

        val detail = (result as Outcome.Success).value
        assertThat(detail.id).isEqualTo(101)
        assertThat(detail.title).isEqualTo("Iron Man (Cached)")
        assertThat(detail.overview).isEqualTo("Cached overview")
    }

    @Test
    fun getMovieDetail_whenApiFails_andNoDbCache_returnsFailure() = runTest {
        fakeApi.failureResponse = ApiResponse.Failure.Error(
            Response.error<Any>(404, "Not Found".toResponseBody())
        )

        val result = repository.getMovieDetail(999)
        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
    }

    // endregion

    // region Reactive Flows Tests

    @Test
    fun observeFavorites_and_observeWatchlist_emitUpdatedListsReactively() = runTest {
        sessionManager.sessionStateFlow.value = SessionState.Guest

        assertThat(repository.observeFavorites().first()).isEmpty()
        assertThat(repository.observeWatchlist().first()).isEmpty()

        repository.toggleFavorite(sampleMovie)
        val favs = repository.observeFavorites().first()
        assertThat(favs.map { it.id }).containsExactly(101)
        assertThat(favs.first().isFavorite).isTrue()
        assertThat(favs.first().isInWatchlist).isFalse()

        repository.toggleWatchlist(sampleMovie)
        val watch = repository.observeWatchlist().first()
        assertThat(watch.map { it.id }).containsExactly(101)
        assertThat(watch.first().isInWatchlist).isTrue()
        assertThat(watch.first().isFavorite).isTrue()

        val favsWithWatchlist = repository.observeFavorites().first()
        assertThat(favsWithWatchlist.first().isFavorite).isTrue()
        assertThat(favsWithWatchlist.first().isInWatchlist).isTrue()
    }

    // endregion
}
