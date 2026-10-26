package com.adauto.cinemahub.core.network.api

import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.retrofit.adapters.ApiResponseCallAdapterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class TmdbApiTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var api: TmdbApi

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        api = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .addCallAdapterFactory(ApiResponseCallAdapterFactory.create())
            .build()
            .create(TmdbApi::class.java)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun getPopularMovies_returnsApiResponseSuccess_withParsedResults() = runTest {
        val mockBody = """
            {
                "page": 1,
                "results": [
                    {
                        "id": 969681,
                        "title": "Spider-Man: Brand New Day",
                        "overview": "Peter Parker battles a mysterious villain.",
                        "poster_path": "/bjiS5ipwxb9JFy3XRRN4OAilSeX.jpg",
                        "backdrop_path": "/r08LxMJCbf1SiJIWeyH9aJmdqwC.jpg",
                        "vote_average": 8.5,
                        "release_date": "2026-07-29"
                    }
                ],
                "total_pages": 50,
                "total_results": 1000
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(mockBody))

        val response = api.getPopularMovies(page = 1)
        assertTrue(response is ApiResponse.Success)
        val data = (response as ApiResponse.Success).data
        assertEquals(1, data.page)
        assertEquals(50, data.totalPages)
        assertEquals(1, data.results.size)
        assertEquals(969681, data.results[0].id)
        assertEquals("Spider-Man: Brand New Day", data.results[0].title)
    }

    @Test
    fun getMovieDetail_returnsApiResponseSuccess_withCreditsAndGenres() = runTest {
        val mockBody = """
            {
                "id": 969681,
                "title": "Spider-Man: Brand New Day",
                "overview": "Detailed synopsis...",
                "poster_path": "/poster.jpg",
                "backdrop_path": "/backdrop.jpg",
                "vote_average": 8.5,
                "runtime": 142,
                "release_date": "2026-07-29",
                "genres": [
                    {"id": 28, "name": "Ação"},
                    {"id": 878, "name": "Ficção Científica"}
                ],
                "credits": {
                    "cast": [
                        {"id": 1, "name": "Tom Holland", "character": "Peter Parker"},
                        {"id": 2, "name": "Zendaya", "character": "MJ"}
                    ]
                }
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(mockBody))

        val response = api.getMovieDetail(movieId = 969681)
        assertTrue(response is ApiResponse.Success)
        val detail = (response as ApiResponse.Success).data
        assertEquals(969681, detail.id)
        assertEquals(142, detail.runtime)
        assertEquals(2, detail.genres.size)
        assertEquals("Ação", detail.genres[0].name)
        assertEquals(2, detail.credits?.cast?.size)
        assertEquals("Tom Holland", detail.credits?.cast?.get(0)?.name)
    }
}
