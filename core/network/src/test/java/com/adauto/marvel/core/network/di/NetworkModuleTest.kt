package com.adauto.marvel.core.network.di

import com.adauto.marvel.core.network.interceptor.MarvelAuthInterceptor
import com.adauto.marvel.core.network.interceptor.MarvelAuthenticator
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
    fun provideMarvelOkHttpClient_configuresInterceptorsAndAuthenticator() {
        val authInterceptor = mockk<MarvelAuthInterceptor>(relaxed = true)
        val authenticator = mockk<MarvelAuthenticator>(relaxed = true)

        val client = NetworkModule.provideMarvelOkHttpClient(authInterceptor, authenticator)

        assertEquals(15000, client.connectTimeoutMillis)
        assertEquals(20000, client.readTimeoutMillis)
        assertTrue(client.interceptors.contains(authInterceptor))
        assertTrue(client.interceptors.any { it is RedactingHttpLoggingInterceptor })
        assertEquals(authenticator, client.authenticator)
    }

    @Test
    fun provideRetrofit_and_provideMarvelApi_createProperlyConfiguredInstances() {
        val authInterceptor = mockk<MarvelAuthInterceptor>(relaxed = true)
        val authenticator = mockk<MarvelAuthenticator>(relaxed = true)
        val json = NetworkModule.provideJson()
        val client = NetworkModule.provideMarvelOkHttpClient(authInterceptor, authenticator)

        val retrofit = NetworkModule.provideRetrofit(client, json)
        assertEquals(NetworkModule.MARVEL_BASE_URL, retrofit.baseUrl().toString())

        val api = NetworkModule.provideMarvelApi(retrofit)
        assertNotNull(api)
    }
}
