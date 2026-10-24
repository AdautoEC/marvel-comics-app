package com.adauto.marvel.core.network.interceptor

import com.adauto.marvel.core.network.security.KeysUnavailableException
import com.adauto.marvel.core.network.security.TmdbApiKeyProvider
import com.adauto.marvel.core.network.security.TmdbCredentials
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class TmdbAuthInterceptorTest {

    private val keyProvider: TmdbApiKeyProvider = mockk()
    private val chain: Interceptor.Chain = mockk()
    private lateinit var interceptor: TmdbAuthInterceptor

    @Before
    fun setUp() {
        interceptor = TmdbAuthInterceptor(keyProvider)
    }

    private fun mockChainResponse(request: Request): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("{}".toResponseBody())
            .build()
    }

    @Test
    fun intercept_whenCredentialsMissing_throwsKeysUnavailableException() {
        coEvery { keyProvider.getCredentials() } returns null

        val request = Request.Builder()
            .url("https://api.themoviedb.org/3/movie/popular")
            .build()
        every { chain.request() } returns request

        assertThrows(KeysUnavailableException::class.java) {
            interceptor.intercept(chain)
        }
    }

    @Test
    fun intercept_whenCredentialsEmpty_throwsKeysUnavailableException() {
        coEvery { keyProvider.getCredentials() } returns TmdbCredentials(apiKey = "", readAccessToken = "")

        val request = Request.Builder()
            .url("https://api.themoviedb.org/3/movie/popular")
            .build()
        every { chain.request() } returns request

        assertThrows(KeysUnavailableException::class.java) {
            interceptor.intercept(chain)
        }
    }

    @Test
    fun intercept_whenReadAccessTokenPresent_addsBearerAuthorizationHeader() {
        coEvery { keyProvider.getCredentials() } returns TmdbCredentials(
            apiKey = "api_key_123",
            readAccessToken = "token_xyz"
        )

        val request = Request.Builder()
            .url("https://api.themoviedb.org/3/movie/popular?api_key=old_key&language=en-US")
            .build()
        val requestSlot = slot<Request>()
        every { chain.request() } returns request
        every { chain.proceed(capture(requestSlot)) } answers { mockChainResponse(requestSlot.captured) }

        val response = interceptor.intercept(chain)

        assertThat(response.isSuccessful).isTrue()
        val interceptedRequest = requestSlot.captured
        assertThat(interceptedRequest.header("Authorization")).isEqualTo("Bearer token_xyz")
        assertThat(interceptedRequest.url.queryParameter("api_key")).isNull()
        assertThat(interceptedRequest.url.queryParameter("language")).isEqualTo("en-US")
    }

    @Test
    fun intercept_whenApiKeyPresent_replacesQueryParameter() {
        coEvery { keyProvider.getCredentials() } returns TmdbCredentials(
            apiKey = "new_api_key",
            readAccessToken = null
        )

        val request = Request.Builder()
            .url("https://api.themoviedb.org/3/movie/popular?api_key=old_key&language=en-US")
            .header("Authorization", "Bearer old_bearer")
            .build()
        val requestSlot = slot<Request>()
        every { chain.request() } returns request
        every { chain.proceed(capture(requestSlot)) } answers { mockChainResponse(requestSlot.captured) }

        val response = interceptor.intercept(chain)

        assertThat(response.isSuccessful).isTrue()
        val interceptedRequest = requestSlot.captured
        assertThat(interceptedRequest.url.queryParameter("api_key")).isEqualTo("new_api_key")
        assertThat(interceptedRequest.header("Authorization")).isNull()
        assertThat(interceptedRequest.url.queryParameter("language")).isEqualTo("en-US")
    }
}
