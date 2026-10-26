package com.adauto.cinemahub.core.network.security

interface TmdbApiKeyProvider {
    suspend fun getApiKey(): String?
    suspend fun getReadAccessToken(): String? = null
    suspend fun getCredentials(): TmdbCredentials? = getApiKey()?.let { TmdbCredentials(apiKey = it) }
    suspend fun refreshKeyForced(): TmdbCredentials? = getCredentials()
}
