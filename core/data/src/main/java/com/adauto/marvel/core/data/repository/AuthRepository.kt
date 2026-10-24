package com.adauto.marvel.core.data.repository

import com.adauto.marvel.core.model.AccountProfile
import com.adauto.marvel.core.model.Outcome
import com.adauto.marvel.core.model.SessionState
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val sessionState: StateFlow<SessionState>
    suspend fun createRequestToken(): Outcome<String>
    suspend fun completeAuthentication(requestToken: String): Outcome<AccountProfile>
    suspend fun logout()
}
