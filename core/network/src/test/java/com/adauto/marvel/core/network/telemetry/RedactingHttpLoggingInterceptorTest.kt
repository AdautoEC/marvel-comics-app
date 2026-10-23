package com.adauto.marvel.core.network.telemetry

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RedactingHttpLoggingInterceptorTest {

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
    fun sanitizeUrlForTelemetry_replacesSensitiveParamsWhilePreservingOthers() {
        val originalUrl = "https://gateway.marvel.com/v1/public/comics?ts=12345&apikey=super-secret-key&hash=abcdef0123456789&limit=20&offset=40".toHttpUrl()

        val sanitized = sanitizeUrlForTelemetry(originalUrl)
        val parsedSanitized = sanitized.toHttpUrl()

        // Verify sensitive parameters are replaced with [REDACTED]
        assertEquals("[REDACTED]", parsedSanitized.queryParameter("apikey"))
        assertEquals("[REDACTED]", parsedSanitized.queryParameter("hash"))
        assertEquals("[REDACTED]", parsedSanitized.queryParameter("ts"))

        // Verify other parameters are preserved
        assertEquals("20", parsedSanitized.queryParameter("limit"))
        assertEquals("40", parsedSanitized.queryParameter("offset"))

        // Verify raw sensitive strings are completely absent
        assertFalse(sanitized.contains("super-secret-key"))
        assertFalse(sanitized.contains("abcdef0123456789"))
        assertFalse(sanitized.contains("12345"))
    }

    @Test
    fun sanitizeUrlForTelemetry_preservesUrlsWithoutSensitiveParams() {
        val originalUrl = "https://gateway.marvel.com/v1/public/characters?name=Spider-Man&limit=10".toHttpUrl()

        val sanitized = sanitizeUrlForTelemetry(originalUrl)
        val parsedSanitized = sanitized.toHttpUrl()

        assertEquals("Spider-Man", parsedSanitized.queryParameter("name"))
        assertEquals("10", parsedSanitized.queryParameter("limit"))
        assertEquals(originalUrl.toString(), sanitized)
    }

    @Test
    fun sanitizeUrlForTelemetry_handlesUrlWithNoQueryParams() {
        val originalUrl = "https://gateway.marvel.com/v1/public/comics".toHttpUrl()

        val sanitized = sanitizeUrlForTelemetry(originalUrl)

        assertEquals(originalUrl.toString(), sanitized)
    }

    @Test
    fun interceptor_logsSanitizedUrlAndRedactsSensitiveQueryParams() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("OK"))

        val loggedMessages = mutableListOf<String>()
        val interceptor = RedactingHttpLoggingInterceptor { message ->
            loggedMessages.add(message)
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        val requestUrl = server.url("/v1/public/comics")
            .newBuilder()
            .addQueryParameter("ts", "99999")
            .addQueryParameter("apikey", "my-public-key")
            .addQueryParameter("hash", "my-md5-hash")
            .addQueryParameter("format", "comic")
            .build()

        val request = Request.Builder().url(requestUrl).build()
        val response = client.newCall(request).execute()

        assertEquals(200, response.code)

        // Verify logging output
        assertTrue("Expected logged messages to be non-empty", loggedMessages.isNotEmpty())
        val combinedLog = loggedMessages.joinToString("\n")

        // Sensitive credentials must NOT be logged in plaintext
        assertFalse("Log must not contain sensitive apikey", combinedLog.contains("my-public-key"))
        assertFalse("Log must not contain sensitive hash", combinedLog.contains("my-md5-hash"))
        assertFalse("Log must not contain sensitive ts", combinedLog.contains("99999"))

        // Redacted indicator and non-sensitive params should be in the log
        assertTrue("Log should indicate REDACTED", combinedLog.contains("REDACTED"))
        assertTrue("Log should keep format parameter", combinedLog.contains("format=comic"))
    }

    @Test
    fun sanitizeUrlForTelemetry_replacesTmdbSensitiveParamsWhilePreservingOthers() {
        val originalUrl =
            "https://api.themoviedb.org/3/account?api_key=tmdb-secret-key&session_id=user-session-12345&language=en-US"
                .toHttpUrl()

        val sanitized = sanitizeUrlForTelemetry(originalUrl)
        val parsedSanitized = sanitized.toHttpUrl()

        // Verify TMDB sensitive parameters are replaced with [REDACTED]
        assertEquals("[REDACTED]", parsedSanitized.queryParameter("api_key"))
        assertEquals("[REDACTED]", parsedSanitized.queryParameter("session_id"))

        // Verify other parameters are preserved
        assertEquals("en-US", parsedSanitized.queryParameter("language"))

        // Verify raw sensitive strings are completely absent
        assertFalse(sanitized.contains("tmdb-secret-key"))
        assertFalse(sanitized.contains("user-session-12345"))
    }

    @Test
    fun interceptor_redactsTmdbSensitiveQueryParamsInLogs() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("OK"))

        val loggedMessages = mutableListOf<String>()
        val interceptor = RedactingHttpLoggingInterceptor { message ->
            loggedMessages.add(message)
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        val requestUrl = server.url("/3/account")
            .newBuilder()
            .addQueryParameter("api_key", "tmdb-secret-key")
            .addQueryParameter("session_id", "secret-session-id")
            .addQueryParameter("page", "1")
            .build()

        val request = Request.Builder().url(requestUrl).build()
        val response = client.newCall(request).execute()

        assertEquals(200, response.code)

        val combinedLog = loggedMessages.joinToString("\n")
        assertFalse("Log must not contain sensitive api_key", combinedLog.contains("tmdb-secret-key"))
        assertFalse("Log must not contain sensitive session_id", combinedLog.contains("secret-session-id"))
        assertTrue("Log should indicate REDACTED", combinedLog.contains("REDACTED"))
        assertTrue("Log should keep page parameter", combinedLog.contains("page=1"))
    }
}
