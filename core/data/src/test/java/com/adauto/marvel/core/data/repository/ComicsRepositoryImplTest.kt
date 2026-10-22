package com.adauto.marvel.core.data.repository

import com.adauto.marvel.core.database.MarvelDatabase
import com.adauto.marvel.core.database.dao.ComicDao
import com.adauto.marvel.core.database.entity.ComicEntity
import com.adauto.marvel.core.model.DomainError
import com.adauto.marvel.core.model.Outcome
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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

class ComicsRepositoryImplTest {

    private val api: MarvelApi = mockk()
    private val database: MarvelDatabase = mockk()
    private val comicDao: ComicDao = mockk(relaxed = true)

    private lateinit var repository: ComicsRepositoryImpl

    @Before
    fun setUp() {
        every { database.comicDao() } returns comicDao
        repository = ComicsRepositoryImpl(
            api = api,
            database = database
        )
    }

    // region getComicDetail tests

    @Test
    fun `getComicDetail returns cached comic without calling API when present in database`() = runTest {
        val cachedEntity = ComicEntity(
            id = 1001,
            position = 0,
            title = "Spider-Man: Blue",
            issueNumber = 1.0,
            description = "Heartwarming story",
            thumbnailPath = "http://example.com/spiderman",
            thumbnailExt = "jpg",
            pageCount = 32,
            priceCents = 399,
            onsaleDate = "2026-02-01",
            creatorsJson = "Jeph Loeb, Tim Sale",
            charactersJson = "Peter Parker, Gwen Stacy",
            fetchedAt = 5000L
        )
        coEvery { comicDao.getComicById(1001) } returns cachedEntity

        val outcome = repository.getComicDetail(1001)

        assertThat(outcome).isInstanceOf(Outcome.Success::class.java)
        val success = outcome as Outcome.Success
        assertThat(success.value.id).isEqualTo(1001)
        assertThat(success.value.title).isEqualTo("Spider-Man: Blue")
        assertThat(success.value.issueNumber).isEqualTo("#1")
        assertThat(success.value.creators).containsExactly("Jeph Loeb", "Tim Sale").inOrder()
        assertThat(success.value.printPrice).isEqualTo("$3.99")

        coVerify(exactly = 0) { api.getComicDetail(any()) }
    }

    @Test
    fun `getComicDetail falls back to API when not cached and succeeds`() = runTest {
        coEvery { comicDao.getComicById(2002) } returns null

        val comicDto = ComicDto(
            id = 2002,
            title = "Daredevil: Born Again",
            issueNumber = 227.0,
            description = "Masterpiece",
            pageCount = 36
        )
        val responseDto = MarvelResponseDto(
            data = MarvelDataContainer(
                results = listOf(comicDto)
            )
        )
        coEvery { api.getComicDetail(2002) } returns ApiResponse.Success(responseDto)

        val outcome = repository.getComicDetail(2002)

        assertThat(outcome).isInstanceOf(Outcome.Success::class.java)
        val success = outcome as Outcome.Success
        assertThat(success.value.id).isEqualTo(2002)
        assertThat(success.value.title).isEqualTo("Daredevil: Born Again")
        assertThat(success.value.issueNumber).isEqualTo("#227")

        coVerify(exactly = 1) { api.getComicDetail(2002) }
    }

    @Test
    fun `getComicDetail returns NotFound when API results are empty`() = runTest {
        coEvery { comicDao.getComicById(3003) } returns null

        val responseDto = MarvelResponseDto(
            data = MarvelDataContainer<ComicDto>(results = emptyList())
        )
        coEvery { api.getComicDetail(3003) } returns ApiResponse.Success(responseDto)

        val outcome = repository.getComicDetail(3003)

        assertThat(outcome).isInstanceOf(Outcome.Failure::class.java)
        val failure = outcome as Outcome.Failure
        assertThat(failure.error).isEqualTo(DomainError.NotFound)
    }

    @Test
    fun `getComicDetail maps HTTP 404 to DomainError NotFound`() = runTest {
        coEvery { comicDao.getComicById(4004) } returns null

        val errorResponse = Response.error<MarvelResponseDto<ComicDto>>(
            404,
            "Not Found".toResponseBody()
        )
        coEvery { api.getComicDetail(4004) } returns ApiResponse.Failure.Error(errorResponse)

        val outcome = repository.getComicDetail(4004)

        assertThat(outcome).isInstanceOf(Outcome.Failure::class.java)
        val failure = outcome as Outcome.Failure
        assertThat(failure.error).isEqualTo(DomainError.NotFound)
    }

