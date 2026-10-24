package com.adauto.marvel.core.network.authenticator

import com.adauto.marvel.core.network.security.TmdbApiKeyProvider
import com.adauto.marvel.core.network.security.TmdbCredentials
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Test

class TmdbAuthenticatorTest {

    private val keyProvider: TmdbApiKeyProvider = mockk()
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private lateinit var authenticator: TmdbAuthenticator

    @Before
    fun setUp() {
        authenticator = TmdbAuthenticator(keyProvider, json)
    }

    private fun createResponse(
        url: String = "https://api.themoviedb.org/3/movie/popular?api_key=old_key",
        bodyJson: String = """{"status_code":7,"status_message":"Invalid API key","success":false}""",
        headers: Map<String, String> = emptyMap(),
        priorResponse: Response? = null
    ): Response {
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (name, value) -> requestBuilder.header(name, value) }
        val request = requestBuilder.build()

        val responseBody = bodyJson.toResponseBody("application/json".toMediaType())

        val responseBuilder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .body(responseBody)

        if (priorResponse != null) {
            responseBuilder.priorResponse(priorResponse)
        }

        return responseBuilder.build()
    }

    @Test
    fun authenticate_whenStatusCodeIs7_refetchesAndRetriesWithNewKey() {
        val response = createResponse(
            url = "https://api.themoviedb.org/3/movie/popular?api_key=old_key",
            bodyJson = """{"status_code":7,"status_message":"Invalid API key: You must be granted a valid key.","success":false}"""
        )

        coEvery { keyProvider.getCredentials() } returns TmdbCredentials(apiKey = "old_key")
        coEvery { keyProvider.refreshKeyForced() } returns TmdbCredentials(apiKey = "new_key")

        val resultRequest = authenticator.authenticate(null, response)

        assertThat(resultRequest).isNotNull()
        assertThat(resultRequest?.url?.queryParameter("api_key")).isEqualTo("new_key")
        coVerify(exactly = 1) { keyProvider.refreshKeyForced() }
    }

    @Test
    fun authenticate_whenStatusCodeIsNot7_returnsNull() {
        val response = createResponse(
            url = "https://api.themoviedb.org/3/movie/popular?api_key=old_key",
            bodyJson = """{"status_code":3,"status_message":"Authentication failed: You do not have permissions to access the service.","success":false}"""
        )

        val resultRequest = authenticator.authenticate(null, response)

        assertThat(resultRequest).isNull()
        coVerify(exactly = 0) { keyProvider.getCredentials() }
        coVerify(exactly = 0) { keyProvider.refreshKeyForced() }
    }

    @Test
    fun authenticate_whenKeyAlreadyRotated_retriesImmediatelyWithoutFetch() {
        val response = createResponse(
            url = "https://api.themoviedb.org/3/movie/popular?api_key=old_key",
            bodyJson = """{"status_code":7,"status_message":"Invalid API key","success":false}"""
        )

        coEvery { keyProvider.getCredentials() } returns TmdbCredentials(apiKey = "rotated_key")

        val resultRequest = authenticator.authenticate(null, response)

        assertThat(resultRequest).isNotNull()
        assertThat(resultRequest?.url?.queryParameter("api_key")).isEqualTo("rotated_key")
        coVerify(exactly = 1) { keyProvider.getCredentials() }
        coVerify(exactly = 0) { keyProvider.refreshKeyForced() }
    }

    @Test
    fun authenticate_whenRetryLimitExceeded_returnsNull() {
        val initialRequest = Request.Builder()
            .url("https://api.themoviedb.org/3/movie/popular?api_key=old_key")
            .build()
        val priorResponse = Response.Builder()
            .request(initialRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .build()

        val response = createResponse(
            url = "https://api.themoviedb.org/3/movie/popular?api_key=old_key",
            bodyJson = """{"status_code":7,"status_message":"Invalid API key","success":false}""",
            priorResponse = priorResponse
        )

        val resultRequest = authenticator.authenticate(null, response)

        assertThat(resultRequest).isNull()
        coVerify(exactly = 0) { keyProvider.getCredentials() }
        coVerify(exactly = 0) { keyProvider.refreshKeyForced() }
    }

    @Test
    fun authenticate_whenRotatedWithReadAccessToken_setsAuthorizationHeader() {
        val response = createResponse(
            url = "https://api.themoviedb.org/3/movie/popular?api_key=old_key",
            bodyJson = """{"status_code":7,"status_message":"Invalid API key","success":false}"""
        )

        coEvery { keyProvider.getCredentials() } returns TmdbCredentials(apiKey = "old_key")
        coEvery { keyProvider.refreshKeyForced() } returns TmdbCredentials(readAccessToken = "new_bearer_token")

        val resultRequest = authenticator.authenticate(null, response)

        assertThat(resultRequest).isNotNull()
        assertThat(resultRequest?.header("Authorization")).isEqualTo("Bearer new_bearer_token")
    }

    @Test
    fun authenticate_whenRefreshReturnsNull_returnsNull() {
        val response = createResponse(
            url = "https://api.themoviedb.org/3/movie/popular?api_key=old_key",
            bodyJson = """{"status_code":7,"status_message":"Invalid API key","success":false}"""
        )

        coEvery { keyProvider.getCredentials() } returns TmdbCredentials(apiKey = "old_key")
        coEvery { keyProvider.refreshKeyForced() } returns null

        val resultRequest = authenticator.authenticate(null, response)

        assertThat(resultRequest).isNull()
    }

    @Test
    fun authenticate_whenMalformedJson_returnsNull() {
        val response = createResponse(
            url = "https://api.themoviedb.org/3/movie/popular?api_key=old_key",
            bodyJson = "Internal Server Error - Non JSON"
        )

        val resultRequest = authenticator.authenticate(null, response)

        assertThat(resultRequest).isNull()
        coVerify(exactly = 0) { keyProvider.getCredentials() }
        coVerify(exactly = 0) { keyProvider.refreshKeyForced() }
    }
}
