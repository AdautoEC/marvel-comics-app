package com.adauto.cinemahub.core.network.di

import com.adauto.cinemahub.core.network.authenticator.TmdbAuthenticator
import com.adauto.cinemahub.core.network.interceptor.TmdbAuthInterceptor
import com.adauto.cinemahub.core.network.telemetry.RedactingHttpLoggingInterceptor
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkModuleTest {

    @Test
    fun provideTmdbOkHttpClient_configuresInterceptorsAndTimeouts() {
        val authInterceptor = mockk<TmdbAuthInterceptor>(relaxed = true)
        val authenticator = mockk<TmdbAuthenticator>(relaxed = true)

        val client = NetworkModule.provideTmdbOkHttpClient(authInterceptor, authenticator)

        assertEquals(15000, client.connectTimeoutMillis)
        assertEquals(20000, client.readTimeoutMillis)
        assertTrue(client.interceptors.contains(authInterceptor))
        assertTrue(client.interceptors.any { it is RedactingHttpLoggingInterceptor })
        assertEquals(authenticator, client.authenticator)
    }

    @Test
    fun provideRetrofit_configuresBaseUrl() {
        val authInterceptor = mockk<TmdbAuthInterceptor>(relaxed = true)
        val authenticator = mockk<TmdbAuthenticator>(relaxed = true)
        val json = NetworkModule.provideJson()
        val client = NetworkModule.provideTmdbOkHttpClient(authInterceptor, authenticator)

        val retrofit = NetworkModule.provideRetrofit(client, json)
        assertEquals(NetworkModule.TMDB_BASE_URL, retrofit.baseUrl().toString())
    }
}
