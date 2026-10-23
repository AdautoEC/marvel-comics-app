package com.adauto.marvel.core.network.di

import com.adauto.marvel.core.network.interceptor.TmdbAuthInterceptor
import com.adauto.marvel.core.network.telemetry.RedactingHttpLoggingInterceptor
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkModuleTest {

    @Test
    fun provideJson_createsJsonWithLenientConfig() {
        val json = NetworkModule.provideJson()
        assertNotNull(json)
    }

    @Test
    fun provideTmdbOkHttpClient_configuresInterceptors() {
        val authInterceptor = mockk<TmdbAuthInterceptor>(relaxed = true)

        val client = NetworkModule.provideTmdbOkHttpClient(authInterceptor)

        assertEquals(15000, client.connectTimeoutMillis)
        assertEquals(20000, client.readTimeoutMillis)
        assertTrue(client.interceptors.contains(authInterceptor))
        assertTrue(client.interceptors.any { it is RedactingHttpLoggingInterceptor })
    }

    @Test
    fun provideRetrofit_and_provideTmdbApi_createProperlyConfiguredInstances() {
        val authInterceptor = mockk<TmdbAuthInterceptor>(relaxed = true)
        val json = NetworkModule.provideJson()
        val client = NetworkModule.provideTmdbOkHttpClient(authInterceptor)

        val retrofit = NetworkModule.provideRetrofit(client, json)
        assertEquals(NetworkModule.TMDB_BASE_URL, retrofit.baseUrl().toString())

        val api = NetworkModule.provideTmdbApi(retrofit)
        assertNotNull(api)
    }
}
