package com.adauto.marvel.core.network.interceptor

import com.adauto.marvel.core.network.security.KeysUnavailableException
import com.adauto.marvel.core.network.security.TmdbApiKeyProvider
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class TmdbAuthInterceptor @Inject constructor(
    private val keyProvider: TmdbApiKeyProvider
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val credentials = runBlocking { keyProvider.getCredentials() }
        if (credentials == null || (credentials.apiKey.isNullOrBlank() && credentials.readAccessToken.isNullOrBlank())) {
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

