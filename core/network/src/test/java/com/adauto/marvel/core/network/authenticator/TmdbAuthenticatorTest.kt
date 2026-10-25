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

    @Test
    fun authenticate_whenBothCredentialsExist_andRequestIsBearer_refetchesAndRetries() {
        val response = createResponse(
            url = "https://api.themoviedb.org/3/movie/popular",
            headers = mapOf("Authorization" to "Bearer old_token"),
            bodyJson = """{"status_code":7,"status_message":"Invalid API key","success":false}"""
        )

        // Both apiKey and readAccessToken are present in currentCredentials
        coEvery { keyProvider.getCredentials() } returns TmdbCredentials(
            apiKey = "current_api_key",
            readAccessToken = "old_token"
        )
        coEvery { keyProvider.refreshKeyForced() } returns TmdbCredentials(
            apiKey = "current_api_key",
            readAccessToken = "new_token"
        )

        val resultRequest = authenticator.authenticate(null, response)

        assertThat(resultRequest).isNotNull()
        assertThat(resultRequest?.header("Authorization")).isEqualTo("Bearer new_token")
        // Must have called refreshKeyForced instead of falsely considering apiKey != null as rotated
        coVerify(exactly = 1) { keyProvider.refreshKeyForced() }
    }

    @Test
    fun authenticate_whenBothCredentialsExist_andRequestIsApiKey_refetchesAndRetries() {
        val response = createResponse(
            url = "https://api.themoviedb.org/3/movie/popular?api_key=old_key",
            bodyJson = """{"status_code":7,"status_message":"Invalid API key","success":false}"""
        )

        coEvery { keyProvider.getCredentials() } returns TmdbCredentials(
            apiKey = "old_key",
            readAccessToken = "current_token"
        )
        coEvery { keyProvider.refreshKeyForced() } returns TmdbCredentials(
            apiKey = "new_key",
            readAccessToken = "current_token"
        )

        val resultRequest = authenticator.authenticate(null, response)

        assertThat(resultRequest).isNotNull()
        // Prioritizes readAccessToken if present, stripping api_key query param
        assertThat(resultRequest?.header("Authorization")).isEqualTo("Bearer current_token")
        assertThat(resultRequest?.url?.queryParameter("api_key")).isNull()
        coVerify(exactly = 1) { keyProvider.refreshKeyForced() }
    }

    @Test
    fun authenticate_whenSwitchedToBearer_removesApiKeyQueryParam() {
        val response = createResponse(
            url = "https://api.themoviedb.org/3/movie/popular?api_key=old_key",
            bodyJson = """{"status_code":7,"status_message":"Invalid API key","success":false}"""
        )

        coEvery { keyProvider.getCredentials() } returns TmdbCredentials(apiKey = "old_key")
        coEvery { keyProvider.refreshKeyForced() } returns TmdbCredentials(
            readAccessToken = "new_bearer_token"
        )

        val resultRequest = authenticator.authenticate(null, response)

        assertThat(resultRequest).isNotNull()
        assertThat(resultRequest?.header("Authorization")).isEqualTo("Bearer new_bearer_token")
        assertThat(resultRequest?.url?.queryParameter("api_key")).isNull()
    }

    @Test
    fun authenticate_whenSwitchedToApiKey_removesAuthorizationHeader() {
        val response = createResponse(
            url = "https://api.themoviedb.org/3/movie/popular",
            headers = mapOf("Authorization" to "Bearer old_bearer_token"),
            bodyJson = """{"status_code":7,"status_message":"Invalid API key","success":false}"""
        )

        coEvery { keyProvider.getCredentials() } returns TmdbCredentials(readAccessToken = "old_bearer_token")
        coEvery { keyProvider.refreshKeyForced() } returns TmdbCredentials(apiKey = "new_key_only")

        val resultRequest = authenticator.authenticate(null, response)

        assertThat(resultRequest).isNotNull()
        assertThat(resultRequest?.url?.queryParameter("api_key")).isEqualTo("new_key_only")
        assertThat(resultRequest?.header("Authorization")).isNull()
    }

    @Test
    fun authenticate_whenGetCredentialsThrows_returnsNullWithoutCrashing() {
        val response = createResponse(
            url = "https://api.themoviedb.org/3/movie/popular?api_key=old_key",
            bodyJson = """{"status_code":7,"status_message":"Invalid API key","success":false}"""
        )

        coEvery { keyProvider.getCredentials() } throws RuntimeException("Remote config error")

        val resultRequest = authenticator.authenticate(null, response)

        assertThat(resultRequest).isNull()
    }

    @Test
    fun authenticate_whenRefreshKeyForcedThrows_returnsNullWithoutCrashing() {
        val response = createResponse(
            url = "https://api.themoviedb.org/3/movie/popular?api_key=old_key",
            bodyJson = """{"status_code":7,"status_message":"Invalid API key","success":false}"""
        )

        coEvery { keyProvider.getCredentials() } returns TmdbCredentials(apiKey = "old_key")
        coEvery { keyProvider.refreshKeyForced() } throws RuntimeException("Network error on refresh")

        val resultRequest = authenticator.authenticate(null, response)

        assertThat(resultRequest).isNull()
    }

    @Test
    fun authenticate_returnsNullWhenKeyProviderReturnsIdenticalCredentialsDuringCooldown() {
        val sameCreds = TmdbCredentials(apiKey = "invalid_key", readAccessToken = null)
        coEvery { keyProvider.getCredentials() } returns sameCreds
        coEvery { keyProvider.refreshKeyForced() } returns sameCreds

        val response = createResponse(
            url = "https://api.themoviedb.org/3/movie/1?api_key=invalid_key",
            bodyJson = """{"status_code":7,"status_message":"Invalid API key","success":false}"""
        )
        val nextRequest = authenticator.authenticate(null, response)

        assertThat(nextRequest).isNull()
    }

    @Test
    fun authenticate_returnsNullWhenBearerTokenMatchesDuringCooldown() {
        val sameCreds = TmdbCredentials(apiKey = null, readAccessToken = "invalid_bearer")
        coEvery { keyProvider.getCredentials() } returns sameCreds
        coEvery { keyProvider.refreshKeyForced() } returns sameCreds

        val response = createResponse(
            url = "https://api.themoviedb.org/3/movie/1",
            headers = mapOf("Authorization" to "Bearer invalid_bearer"),
            bodyJson = """{"status_code":7,"status_message":"Invalid API key","success":false}"""
        )
        val nextRequest = authenticator.authenticate(null, response)

        assertThat(nextRequest).isNull()
    }
}
