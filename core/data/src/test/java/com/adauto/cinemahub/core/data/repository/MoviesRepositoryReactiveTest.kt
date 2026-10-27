package com.adauto.cinemahub.core.data.repository

import android.content.Context
import androidx.paging.testing.asSnapshot
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.adauto.cinemahub.core.data.fake.FakeSessionManager
import com.adauto.cinemahub.core.data.fake.FakeTmdbApi
import com.adauto.cinemahub.core.data.sync.SyncCoordinator
import com.adauto.cinemahub.core.database.CinemaHubDatabase
import com.adauto.cinemahub.core.database.entity.FavoriteMovieEntity
import com.adauto.cinemahub.core.database.entity.MovieEntity
import com.adauto.cinemahub.core.database.entity.MovieRemoteKeysEntity
import com.adauto.cinemahub.core.database.entity.WatchlistMovieEntity
import com.adauto.cinemahub.core.model.MovieCategory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class MoviesRepositoryReactiveTest {

    private lateinit var db: CinemaHubDatabase
    private lateinit var fakeApi: FakeTmdbApi
    private lateinit var fakeSessionManager: FakeSessionManager
    private lateinit var workManager: WorkManager
    private val syncCoordinator = SyncCoordinator()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC)

    private lateinit var repository: MoviesRepositoryImpl

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
        fakeSessionManager = FakeSessionManager()

        repository = MoviesRepositoryImpl(
            api = fakeApi,
            database = db,
            sessionManager = fakeSessionManager,
            workManager = workManager,
            syncCoordinator = syncCoordinator,
            externalScope = TestScope(),
            clock = clock
        )
    }

    @After
    fun tearDown() {
        db.close()
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    private fun createMovieEntity(id: Int, position: Int): MovieEntity = MovieEntity(
        id = id,
        category = "POPULAR",
        position = position,
        title = "Movie $id",
        overview = "Overview $id",
        posterPath = "/poster$id.jpg",
        backdropPath = "/backdrop$id.jpg",
        voteAverage = 8.0,
        releaseDate = "2024-01-01",
        fetchedAt = clock.millis()
    )

    @Test
    fun movies_mapsRoomProjectionWithFavoriteAndWatchlistStateCorrectly() = runTest {
        db.movieDao().upsertAll(
            listOf(
                createMovieEntity(id = 1, position = 0),
                createMovieEntity(id = 2, position = 1),
                createMovieEntity(id = 3, position = 2),
                createMovieEntity(id = 4, position = 3)
            )
        )
        db.movieRemoteKeysDao().insertOrReplace(
            MovieRemoteKeysEntity(
                label = "POPULAR",
                nextPage = null,
                totalPages = 1,
                updatedAt = clock.millis()
            )
        )
        db.favoriteDao().upsert(
            FavoriteMovieEntity(2, "Movie 2", "/poster2.jpg", 8.0, "2024-01-01", 1000L)
        )
        db.favoriteDao().upsert(
            FavoriteMovieEntity(4, "Movie 4", "/poster4.jpg", 8.0, "2024-01-01", 1000L)
        )
        db.watchlistDao().upsert(
            WatchlistMovieEntity(3, "Movie 3", "/poster3.jpg", 8.0, "2024-01-01", 1000L)
        )
        db.watchlistDao().upsert(
            WatchlistMovieEntity(4, "Movie 4", "/poster4.jpg", 8.0, "2024-01-01", 1000L)
        )

        val snapshot = repository.movies(MovieCategory.POPULAR).asSnapshot()

        assertThat(snapshot).hasSize(4)

        assertThat(snapshot[0].id).isEqualTo(1)
        assertThat(snapshot[0].isFavorite).isFalse()
        assertThat(snapshot[0].isInWatchlist).isFalse()

        assertThat(snapshot[1].id).isEqualTo(2)
        assertThat(snapshot[1].isFavorite).isTrue()
        assertThat(snapshot[1].isInWatchlist).isFalse()

        assertThat(snapshot[2].id).isEqualTo(3)
        assertThat(snapshot[2].isFavorite).isFalse()
        assertThat(snapshot[2].isInWatchlist).isTrue()

        assertThat(snapshot[3].id).isEqualTo(4)
        assertThat(snapshot[3].isFavorite).isTrue()
        assertThat(snapshot[3].isInWatchlist).isTrue()
    }
}
