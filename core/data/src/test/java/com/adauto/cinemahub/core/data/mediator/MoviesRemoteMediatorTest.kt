package com.adauto.cinemahub.core.data.mediator

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.adauto.cinemahub.core.database.CinemaHubDatabase
import com.adauto.cinemahub.core.database.dao.MovieDao
import com.adauto.cinemahub.core.database.dao.MovieRemoteKeysDao
import com.adauto.cinemahub.core.database.entity.MovieEntity
import com.adauto.cinemahub.core.database.entity.MovieRemoteKeysEntity
import com.adauto.cinemahub.core.database.model.MovieWithUserData
import com.adauto.cinemahub.core.network.api.TmdbApi
import com.adauto.cinemahub.core.network.model.TmdbMovieDto
import com.adauto.cinemahub.core.network.model.TmdbPageResponseDto
import com.google.common.truth.Truth.assertThat
import com.skydoves.sandwich.ApiResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalPagingApi::class)
class MoviesRemoteMediatorTest {

    private val api: TmdbApi = mockk()
    private val database: CinemaHubDatabase = mockk()
    private val movieDao: MovieDao = mockk(relaxed = true)
    private val remoteKeysDao: MovieRemoteKeysDao = mockk(relaxed = true)
    private val fixedClock = Clock.fixed(Instant.ofEpochMilli(10_000_000L), ZoneOffset.UTC)

    private lateinit var mediator: MoviesRemoteMediator

    @Before
    fun setUp() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { database.withTransaction(any<suspend () -> Any>()) } coAnswers {
            @Suppress("UNCHECKED_CAST")
            val block = it.invocation.args[1] as suspend () -> Any
            block()
        }
        every { database.movieDao() } returns movieDao
        every { database.movieRemoteKeysDao() } returns remoteKeysDao

