package com.adauto.cinemahub.core.network.interceptor

import com.adauto.cinemahub.core.network.security.KeysUnavailableException
import com.adauto.cinemahub.core.network.security.TmdbApiKeyProvider
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class TmdbAuthInterceptor @Inject constructor(
    private val keyProvider: TmdbApiKeyProvider
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val credentials = runCatching {
            runBlocking { keyProvider.getCredentials() }
        }.getOrNull()

        if (credentials == null || !credentials.isNotEmpty) {
            throw KeysUnavailableException()
        }

        val request = chain.request()
        val requestBuilder = request.newBuilder()

        if (!credentials.readAccessToken.isNullOrBlank()) {
            val newUrl = request.url.newBuilder()
                .removeAllQueryParameters("api_key")
                .build()
            requestBuilder
                .url(newUrl)
                .header("Authorization", "Bearer ${credentials.readAccessToken}")
        } else if (!credentials.apiKey.isNullOrBlank()) {
            val newUrl = request.url.newBuilder()
                .setQueryParameter("api_key", credentials.apiKey)
                .build()
            requestBuilder
                .url(newUrl)
                .removeHeader("Authorization")
        }

        return chain.proceed(requestBuilder.build())
    }
}

