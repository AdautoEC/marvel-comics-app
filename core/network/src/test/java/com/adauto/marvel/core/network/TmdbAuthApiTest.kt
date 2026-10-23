package com.adauto.marvel.core.network

import com.adauto.marvel.core.network.api.TmdbApi
import com.adauto.marvel.core.network.model.TmdbCreateSessionRequestDto
import com.adauto.marvel.core.network.model.TmdbFavoriteRequestDto
import com.adauto.marvel.core.network.model.TmdbWatchlistRequestDto
import com.google.common.truth.Truth.assertThat
import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.retrofit.adapters.ApiResponseCallAdapterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class TmdbAuthApiTest {
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
    fun createRequestToken_parses_success_response() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success":true,"expires_at":"2026-10-23 00:00:00 UTC","request_token":"test_token_123"}""")
        )

        val response = api.createRequestToken()

        val recordedRequest = mockWebServer.takeRequest()
        assertThat(recordedRequest.path).isEqualTo("/3/authentication/token/new")
        assertThat(recordedRequest.method).isEqualTo("GET")

        assertThat(response).isInstanceOf(ApiResponse.Success::class.java)
        val body = (response as ApiResponse.Success).data
        assertThat(body.success).isTrue()
        assertThat(body.request_token).isEqualTo("test_token_123")
    }

    @Test
    fun createSession_sends_body_and_parses_session_id() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success":true,"session_id":"sess_98765"}""")
        )

        val response = api.createSession(TmdbCreateSessionRequestDto("token_abc"))

        val recordedRequest = mockWebServer.takeRequest()
        assertThat(recordedRequest.path).isEqualTo("/3/authentication/session/new")
        assertThat(recordedRequest.method).isEqualTo("POST")
        assertThat(recordedRequest.body.readUtf8()).contains(""""request_token":"token_abc"""")

        assertThat(response).isInstanceOf(ApiResponse.Success::class.java)
        val body = (response as ApiResponse.Success).data
        assertThat(body.success).isTrue()
        assertThat(body.session_id).isEqualTo("sess_98765")
    }

    @Test
    fun getAccountDetails_parses_account_and_avatar() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":42,"username":"adauto_user","name":"Adauto Souza","avatar":{"tmdb":{"avatar_path":"/avatar.png"}}}""")
        )

        val response = api.getAccountDetails(sessionId = "sess_98765")

        val recordedRequest = mockWebServer.takeRequest()
        assertThat(recordedRequest.path).isEqualTo("/3/account?session_id=sess_98765")
        assertThat(recordedRequest.method).isEqualTo("GET")

        assertThat(response).isInstanceOf(ApiResponse.Success::class.java)
        val body = (response as ApiResponse.Success).data
        assertThat(body.id).isEqualTo(42)
        assertThat(body.username).isEqualTo("adauto_user")
        assertThat(body.avatar?.tmdb?.avatar_path).isEqualTo("/avatar.png")
    }

    @Test
    fun markAsFavorite_sends_post_and_parses_action_response() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"status_code":1,"status_message":"Success.","success":true}""")
        )

        val response = api.markAsFavorite(
            accountId = 42,
            sessionId = "sess_98765",
            body = TmdbFavoriteRequestDto(media_id = 550, favorite = true)
        )

        val recordedRequest = mockWebServer.takeRequest()
        assertThat(recordedRequest.path).isEqualTo("/3/account/42/favorite?session_id=sess_98765")
        assertThat(recordedRequest.method).isEqualTo("POST")
        assertThat(recordedRequest.body.readUtf8()).contains(""""media_id":550""")

        assertThat(response).isInstanceOf(ApiResponse.Success::class.java)
        val body = (response as ApiResponse.Success).data
        assertThat(body.status_code).isEqualTo(1)
        assertThat(body.success).isTrue()
    }

    @Test
    fun addToWatchlist_sends_post_and_parses_action_response() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"status_code":1,"status_message":"Success.","success":true}""")
        )

        val response = api.addToWatchlist(
            accountId = 42,
            sessionId = "sess_98765",
            body = TmdbWatchlistRequestDto(media_id = 550, watchlist = true)
        )

        val recordedRequest = mockWebServer.takeRequest()
        assertThat(recordedRequest.path).isEqualTo("/3/account/42/watchlist?session_id=sess_98765")
        assertThat(recordedRequest.method).isEqualTo("POST")
        assertThat(recordedRequest.body.readUtf8()).contains(""""watchlist":true""")

        assertThat(response).isInstanceOf(ApiResponse.Success::class.java)
    }
}