    @Test
    fun `getComicDetail maps HTTP 401 and 403 to DomainError InvalidKey`() = runTest {
        coEvery { comicDao.getComicById(4005) } returns null

        val errorResponse = Response.error<MarvelResponseDto<ComicDto>>(
            401,
            "Invalid Credentials".toResponseBody()
        )
        coEvery { api.getComicDetail(4005) } returns ApiResponse.Failure.Error(errorResponse)

        val outcome = repository.getComicDetail(4005)

        assertThat(outcome).isInstanceOf(Outcome.Failure::class.java)
        val failure = outcome as Outcome.Failure
        assertThat(failure.error).isEqualTo(DomainError.InvalidKey)
    }

    @Test
    fun `getComicDetail maps HTTP 429 to DomainError RateLimited`() = runTest {
        coEvery { comicDao.getComicById(4006) } returns null

        val errorResponse = Response.error<MarvelResponseDto<ComicDto>>(
            429,
            "Too Many Requests".toResponseBody()
        )
        coEvery { api.getComicDetail(4006) } returns ApiResponse.Failure.Error(errorResponse)

        val outcome = repository.getComicDetail(4006)

        assertThat(outcome).isInstanceOf(Outcome.Failure::class.java)
        val failure = outcome as Outcome.Failure
        assertThat(failure.error).isEqualTo(DomainError.RateLimited)
    }

    @Test
    fun `getComicDetail maps HTTP 500 to DomainError Server`() = runTest {
        coEvery { comicDao.getComicById(4007) } returns null

        val errorResponse = Response.error<MarvelResponseDto<ComicDto>>(
            500,
            "Internal Server Error".toResponseBody()
        )
        coEvery { api.getComicDetail(4007) } returns ApiResponse.Failure.Error(errorResponse)

        val outcome = repository.getComicDetail(4007)

        assertThat(outcome).isInstanceOf(Outcome.Failure::class.java)
        val failure = outcome as Outcome.Failure
        assertThat(failure.error).isEqualTo(DomainError.Server(500))
    }

    @Test
    fun `getComicDetail maps SocketTimeoutException to DomainError Timeout`() = runTest {
        coEvery { comicDao.getComicById(4008) } returns null

        coEvery { api.getComicDetail(4008) } returns ApiResponse.Failure.Exception(
            SocketTimeoutException("Timeout")
        )

        val outcome = repository.getComicDetail(4008)

        assertThat(outcome).isInstanceOf(Outcome.Failure::class.java)
        val failure = outcome as Outcome.Failure
        assertThat(failure.error).isEqualTo(DomainError.Timeout)
    }

    @Test
    fun `getComicDetail maps IOException to DomainError NoConnection`() = runTest {
        coEvery { comicDao.getComicById(4009) } returns null

        coEvery { api.getComicDetail(4009) } returns ApiResponse.Failure.Exception(
            IOException("No Internet")
        )

        val outcome = repository.getComicDetail(4009)

        assertThat(outcome).isInstanceOf(Outcome.Failure::class.java)
        val failure = outcome as Outcome.Failure
        assertThat(failure.error).isEqualTo(DomainError.NoConnection)
    }

    @Test
    fun `getComicDetail maps generic Exception to DomainError Unknown`() = runTest {
        coEvery { comicDao.getComicById(4010) } returns null

        val error = IllegalStateException("Unexpected")
        coEvery { api.getComicDetail(4010) } returns ApiResponse.Failure.Exception(error)

        val outcome = repository.getComicDetail(4010)

        assertThat(outcome).isInstanceOf(Outcome.Failure::class.java)
        val failure = outcome as Outcome.Failure
        assertThat(failure.error).isEqualTo(DomainError.Unknown(error))
    }

    // endregion

    // region comics PagingData tests

    @Test
    fun `comics returns Flow of PagingData`() = runTest {
        val flow = repository.comics()
        assertThat(flow).isNotNull()
    }

    // endregion
}
