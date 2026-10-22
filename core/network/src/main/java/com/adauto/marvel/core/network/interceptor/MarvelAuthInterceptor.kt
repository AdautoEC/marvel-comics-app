package com.adauto.marvel.core.network.interceptor

import com.adauto.marvel.core.network.security.MarvelKeyProvider
import com.adauto.marvel.core.network.security.computeMarvelHash
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.time.Clock
import javax.inject.Inject

class MarvelKeysUnavailableException(message: String = "Marvel keys not available") : IOException(message)

class MarvelAuthInterceptor @Inject constructor(
    private val keyProvider: MarvelKeyProvider,
    private val clock: Clock = Clock.systemUTC()
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host

        if (!host.contains("gateway.marvel.com")) {
            return chain.proceed(request)
        }

        val keys = runBlocking { keyProvider.current() } ?: throw MarvelKeysUnavailableException()
        val ts = clock.millis().toString()
        val hash = computeMarvelHash(ts, keys.privateKey, keys.publicKey)

        val newUrl = request.url.newBuilder()
            .setQueryParameter("ts", ts)
            .setQueryParameter("apikey", keys.publicKey)
            .setQueryParameter("hash", hash)
            .build()

        return chain.proceed(request.newBuilder().url(newUrl).build())
    }
}
