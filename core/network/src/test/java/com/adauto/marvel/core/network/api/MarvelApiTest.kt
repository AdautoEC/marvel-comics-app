package com.adauto.marvel.core.network.api

import com.adauto.marvel.core.network.model.ComicDto
import com.adauto.marvel.core.network.model.MarvelResponseDto
import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.retrofit.adapters.ApiResponseCallAdapterFactory
import com.skydoves.sandwich.retrofit.statusCode
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

class MarvelApiTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var api: MarvelApi

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
            .create(MarvelApi::class.java)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getComics returns ApiResponse Success with parsed results`() = runTest {
        val mockBody = """
            {
                "code": 200,
                "status": "Ok",
                "attributionText": "Data provided by Marvel. (c) 2026 MARVEL",
                "data": {
                    "offset": 0,
                    "limit": 20,
                    "total": 100,
                    "count": 1,
                    "results": [
                        {
                            "id": 82967,
                            "title": "Marvel Previews (2017)",
                            "issueNumber": 0.0,
                            "description": "Description sample",
                            "pageCount": 112,
                            "thumbnail": {
                                "path": "http://i.annihil.us/u/prod/marvel/i/mg/b/40/image_not_available",
                                "extension": "jpg"
                            },
                            "dates": [
                                {
                                    "type": "onsaleDate",
                                    "date": "2026-10-22T00:00:00-0400"
                                }
                            ],
                            "prices": [
                                {
                                    "type": "printPrice",
                                    "price": 3.99
                                }
                            ],
                            "creators": {
                                "available": 1,
                                "items": [
                                    {
                                        "name": "Stan Lee",
                                        "role": "writer"
                                    }
                                ]
                            },
                            "characters": {
                                "available": 1,
                                "items": [
                                    {
                                        "name": "Spider-Man"
                                    }
                                ]
                            }
                        }
                    ]
                }
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(mockBody))

        val response = api.getComics(offset = 0, limit = 20)
        assertTrue(response is ApiResponse.Success)
        val data = (response as ApiResponse.Success).data
        assertEquals(200, data.code)
        assertEquals("Ok", data.status)
        assertEquals("Data provided by Marvel. (c) 2026 MARVEL", data.attributionText)
        assertEquals(0, data.data.offset)
        assertEquals(20, data.data.limit)
        assertEquals(100, data.data.total)
        assertEquals(1, data.data.count)

        val comic = data.data.results[0]
        assertEquals(82967, comic.id)
        assertEquals("Marvel Previews (2017)", comic.title)
        assertEquals(0.0, comic.issueNumber, 0.001)
        assertEquals("Description sample", comic.description)
        assertEquals(112, comic.pageCount)
        assertEquals("http://i.annihil.us/u/prod/marvel/i/mg/b/40/image_not_available", comic.thumbnail?.path)
        assertEquals("jpg", comic.thumbnail?.extension)
        assertEquals(1, comic.dates.size)
        assertEquals("onsaleDate", comic.dates[0].type)
        assertEquals(1, comic.prices.size)
        assertEquals(3.99f, comic.prices[0].price, 0.001f)
        assertEquals(1, comic.creators.available)
        assertEquals("Stan Lee", comic.creators.items[0].name)
        assertEquals("writer", comic.creators.items[0].role)
        assertEquals("Spider-Man", comic.characters.items[0].name)

        val recorded = mockWebServer.takeRequest()
        assertEquals("/v1/public/comics?offset=0&limit=20&orderBy=-focDate&noVariants=true", recorded.path)
    }

    @Test
    fun `getComicDetail returns ApiResponse Success with parsed results`() = runTest {
        val mockBody = """
            {
                "code": 200,
                "status": "Ok",
                "attributionText": "Data provided by Marvel. (c) 2026 MARVEL",
                "data": {
                    "offset": 0,
                    "limit": 1,
                    "total": 1,
                    "count": 1,
                    "results": [
                        {
                            "id": 82967,
                            "title": "Marvel Previews (2017)",
                            "issueNumber": 1.0,
                            "description": "Detail description",
                            "pageCount": 32,
                            "thumbnail": {
                                "path": "http://example.com/cover",
                                "extension": "png"
                            }
                        }
                    ]
                }
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(mockBody))

        val response = api.getComicDetail(comicId = 82967)
        assertTrue(response is ApiResponse.Success)
        val data = (response as ApiResponse.Success).data
        assertEquals(82967, data.data.results[0].id)
        assertEquals("Marvel Previews (2017)", data.data.results[0].title)

        val recorded = mockWebServer.takeRequest()
        assertEquals("/v1/public/comics/82967", recorded.path)
    }

    @Test
    fun `getComicDetail returns ApiResponse Failure Error on 404`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(404).setBody("""{"code":404,"status":"Not Found"}"""))

        val response = api.getComicDetail(comicId = 99999)
        assertTrue(response is ApiResponse.Failure.Error)
        val error = response as ApiResponse.Failure.Error
        assertEquals(404, error.statusCode.code)
    }
}
