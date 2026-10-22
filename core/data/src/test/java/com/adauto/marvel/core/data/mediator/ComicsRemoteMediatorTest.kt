package com.adauto.marvel.core.data.mediator

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.adauto.marvel.core.database.MarvelDatabase
import com.adauto.marvel.core.database.dao.ComicDao
import com.adauto.marvel.core.database.dao.RemoteKeyDao
import com.adauto.marvel.core.database.entity.ComicEntity
import com.adauto.marvel.core.database.entity.RemoteKeyEntity
import com.adauto.marvel.core.model.MarvelApiException
import com.adauto.marvel.core.network.api.MarvelApi
import com.adauto.marvel.core.network.model.ComicDto
import com.adauto.marvel.core.network.model.MarvelDataContainer
import com.adauto.marvel.core.network.model.MarvelResponseDto
import com.google.common.truth.Truth.assertThat
import com.skydoves.sandwich.ApiResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalPagingApi::class)
class ComicsRemoteMediatorTest {

    private val api: MarvelApi = mockk()
    private val database: MarvelDatabase = mockk()
    private val comicDao: ComicDao = mockk(relaxed = true)
    private val remoteKeyDao: RemoteKeyDao = mockk(relaxed = true)

    private val fixedInstant = Instant.parse("2026-10-22T12:00:00Z")
    private val fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    private lateinit var mediator: ComicsRemoteMediator

    private val pagingState = PagingState<Int, ComicEntity>(
        pages = emptyList(),
        anchorPosition = null,
        config = PagingConfig(pageSize = 20),
        leadingPlaceholderCount = 0
    )

