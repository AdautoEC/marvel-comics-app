package com.adauto.marvel.core.network.interceptor

import com.adauto.marvel.core.network.security.TmdbApiKeyProvider
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class TmdbAuthInterceptor @Inject constructor(
    private val keyProvider: TmdbApiKeyProvider
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val apiKey = runBlocking { keyProvider.getApiKey() }

        val newUrl = if (!apiKey.isNullOrBlank() && request.url.queryParameter("api_key") == null) {
            request.url.newBuilder()
                .addQueryParameter("api_key", apiKey)
                .build()
        } else {
            request.url
        }

        return chain.proceed(request.newBuilder().url(newUrl).build())
    }
}
