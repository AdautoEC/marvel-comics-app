package com.adauto.marvel.core.network.interceptor

import com.adauto.marvel.core.network.security.MarvelKeyProvider
import com.adauto.marvel.core.network.security.computeMarvelHash
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.time.Clock
import javax.inject.Inject

class MarvelAuthenticator @Inject constructor(
    private val keyProvider: MarvelKeyProvider,
    private val clock: Clock = Clock.systemUTC()
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.code != 401) {
            return null
        }

        // Loop prevention: do not retry if this response was already a retry attempt
        if (response.priorResponse != null) {
            return null
        }

        val request = response.request
        val host = request.url.host
        if (!host.contains("gateway.marvel.com")) {
            return null
        }

        val refreshed = try {
            runBlocking { keyProvider.forceRefresh() }
        } catch (_: Exception) {
            false
        }

        if (!refreshed) {
            return null
        }

        val keys = try {
            runBlocking { keyProvider.current() }
        } catch (_: Exception) {
            null
        } ?: return null

        val ts = clock.millis().toString()
        val hash = computeMarvelHash(ts, keys.privateKey, keys.publicKey)

        val newUrl = request.url.newBuilder()
            .setQueryParameter("ts", ts)
            .setQueryParameter("apikey", keys.publicKey)
            .setQueryParameter("hash", hash)
            .build()

        return request.newBuilder()
            .url(newUrl)
            .build()
    }
}