    @Before
    fun setUp() {
        every { database.comicDao() } returns comicDao
        every { database.remoteKeyDao() } returns remoteKeyDao

        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { database.withTransaction(any<suspend () -> Any>()) } coAnswers {
            val block = secondArg<suspend () -> Any>()
            block()
        }

        mediator = ComicsRemoteMediator(
            api = api,
            database = database,
            clock = fixedClock,
            cacheTtlHours = 6L
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // region initialize tests

    @Test
    fun `initialize returns SKIP_INITIAL_REFRESH when cache is younger than 6 hours`() = runTest {
        val lastFetched = fixedClock.millis() - TimeUnit.HOURS.toMillis(5)
        coEvery { comicDao.lastFetchedAt() } returns lastFetched

        val action = mediator.initialize()

        assertThat(action).isEqualTo(RemoteMediator.InitializeAction.SKIP_INITIAL_REFRESH)
    }

    @Test
    fun `initialize returns LAUNCH_INITIAL_REFRESH when cache is older than 6 hours`() = runTest {
        val lastFetched = fixedClock.millis() - TimeUnit.HOURS.toMillis(7)
        coEvery { comicDao.lastFetchedAt() } returns lastFetched

        val action = mediator.initialize()

        assertThat(action).isEqualTo(RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH)
    }

    @Test
    fun `initialize returns LAUNCH_INITIAL_REFRESH when cache is empty`() = runTest {
        coEvery { comicDao.lastFetchedAt() } returns null

        val action = mediator.initialize()

        assertThat(action).isEqualTo(RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH)
    }

    // endregion

    // region load REFRESH tests

    @Test
    fun `refresh load returns Success and clears cache when api returns data`() = runTest {
        val comics = listOf(
            ComicDto(id = 101, title = "Iron Man #1", issueNumber = 1.0),
            ComicDto(id = 102, title = "Iron Man #2", issueNumber = 2.0)
        )
        val responseDto = MarvelResponseDto(
            data = MarvelDataContainer(
                offset = 0,
                limit = 20,
                total = 100,
                count = 2,
                results = comics
            )
        )
        coEvery { api.getComics(offset = 0, limit = 20) } returns ApiResponse.Success(responseDto)

        val result = mediator.load(LoadType.REFRESH, pagingState)

        assertThat(result).isInstanceOf(RemoteMediator.MediatorResult.Success::class.java)
        val success = result as RemoteMediator.MediatorResult.Success
        assertThat(success.endOfPaginationReached).isFalse()

        coVerify(exactly = 1) { comicDao.clearAll() }
        coVerify(exactly = 1) { remoteKeyDao.clearAll() }

        val entitiesSlot = slot<List<ComicEntity>>()
        coVerify(exactly = 1) { comicDao.upsert(capture(entitiesSlot)) }
        val capturedEntities = entitiesSlot.captured
        assertThat(capturedEntities).hasSize(2)
        assertThat(capturedEntities[0].id).isEqualTo(101)
        assertThat(capturedEntities[0].position).isEqualTo(0)
        assertThat(capturedEntities[1].id).isEqualTo(102)
        assertThat(capturedEntities[1].position).isEqualTo(1)

        val keysSlot = slot<List<RemoteKeyEntity>>()
        coVerify(exactly = 1) { remoteKeyDao.upsert(capture(keysSlot)) }
        val capturedKeys = keysSlot.captured
        assertThat(capturedKeys).hasSize(2)
        assertThat(capturedKeys[0].comicId).isEqualTo(101)
        assertThat(capturedKeys[0].nextOffset).isEqualTo(2)
        assertThat(capturedKeys[1].comicId).isEqualTo(102)
        assertThat(capturedKeys[1].nextOffset).isEqualTo(2)
    }

    @Test
    fun `refresh load returns Success with endOfPaginationReached true when total reached`() = runTest {
        val comics = listOf(
            ComicDto(id = 201, title = "Thor #1", issueNumber = 1.0)
        )
        val responseDto = MarvelResponseDto(
            data = MarvelDataContainer(
                offset = 0,
                limit = 20,
                total = 1,
                count = 1,
                results = comics
            )
        )
        coEvery { api.getComics(offset = 0, limit = 20) } returns ApiResponse.Success(responseDto)

        val result = mediator.load(LoadType.REFRESH, pagingState)

        assertThat(result).isInstanceOf(RemoteMediator.MediatorResult.Success::class.java)
        val success = result as RemoteMediator.MediatorResult.Success
        assertThat(success.endOfPaginationReached).isTrue()

        val keysSlot = slot<List<RemoteKeyEntity>>()
        coVerify(exactly = 1) { remoteKeyDao.upsert(capture(keysSlot)) }
        assertThat(keysSlot.captured[0].nextOffset).isNull()
    }

    @Test
    fun `refresh load returns Error when api returns error response`() = runTest {
        val errorResponse = Response.error<MarvelResponseDto<ComicDto>>(
            500,
            "Internal Server Error".toResponseBody()
        )
        coEvery { api.getComics(offset = 0, limit = 20) } returns ApiResponse.Failure.Error(errorResponse)

        val result = mediator.load(LoadType.REFRESH, pagingState)

        assertThat(result).isInstanceOf(RemoteMediator.MediatorResult.Error::class.java)
        val errorResult = result as RemoteMediator.MediatorResult.Error
        assertThat(errorResult.throwable).isInstanceOf(MarvelApiException::class.java)
        val apiException = errorResult.throwable as MarvelApiException
        assertThat(apiException.code).isEqualTo(500)
        coVerify(exactly = 0) { comicDao.clearAll() }
        coVerify(exactly = 0) { comicDao.upsert(any()) }
    }

    @Test
    fun `refresh load returns Error with code 429 when api returns rate limit`() = runTest {
        val errorResponse = Response.error<MarvelResponseDto<ComicDto>>(
            429,
            "Too Many Requests".toResponseBody()
        )
        coEvery { api.getComics(offset = 0, limit = 20) } returns ApiResponse.Failure.Error(errorResponse)

        val result = mediator.load(LoadType.REFRESH, pagingState)

        assertThat(result).isInstanceOf(RemoteMediator.MediatorResult.Error::class.java)
        val errorResult = result as RemoteMediator.MediatorResult.Error
        assertThat(errorResult.throwable).isInstanceOf(MarvelApiException::class.java)
        val apiException = errorResult.throwable as MarvelApiException
        assertThat(apiException.code).isEqualTo(429)
    }

    @Test
    fun `refresh load returns Error when api throws exception`() = runTest {
        coEvery { api.getComics(offset = 0, limit = 20) } returns ApiResponse.Failure.Exception(
            IOException("Connection refused")
        )

        val result = mediator.load(LoadType.REFRESH, pagingState)

        assertThat(result).isInstanceOf(RemoteMediator.MediatorResult.Error::class.java)
        val errorResult = result as RemoteMediator.MediatorResult.Error
        assertThat(errorResult.throwable).isInstanceOf(IOException::class.java)
    }

    // endregion

    // region load APPEND tests

    @Test
    fun `append load queries lastNextOffset and fetches next page without clearing cache`() = runTest {
        coEvery { remoteKeyDao.lastNextOffset() } returns 20

        val comics = listOf(
            ComicDto(id = 301, title = "Captain America #21", issueNumber = 21.0)
        )
        val responseDto = MarvelResponseDto(
            data = MarvelDataContainer(
                offset = 20,
                limit = 20,
                total = 100,
                count = 1,
                results = comics
            )
        )
        coEvery { api.getComics(offset = 20, limit = 20) } returns ApiResponse.Success(responseDto)

        val result = mediator.load(LoadType.APPEND, pagingState)

        assertThat(result).isInstanceOf(RemoteMediator.MediatorResult.Success::class.java)
        coVerify(exactly = 0) { comicDao.clearAll() }
        coVerify(exactly = 0) { remoteKeyDao.clearAll() }

        val entitiesSlot = slot<List<ComicEntity>>()
        coVerify(exactly = 1) { comicDao.upsert(capture(entitiesSlot)) }
        assertThat(entitiesSlot.captured[0].position).isEqualTo(20) // offset + index = 20 + 0

        val keysSlot = slot<List<RemoteKeyEntity>>()
        coVerify(exactly = 1) { remoteKeyDao.upsert(capture(keysSlot)) }
        assertThat(keysSlot.captured[0].nextOffset).isEqualTo(21)
    }

    @Test
    fun `append load returns Success with endOfPagination reached when lastNextOffset is null`() = runTest {
        coEvery { remoteKeyDao.lastNextOffset() } returns null

        val result = mediator.load(LoadType.APPEND, pagingState)

        assertThat(result).isInstanceOf(RemoteMediator.MediatorResult.Success::class.java)
        val success = result as RemoteMediator.MediatorResult.Success
        assertThat(success.endOfPaginationReached).isTrue()

        coVerify(exactly = 0) { api.getComics(any(), any()) }
    }

    // endregion

    // region load PREPEND tests

    @Test
    fun `prepend load returns Success with endOfPaginationReached true immediately`() = runTest {
        val result = mediator.load(LoadType.PREPEND, pagingState)

        assertThat(result).isInstanceOf(RemoteMediator.MediatorResult.Success::class.java)
        val success = result as RemoteMediator.MediatorResult.Success
        assertThat(success.endOfPaginationReached).isTrue()

        coVerify(exactly = 0) { api.getComics(any(), any()) }
    }

    // endregion
}
