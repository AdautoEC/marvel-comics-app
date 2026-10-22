package com.adauto.marvel.core.network.security

interface TmdbApiKeyProvider {
    suspend fun getApiKey(): String?
}
