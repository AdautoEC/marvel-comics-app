package com.adauto.cinemahub.core.data.repository

import com.adauto.cinemahub.core.model.AccountProfile
import com.adauto.cinemahub.core.model.Outcome
import com.adauto.cinemahub.core.model.SessionState
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val sessionState: StateFlow<SessionState>
    suspend fun buildAuthorizationUrl(): Outcome<String>
    suspend fun createRequestToken(): Outcome<String>
    suspend fun completeAuthentication(requestToken: String): Outcome<AccountProfile>
    suspend fun logout()
}
