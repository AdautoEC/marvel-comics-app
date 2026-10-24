package com.adauto.marvel.core.data.mediator

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.adauto.marvel.core.database.MarvelDatabase
import com.adauto.marvel.core.database.dao.MovieDao
import com.adauto.marvel.core.database.entity.MovieEntity
import com.adauto.marvel.core.database.model.MovieWithUserData
import com.adauto.marvel.core.network.api.TmdbApi
import com.adauto.marvel.core.network.model.TmdbMovieDto
import com.adauto.marvel.core.network.model.TmdbPageResponseDto
import com.google.common.truth.Truth.assertThat
import com.skydoves.sandwich.ApiResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalPagingApi::class)
class MoviesRemoteMediatorTest {

    private val api: TmdbApi = mockk()
    private val database: MarvelDatabase = mockk()
    private val movieDao: MovieDao = mockk(relaxed = true)
    private val fixedClock = Clock.fixed(Instant.ofEpochMilli(1_000_000L), ZoneOffset.UTC)

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

        mediator = MoviesRemoteMediator(
            api = api,
            database = database,
            category = "POPULAR",
            clock = fixedClock,
            cacheTtlHours = 6L
        )
    }

    private fun createMovieWithUserData(position: Int): MovieWithUserData {
        return MovieWithUserData(
            movie = MovieEntity(
                id = position + 1,
                category = "POPULAR",
                position = position,
                title = "Movie ${position + 1}",
                overview = "Overview",
                posterPath = null,
                backdropPath = null,
                voteAverage = 7.0,
                releaseDate = "2024-01-01",
                fetchedAt = 1_000_000L
            ),
            isFavorite = false,
            isWatched = false
        )
    }

    @Test
    fun load_append_calculatesNextPageFromLastItemMoviePosition() = runTest {
        val lastItem = createMovieWithUserData(position = 39) // page 2 item (0-based)
        val state = PagingState<Int, MovieWithUserData>(
            pages = listOf(
                PagingSource.LoadResult.Page(
                    data = listOf(lastItem),
                    prevKey = null,
                    nextKey = 2
                )
            ),
            anchorPosition = null,
            config = PagingConfig(pageSize = 20),
            leadingPlaceholderCount = 0
        )

        val page3Dto = TmdbMovieDto(
            id = 41,
            title = "Movie 41",
            overview = "Overview",
            posterPath = null,
            backdropPath = null,
            voteAverage = 8.0,
            releaseDate = "2024-01-01"
        )
        coEvery {
            api.getPopularMovies(page = 3)
        } returns ApiResponse.Success(
            TmdbPageResponseDto(
                page = 3,
                results = listOf(page3Dto),
                totalPages = 5,
                totalResults = 100
            )
        )

        val result = mediator.load(LoadType.APPEND, state)

        assertThat(result).isInstanceOf(RemoteMediator.MediatorResult.Success::class.java)
        val success = result as RemoteMediator.MediatorResult.Success
        assertThat(success.endOfPaginationReached).isFalse()

        coVerify(exactly = 1) { api.getPopularMovies(page = 3) }
        coVerify(exactly = 1) { movieDao.upsertAll(any()) }
    }

    @Test
    fun load_append_returnsEndOfPaginationReachedWhenLastItemIsNull() = runTest {
        val state = PagingState<Int, MovieWithUserData>(
            pages = emptyList(),
            anchorPosition = null,
            config = PagingConfig(pageSize = 20),
            leadingPlaceholderCount = 0
        )

        val result = mediator.load(LoadType.APPEND, state)

        assertThat(result).isInstanceOf(RemoteMediator.MediatorResult.Success::class.java)
        val success = result as RemoteMediator.MediatorResult.Success
        assertThat(success.endOfPaginationReached).isTrue()
        coVerify(exactly = 0) { api.getPopularMovies(any()) }
    }
}
