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
import com.adauto.cinemahub.core.database.entity.MovieEntity
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
class MoviesRepositorySearchTest {

    private lateinit var db: CinemaHubDatabase
    private lateinit var fakeApi: FakeTmdbApi
    private lateinit var fakeSessionManager: FakeSessionManager
    private lateinit var workManager: WorkManager
    private val syncCoordinator = SyncCoordinator()
    private val fixedClock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC)

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
            clock = fixedClock
        )
    }

    @After
    fun tearDown() {
        db.close()
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test
    fun movies_withSearchQuery_filtersByTitleCaseInsensitiveAndTrimmedInRoom() = runTest {
        db.movieDao().upsertAll(
            listOf(
                MovieEntity(
                    id = 1,
                    category = "POPULAR",
                    position = 0,
                    title = "Spider-Man: Homecoming",
                    overview = "Peter Parker",
                    posterPath = "/spider1.jpg",
                    backdropPath = "/bg1.jpg",
                    voteAverage = 7.4,
                    releaseDate = "2017-07-05",
                    fetchedAt = 1000L
                ),
                MovieEntity(
                    id = 2,
                    category = "POPULAR",
                    position = 1,
                    title = "Iron Man",
                    overview = "Tony Stark",
                    posterPath = "/iron.jpg",
                    backdropPath = "/bg2.jpg",
                    voteAverage = 7.9,
                    releaseDate = "2008-05-02",
                    fetchedAt = 1000L
                ),
                MovieEntity(
                    id = 3,
                    category = "POPULAR",
                    position = 2,
                    title = "The Amazing Spider-Man",
                    overview = "Andrew Garfield",
                    posterPath = "/spider2.jpg",
                    backdropPath = "/bg3.jpg",
                    voteAverage = 6.9,
                    releaseDate = "2012-06-23",
                    fetchedAt = 1000L
                )
            )
        )

        val snapshot = repository.movies(MovieCategory.POPULAR, "  spider  ").asSnapshot()

        assertThat(snapshot.map { it.id }).containsExactly(1, 3).inOrder()
    }
}
