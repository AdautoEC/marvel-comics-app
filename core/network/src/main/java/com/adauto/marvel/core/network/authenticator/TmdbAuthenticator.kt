package com.adauto.marvel.core.network.authenticator

import com.adauto.marvel.core.network.security.TmdbApiKeyProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TmdbAuthenticator @Inject constructor(
    private val keyProvider: TmdbApiKeyProvider,
    private val json: Json
) : Authenticator {

    @Serializable
    private data class TmdbStatusResponse(
        @SerialName("status_code")
        val statusCode: Int? = null
    )

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= MAX_RETRY_COUNT) {
            return null
        }

        val bodyString = try {
            response.peekBody(PEEK_BYTE_COUNT).string()
        } catch (_: Exception) {
            null
        }

        val statusCode = bodyString?.let {
            try {
                json.decodeFromString<TmdbStatusResponse>(it).statusCode
            } catch (_: Exception) {
                null
            }
        }

        if (statusCode != TMDB_INVALID_KEY_STATUS_CODE) {
            // TMDB status_code 7 means "Invalid API key".
            // Other 401s (such as invalid session ID or unapproved token) are NOT key rotation events;
            // return null so OkHttp leaves them to the repository/session manager.
            return null
        }

        synchronized(this) {
            val currentCredentials = runBlocking { keyProvider.getCredentials() }
            val requestKey = response.request.url.queryParameter("api_key")
            val requestBearer = response.request.header("Authorization")?.removePrefix("Bearer ")

            val hasRotated = (currentCredentials?.apiKey != null && currentCredentials.apiKey != requestKey) ||
                (currentCredentials?.readAccessToken != null && currentCredentials.readAccessToken != requestBearer)

            val newCredentials = if (hasRotated) {
                currentCredentials
            } else {
                runBlocking { keyProvider.refreshKeyForced() }
            }

            if (newCredentials == null || !newCredentials.isNotEmpty) return null

            val requestBuilder = response.request.newBuilder()
            if (!newCredentials.readAccessToken.isNullOrBlank()) {
                requestBuilder.header("Authorization", "Bearer ${newCredentials.readAccessToken}")
            } else if (!newCredentials.apiKey.isNullOrBlank()) {
                val newUrl = response.request.url.newBuilder()
                    .setQueryParameter("api_key", newCredentials.apiKey)
                    .build()
                requestBuilder.url(newUrl)
            }
            return requestBuilder.build()
        }
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }

    companion object {
        private const val MAX_RETRY_COUNT = 2
        private const val PEEK_BYTE_COUNT = 1024L
        private const val TMDB_INVALID_KEY_STATUS_CODE = 7
    }
}
