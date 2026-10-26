package com.adauto.marvel.core.data.repository

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.testing.asPagingSourceFactory
import androidx.paging.testing.asSnapshot
import androidx.work.WorkManager
import com.adauto.marvel.core.data.sync.SyncCoordinator
import com.adauto.marvel.core.database.MarvelDatabase
import com.adauto.marvel.core.database.dao.FavoriteDao
import com.adauto.marvel.core.database.dao.MovieDao
import com.adauto.marvel.core.database.dao.MovieRemoteKeysDao
import com.adauto.marvel.core.database.dao.WatchlistDao
import com.adauto.marvel.core.database.entity.MovieEntity
import com.adauto.marvel.core.database.model.MovieWithUserData
import com.adauto.marvel.core.model.MovieCategory
import com.adauto.marvel.core.network.api.TmdbApi
import com.adauto.marvel.core.network.security.session.SessionManager
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Clock
import androidx.room.withTransaction

class MoviesRepositoryReactiveTest {

    private val api: TmdbApi = mockk(relaxed = true)
    private val database: MarvelDatabase = mockk()
    private val movieDao: MovieDao = mockk(relaxed = true)
    private val favoriteDao: FavoriteDao = mockk(relaxed = true)
    private val watchlistDao: WatchlistDao = mockk(relaxed = true)
    private val movieRemoteKeysDao: MovieRemoteKeysDao = mockk(relaxed = true)
    private val sessionManager: SessionManager = mockk(relaxed = true)
    private val workManager: WorkManager = mockk(relaxed = true)
    private val syncCoordinator = SyncCoordinator()
    private val clock = Clock.systemUTC()

    private lateinit var repository: MoviesRepositoryImpl

    @Before
    fun setUp() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { database.withTransaction(any<suspend () -> Any>()) } coAnswers {
            @Suppress("UNCHECKED_CAST")
            val block = it.invocation.args[1] as suspend () -> Any
            block()
        }
        coEvery {
            api.getPopularMovies(any())
        } returns com.skydoves.sandwich.ApiResponse.Success(
            com.adauto.marvel.core.network.model.TmdbPageResponseDto(
                page = 1,
                results = emptyList(),
                totalPages = 1,
                totalResults = 0
            )
        )
        every { database.movieDao() } returns movieDao
        every { database.favoriteDao() } returns favoriteDao
        every { database.watchlistDao() } returns watchlistDao
        every { database.movieRemoteKeysDao() } returns movieRemoteKeysDao

        repository = MoviesRepositoryImpl(
            api = api,
            database = database,
            sessionManager = sessionManager,
            workManager = workManager,
            syncCoordinator = syncCoordinator,
            clock = clock
        )
    }

    private fun createMovieWithUserData(
        id: Int,
        category: String = "POPULAR",
        position: Int = 0,
        isFavorite: Boolean = false,
        isInWatchlist: Boolean = false
    ): MovieWithUserData {
        return MovieWithUserData(
            movie = MovieEntity(
                id = id,
                category = category,
                position = position,
                title = "Movie $id",
                overview = "Overview $id",
                posterPath = "/poster$id.jpg",
                backdropPath = "/backdrop$id.jpg",
                voteAverage = 8.0,
                releaseDate = "2024-01-01",
                fetchedAt = 1000L
            ),
            isFavorite = isFavorite,
            isInWatchlist = isInWatchlist
        )
    }

    @Test
    fun movies_mapsProjectionDirectlyWithoutQueryingFavoriteOrWatchedDaos() = runTest {
        val testData = listOf(
            createMovieWithUserData(id = 1, position = 0, isFavorite = false, isInWatchlist = false),
            createMovieWithUserData(id = 2, position = 1, isFavorite = true, isInWatchlist = false),
            createMovieWithUserData(id = 3, position = 2, isFavorite = false, isInWatchlist = true),
            createMovieWithUserData(id = 4, position = 3, isFavorite = true, isInWatchlist = true)
        )

        val pagingSourceFactory = testData.asPagingSourceFactory()
        every { movieDao.pagingSource("POPULAR") } answers { pagingSourceFactory() }

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

        // Verify N+1 queries were completely eliminated
        coVerify(exactly = 0) { favoriteDao.isFavorite(any()) }
        coVerify(exactly = 0) { watchlistDao.isInWatchlist(any()) }
    }
}
