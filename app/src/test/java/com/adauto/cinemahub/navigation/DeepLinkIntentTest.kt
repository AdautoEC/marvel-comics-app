package com.adauto.cinemahub.navigation

import android.content.Intent
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Before
import org.junit.Test

class DeepLinkIntentTest {

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            val urlString = firstArg<String>()
            val parsed = java.net.URI(urlString)
            val mockUri = mockk<Uri>()
            every { mockUri.scheme } returns parsed.scheme
            every { mockUri.host } returns parsed.host
            every { mockUri.getQueryParameter(any()) } answers { call ->
                val key = call.invocation.args[0] as String
                val query = parsed.query ?: return@answers null
                query.split("&")
                    .map { it.split("=", limit = 2) }
                    .firstOrNull { it[0] == key }
                    ?.getOrNull(1)
            }
            mockUri
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    private fun createIntent(uri: Uri): Intent {
        val intent = mockk<Intent>()
        every { intent.data } returns uri
        return intent
    }

    @Test
    fun intent_with_tmdb_callback_extracts_token_and_approved_flag() {
        val uri = Uri.parse("cinemahub://tmdb-callback?request_token=abc_123&approved=true")
        val intent = createIntent(uri)
        assertThat(intent.data?.getQueryParameter("request_token")).isEqualTo("abc_123")
        assertThat(intent.data?.getQueryParameter("approved")).isEqualTo("true")
    }

    @Test
    fun valid_approved_callback_extracts_request_token() {
        val uri = Uri.parse("cinemahub://tmdb-callback?request_token=test_tok&approved=true")
        val intent = createIntent(uri)

        val token = DeepLinkHandler.extractApprovedRequestToken(intent)

        assertThat(token).isEqualTo("test_tok")
    }

    @Test
    fun valid_approved_callback_is_case_insensitive_for_scheme_and_host() {
        val uppercaseUri = Uri.parse("CINEMAHUB://TMDB-CALLBACK?request_token=upper_tok&approved=true")
        val mixedUri = Uri.parse("CinemaHub://Tmdb-Callback?request_token=mixed_tok&approved=true")

        assertThat(DeepLinkHandler.extractApprovedRequestToken(createIntent(uppercaseUri)))
            .isEqualTo("upper_tok")
        assertThat(DeepLinkHandler.extractApprovedRequestToken(createIntent(mixedUri)))
            .isEqualTo("mixed_tok")
    }

    @Test
    fun legacy_marvelapp_scheme_is_rejected() {
        val uri = Uri.parse("marvelapp://tmdb-callback?request_token=legacy_tok&approved=true")
        val intent = createIntent(uri)

        val token = DeepLinkHandler.extractApprovedRequestToken(intent)

        assertThat(token).isNull()
    }

    @Test
    fun unapproved_callback_returns_null() {
        val uri = Uri.parse("cinemahub://tmdb-callback?request_token=test_tok&approved=false")
        val intent = createIntent(uri)

        val token = DeepLinkHandler.extractApprovedRequestToken(intent)

        assertThat(token).isNull()
    }

    @Test
    fun missing_approved_parameter_returns_null() {
        val uri = Uri.parse("cinemahub://tmdb-callback?request_token=test_tok")
        val intent = createIntent(uri)

        val token = DeepLinkHandler.extractApprovedRequestToken(intent)

        assertThat(token).isNull()
    }

    @Test
    fun invalid_scheme_returns_null() {
        val uri = Uri.parse("https://tmdb-callback?request_token=test_tok&approved=true")
        val intent = createIntent(uri)

        val token = DeepLinkHandler.extractApprovedRequestToken(intent)

        assertThat(token).isNull()
    }

    @Test
    fun invalid_host_returns_null() {
        val uri = Uri.parse("cinemahub://other-host?request_token=test_tok&approved=true")
        val intent = createIntent(uri)

        val token = DeepLinkHandler.extractApprovedRequestToken(intent)

        assertThat(token).isNull()
    }

    @Test
    fun empty_request_token_returns_null() {
        val uri = Uri.parse("cinemahub://tmdb-callback?request_token=&approved=true")
        val intent = createIntent(uri)

        val token = DeepLinkHandler.extractApprovedRequestToken(intent)

        assertThat(token).isNull()
    }

    @Test
    fun null_intent_or_null_uri_returns_null() {
        assertThat(DeepLinkHandler.extractApprovedRequestToken(null as Intent?)).isNull()
        assertThat(DeepLinkHandler.extractApprovedRequestToken(null as Uri?)).isNull()

        val emptyIntent = mockk<Intent>()
        every { emptyIntent.data } returns null
        assertThat(DeepLinkHandler.extractApprovedRequestToken(emptyIntent)).isNull()
    }
}
