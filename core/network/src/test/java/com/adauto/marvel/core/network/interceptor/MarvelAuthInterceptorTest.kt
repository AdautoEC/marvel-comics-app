package com.adauto.marvel.core.network.interceptor

import com.adauto.marvel.core.model.MarvelKeys
import com.adauto.marvel.core.network.security.MarvelKeyProvider
import com.adauto.marvel.core.network.security.computeMarvelHash
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class MarvelAuthInterceptorTest {

    private val keyProvider: MarvelKeyProvider = mockk()
    private val fixedInstant = Instant.parse("2026-10-22T05:00:00Z")
    private val fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
    private val expectedTs = fixedInstant.toEpochMilli().toString()

    private val defaultKeys = MarvelKeys(
        publicKey = "pub_key_123",
        privateKey = "priv_key_456",
        keyVersion = 1
    )

    private lateinit var interceptor: MarvelAuthInterceptor
    private lateinit var mockWebServer: MockWebServer

    @Before
    fun setUp() {
        interceptor = MarvelAuthInterceptor(keyProvider = keyProvider, clock = fixedClock)
        mockWebServer = MockWebServer()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `intercept signs gateway request with ts, apikey, and computed hash`() {
        coEvery { keyProvider.current() } returns defaultKeys

        val request = Request.Builder()
            .url("https://gateway.marvel.com/v1/public/comics")
            .build()

        val capturedRequest = slot<Request>()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(capture(capturedRequest)) } returns Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .build()

        interceptor.intercept(chain)

        val interceptedUrl = capturedRequest.captured.url
        assertThat(interceptedUrl.queryParameter("ts")).isEqualTo(expectedTs)
        assertThat(interceptedUrl.queryParameter("apikey")).isEqualTo("pub_key_123")

        val expectedHash = computeMarvelHash(expectedTs, "priv_key_456", "pub_key_123")
        assertThat(interceptedUrl.queryParameter("hash")).isEqualTo(expectedHash)
    }

    @Test
    fun `intercept preserves existing query parameters on gateway request`() {
        coEvery { keyProvider.current() } returns defaultKeys

        val request = Request.Builder()
            .url("https://gateway.marvel.com/v1/public/comics?limit=20&offset=40")
            .build()

        val capturedRequest = slot<Request>()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(capture(capturedRequest)) } returns Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .build()

        interceptor.intercept(chain)

        val interceptedUrl = capturedRequest.captured.url
        assertThat(interceptedUrl.queryParameter("limit")).isEqualTo("20")
        assertThat(interceptedUrl.queryParameter("offset")).isEqualTo("40")
        assertThat(interceptedUrl.queryParameter("ts")).isEqualTo(expectedTs)
        assertThat(interceptedUrl.queryParameter("apikey")).isEqualTo("pub_key_123")
        val expectedHash = computeMarvelHash(expectedTs, "priv_key_456", "pub_key_123")
        assertThat(interceptedUrl.queryParameter("hash")).isEqualTo(expectedHash)
    }

    @Test
    fun `intercept passes non-gateway requests through without signing`() {
        val nonGatewayUrl = "https://i.annihil.us/u/prod/marvel/i/mg/3/20/image.jpg"
        val request = Request.Builder()
            .url(nonGatewayUrl)
            .build()

        val capturedRequest = slot<Request>()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(capture(capturedRequest)) } returns Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .build()

        interceptor.intercept(chain)

        val interceptedUrl = capturedRequest.captured.url
        assertThat(interceptedUrl.toString()).isEqualTo(nonGatewayUrl)
        assertThat(interceptedUrl.queryParameter("ts")).isNull()
        assertThat(interceptedUrl.queryParameter("apikey")).isNull()
        assertThat(interceptedUrl.queryParameter("hash")).isNull()
        coVerify(exactly = 0) { keyProvider.current() }
    }

    @Test
    fun `intercept throws MarvelKeysUnavailableException when keys are null`() {
        coEvery { keyProvider.current() } returns null

        val request = Request.Builder()
            .url("https://gateway.marvel.com/v1/public/comics")
            .build()

        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request

        assertThrows(MarvelKeysUnavailableException::class.java) {
            interceptor.intercept(chain)
        }
    }

    @Test
    fun `intercept with MockWebServer verifies signed HTTP request on gateway host`() {
        mockWebServer.start()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        coEvery { keyProvider.current() } returns defaultKeys

        val client = OkHttpClient.Builder()
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    return if (hostname == "gateway.marvel.com") {
                        listOf(InetAddress.getByName(mockWebServer.hostName))
                    } else {
                        Dns.SYSTEM.lookup(hostname)
                    }
                }
            })
            .addInterceptor(interceptor)
            .build()

        val request = Request.Builder()
            .url("http://gateway.marvel.com:${mockWebServer.port}/v1/public/comics")
            .build()

        val response = client.newCall(request).execute()
        assertThat(response.isSuccessful).isTrue()

        val recorded = mockWebServer.takeRequest()
        assertThat(recorded.requestUrl?.queryParameter("ts")).isEqualTo(expectedTs)
        assertThat(recorded.requestUrl?.queryParameter("apikey")).isEqualTo("pub_key_123")
        val expectedHash = computeMarvelHash(expectedTs, "priv_key_456", "pub_key_123")
        assertThat(recorded.requestUrl?.queryParameter("hash")).isEqualTo(expectedHash)
    }
}
