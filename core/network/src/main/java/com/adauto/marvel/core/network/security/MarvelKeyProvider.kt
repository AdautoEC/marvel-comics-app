package com.adauto.marvel.core.network.security

import com.adauto.marvel.core.model.MarvelKeys

interface MarvelKeyProvider {
    suspend fun current(): MarvelKeys?
    suspend fun forceRefresh(): Boolean
    suspend fun refreshIfNewer()
}