        mediator = MoviesRemoteMediator(
            api = api,
            database = database,
            category = "POPULAR",
            clock = fixedClock,
            cacheTtlHours = 6L
        )
    }

    private fun createPagingState(): PagingState<Int, MovieWithUserData> {
        return PagingState(
            pages = emptyList(),
            anchorPosition = null,
            config = PagingConfig(pageSize = 20),
            leadingPlaceholderCount = 0
        )
    }

    @Test
    fun initialize_whenRemoteKeyMissing_returnsLaunchInitialRefresh() = runTest {
        coEvery { remoteKeysDao.getKey("POPULAR") } returns null

        val action = mediator.initialize()

        assertThat(action).isEqualTo(RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH)
    }

    @Test
    fun initialize_whenRemoteKeyFresh_returnsSkipInitialRefresh() = runTest {
        val now = fixedClock.millis()
        val freshTime = now - TimeUnit.HOURS.toMillis(2) // 2 hours old, TTL is 6h
        coEvery { remoteKeysDao.getKey("POPULAR") } returns MovieRemoteKeysEntity(
            label = "POPULAR",
            nextPage = 2,
            totalPages = 10,
            updatedAt = freshTime
        )

        val action = mediator.initialize()

        assertThat(action).isEqualTo(RemoteMediator.InitializeAction.SKIP_INITIAL_REFRESH)
    }

    @Test
    fun initialize_whenRemoteKeyExpired_returnsLaunchInitialRefresh() = runTest {
        val now = fixedClock.millis()
        val expiredTime = now - TimeUnit.HOURS.toMillis(8) // 8 hours old, TTL is 6h
        coEvery { remoteKeysDao.getKey("POPULAR") } returns MovieRemoteKeysEntity(
            label = "POPULAR",
            nextPage = 2,
            totalPages = 10,
            updatedAt = expiredTime
        )

        val action = mediator.initialize()

        assertThat(action).isEqualTo(RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH)
    }

    @Test
    fun load_refresh_clearsCategoryAndKeyInSameTransaction_andInsertsRemoteKey() = runTest {
        val movieDto = TmdbMovieDto(id = 1, title = "M1", overview = "O", voteAverage = 7.0, releaseDate = "2024")
        coEvery { api.getPopularMovies(page = 1) } returns ApiResponse.Success(
            TmdbPageResponseDto(page = 1, results = listOf(movieDto), totalPages = 5, totalResults = 100)
        )

        val result = mediator.load(LoadType.REFRESH, createPagingState())

        assertThat(result).isInstanceOf(RemoteMediator.MediatorResult.Success::class.java)
        val success = result as RemoteMediator.MediatorResult.Success
        assertThat(success.endOfPaginationReached).isFalse()

        coVerify(exactly = 1) { movieDao.clearCategory("POPULAR") }
        coVerify(exactly = 1) { remoteKeysDao.deleteKey("POPULAR") }
        coVerify(exactly = 1) { movieDao.upsertAll(any()) }
        coVerify(exactly = 1) {
            remoteKeysDao.insertOrReplace(
                MovieRemoteKeysEntity(
                    label = "POPULAR",
                    nextPage = 2,
                    totalPages = 5,
                    updatedAt = fixedClock.millis()
                )
            )
        }
    }

    @Test
    fun load_append_readsNextPageFromMovieRemoteKeys() = runTest {
        coEvery { remoteKeysDao.getKey("POPULAR") } returns MovieRemoteKeysEntity(
            label = "POPULAR",
            nextPage = 3,
            totalPages = 5,
            updatedAt = fixedClock.millis()
        )
        val movieDto = TmdbMovieDto(id = 41, title = "M41", overview = "O", voteAverage = 8.0, releaseDate = "2024")
        coEvery { api.getPopularMovies(page = 3) } returns ApiResponse.Success(
            TmdbPageResponseDto(page = 3, results = listOf(movieDto), totalPages = 5, totalResults = 100)
        )

        val result = mediator.load(LoadType.APPEND, createPagingState())

        assertThat(result).isInstanceOf(RemoteMediator.MediatorResult.Success::class.java)
        val success = result as RemoteMediator.MediatorResult.Success
        assertThat(success.endOfPaginationReached).isFalse()

        coVerify(exactly = 1) { api.getPopularMovies(page = 3) }
        coVerify(exactly = 1) {
            remoteKeysDao.insertOrReplace(
                MovieRemoteKeysEntity(
                    label = "POPULAR",
                    nextPage = 4,
                    totalPages = 5,
                    updatedAt = fixedClock.millis()
                )
            )
        }
    }

    @Test
    fun load_append_returnsEndOfPaginationReachedWhenNextPageIsNull() = runTest {
        coEvery { remoteKeysDao.getKey("POPULAR") } returns MovieRemoteKeysEntity(
            label = "POPULAR",
            nextPage = null,
            totalPages = 5,
            updatedAt = fixedClock.millis()
        )

        val result = mediator.load(LoadType.APPEND, createPagingState())

        assertThat(result).isInstanceOf(RemoteMediator.MediatorResult.Success::class.java)
        val success = result as RemoteMediator.MediatorResult.Success
        assertThat(success.endOfPaginationReached).isTrue()
        coVerify(exactly = 0) { api.getPopularMovies(any()) }
    }

    @Test
    fun load_whenPageSizeIsSmallerThanTmdbPageSize_computesPositionBasedOnTmdbConstant() = runTest {
        coEvery { remoteKeysDao.getKey("POPULAR") } returns MovieRemoteKeysEntity(
            label = "POPULAR",
            nextPage = 2,
            totalPages = 5,
            updatedAt = fixedClock.millis()
        )
        val movieDto = TmdbMovieDto(id = 100, title = "M100", overview = "O", voteAverage = 8.0, releaseDate = "2024")
        coEvery { api.getPopularMovies(page = 2) } returns ApiResponse.Success(
            TmdbPageResponseDto(page = 2, results = listOf(movieDto), totalPages = 5, totalResults = 100)
        )

        // Paging config has pageSize = 10 (smaller than TMDB 20)
        val stateWithSmallPage = PagingState<Int, MovieWithUserData>(
            pages = emptyList(),
            anchorPosition = null,
            config = PagingConfig(pageSize = 10),
            leadingPlaceholderCount = 0
        )

        mediator.load(LoadType.APPEND, stateWithSmallPage)

        val insertedSlot = slot<List<MovieEntity>>()
        coVerify(atLeast = 1) { movieDao.upsertAll(capture(insertedSlot)) }
        // Page 2 start position must be (2 - 1) * 20 = 20, NOT (2 - 1) * 10 = 10
        assertThat(insertedSlot.captured.first().position).isEqualTo(20)
    }
}
