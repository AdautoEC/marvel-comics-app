package com.adauto.marvel.core.network.interceptor

import com.adauto.marvel.core.model.MarvelKeys
import com.adauto.marvel.core.network.security.MarvelKeyProvider
import com.adauto.marvel.core.network.security.computeMarvelHash
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class MarvelAuthenticatorTest {

    private val keyProvider: MarvelKeyProvider = mockk()
    private val fixedInstant = Instant.parse("2026-10-22T05:30:00Z")
    private val fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
    private val expectedTs = fixedInstant.toEpochMilli().toString()

    private val refreshedKeys = MarvelKeys(
        publicKey = "rotated_pub_key",
        privateKey = "rotated_priv_key",
        keyVersion = 2
    )

    private lateinit var authenticator: MarvelAuthenticator
    private lateinit var mockWebServer: MockWebServer

    @Before
    fun setUp() {
        authenticator = MarvelAuthenticator(keyProvider = keyProvider, clock = fixedClock)
        mockWebServer = MockWebServer()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `authenticate on 401 calls forceRefresh and returns retried request with new keys when rotation succeeded`() {
        coEvery { keyProvider.forceRefresh() } returns true
        coEvery { keyProvider.current() } returns refreshedKeys

        val originalRequest = Request.Builder()
            .url("https://gateway.marvel.com/v1/public/comics?ts=100&apikey=old_pub&hash=old_hash")
            .build()

        val response = Response.Builder()
            .request(originalRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .build()

        val retryRequest = authenticator.authenticate(null, response)

        assertThat(retryRequest).isNotNull()
        coVerify(exactly = 1) { keyProvider.forceRefresh() }
        coVerify(atLeast = 1) { keyProvider.current() }

        val retryUrl = retryRequest!!.url
        assertThat(retryUrl.queryParameter("ts")).isEqualTo(expectedTs)
        assertThat(retryUrl.queryParameter("apikey")).isEqualTo("rotated_pub_key")
        val expectedHash = computeMarvelHash(expectedTs, "rotated_priv_key", "rotated_pub_key")
        assertThat(retryUrl.queryParameter("hash")).isEqualTo(expectedHash)
    }

    @Test
    fun `authenticate on 401 returns null when forceRefresh returns false`() {
        coEvery { keyProvider.forceRefresh() } returns false

        val originalRequest = Request.Builder()
            .url("https://gateway.marvel.com/v1/public/comics?ts=100&apikey=old_pub&hash=old_hash")
            .build()

        val response = Response.Builder()
            .request(originalRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .build()

        val retryRequest = authenticator.authenticate(null, response)

        assertThat(retryRequest).isNull()
        coVerify(exactly = 1) { keyProvider.forceRefresh() }
    }

    @Test
    fun `authenticate on 401 returns null when priorResponse is present to prevent infinite loops`() {
        val originalRequest = Request.Builder()
            .url("https://gateway.marvel.com/v1/public/comics?ts=100&apikey=old_pub&hash=old_hash")
            .build()

        val priorResponse = Response.Builder()
            .request(originalRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .build()

        val secondResponse = Response.Builder()
            .request(originalRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .priorResponse(priorResponse)
            .build()

        val retryRequest = authenticator.authenticate(null, secondResponse)

        assertThat(retryRequest).isNull()
        coVerify(exactly = 0) { keyProvider.forceRefresh() }
    }

    @Test
    fun `authenticate returns null on non-401 responses`() {
        val originalRequest = Request.Builder()
            .url("https://gateway.marvel.com/v1/public/comics")
            .build()

        val response200 = Response.Builder()
            .request(originalRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .build()

        val response403 = Response.Builder()
            .request(originalRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(403)
            .message("Forbidden")
            .build()

        assertThat(authenticator.authenticate(null, response200)).isNull()
        assertThat(authenticator.authenticate(null, response403)).isNull()
        coVerify(exactly = 0) { keyProvider.forceRefresh() }
    }

    @Test
    fun `authenticate returns null for non-gateway requests`() {
        val nonGatewayRequest = Request.Builder()
            .url("https://i.annihil.us/u/prod/marvel/i/mg/3/20/image.jpg")
            .build()

        val response = Response.Builder()
            .request(nonGatewayRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .build()

        val retryRequest = authenticator.authenticate(null, response)

        assertThat(retryRequest).isNull()
        coVerify(exactly = 0) { keyProvider.forceRefresh() }
    }

    @Test
    fun `authenticate returns null when forceRefresh is true but current keys are null`() {
        coEvery { keyProvider.forceRefresh() } returns true
        coEvery { keyProvider.current() } returns null

        val originalRequest = Request.Builder()
            .url("https://gateway.marvel.com/v1/public/comics")
            .build()

        val response = Response.Builder()
            .request(originalRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .build()

        val retryRequest = authenticator.authenticate(null, response)

        assertThat(retryRequest).isNull()
        coVerify(exactly = 1) { keyProvider.forceRefresh() }
    }

    @Test
    fun `end-to-end 401 retry with MockWebServer succeeds on second attempt`() {
        mockWebServer.start()
        // First request returns 401, second request returns 200
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("{\"code\":\"InvalidCredentials\"}"))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{\"code\":200,\"status\":\"Ok\"}"))

        val initialKeys = MarvelKeys("initial_pub", "initial_priv", 1)
        coEvery { keyProvider.current() } returns initialKeys andThen refreshedKeys
        coEvery { keyProvider.forceRefresh() } returns true

        val authInterceptor = MarvelAuthInterceptor(keyProvider = keyProvider, clock = fixedClock)

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
            .addInterceptor(authInterceptor)
            .authenticator(authenticator)
            .build()

        val request = Request.Builder()
            .url("http://gateway.marvel.com:${mockWebServer.port}/v1/public/comics")
            .build()

        val response = client.newCall(request).execute()
        assertThat(response.isSuccessful).isTrue()
        assertThat(response.code).isEqualTo(200)

        // Verify two requests reached the server
        val firstReq = mockWebServer.takeRequest()
        assertThat(firstReq.requestUrl?.queryParameter("apikey")).isEqualTo("initial_pub")

        val secondReq = mockWebServer.takeRequest()
        assertThat(secondReq.requestUrl?.queryParameter("apikey")).isEqualTo("rotated_pub_key")
    }

    @Test
    fun `end-to-end 401 retry with MockWebServer stops after one retry on repeated 401`() {
        mockWebServer.start()
        // Two consecutive 401 responses
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("{\"code\":\"InvalidCredentials\"}"))
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("{\"code\":\"InvalidCredentials\"}"))

        val initialKeys = MarvelKeys("initial_pub", "initial_priv", 1)
        coEvery { keyProvider.current() } returns initialKeys andThen refreshedKeys
        coEvery { keyProvider.forceRefresh() } returns true

        val authInterceptor = MarvelAuthInterceptor(keyProvider = keyProvider, clock = fixedClock)

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
            .addInterceptor(authInterceptor)
            .authenticator(authenticator)
            .build()

        val request = Request.Builder()
            .url("http://gateway.marvel.com:${mockWebServer.port}/v1/public/comics")
            .build()

        val response = client.newCall(request).execute()
        assertThat(response.code).isEqualTo(401)

        // First attempt + 1 retry = 2 total requests, exactly
        assertThat(mockWebServer.requestCount).isEqualTo(2)
        coVerify(exactly = 1) { keyProvider.forceRefresh() }
    }
}
