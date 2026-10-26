package com.adauto.cinemahub.core.network.di

import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ImageClientIsolationTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun imageClient_executesRequestWithoutApikeyOrHashQuery() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("fake-image-bytes"))
        val imageClient = ImageClientModule.provideImageOkHttpClient()

        val request = Request.Builder().url(server.url("/image.jpg")).build()
        val response = imageClient.newCall(request).execute()

        assertEquals(200, response.code)

        val recorded = server.takeRequest()
        assertNull(recorded.requestUrl?.queryParameter("apikey"))
        assertNull(recorded.requestUrl?.queryParameter("hash"))
        assertNull(recorded.requestUrl?.queryParameter("ts"))
    }

    @Test
    fun imageClient_hasConfiguredTimeouts() {
        val imageClient = ImageClientModule.provideImageOkHttpClient()

        assertEquals(15000, imageClient.connectTimeoutMillis)
        assertEquals(20000, imageClient.readTimeoutMillis)
    }

    @Test
    fun imageClient_hasNoAuthInterceptors() {
        val imageClient = ImageClientModule.provideImageOkHttpClient()

        val interceptorNames = imageClient.interceptors.map { it::class.java.name }
        assertTrue(
            "Image client must not contain AuthInterceptor",
            interceptorNames.none { it.contains("AuthInterceptor") }
        )
    }
}
