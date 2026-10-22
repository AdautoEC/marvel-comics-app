package com.adauto.marvel.core.data.repository

import com.adauto.marvel.core.model.Outcome
import com.adauto.marvel.core.model.SessionState
import com.adauto.marvel.core.network.model.TmdbAccountDto
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val sessionState: StateFlow<SessionState>
    suspend fun createRequestToken(): Outcome<String>
    suspend fun completeAuthentication(requestToken: String): Outcome<TmdbAccountDto>
    suspend fun logout()
}
