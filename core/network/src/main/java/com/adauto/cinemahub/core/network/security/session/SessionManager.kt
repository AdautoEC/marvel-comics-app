package com.adauto.cinemahub.core.network.security.session

import com.adauto.cinemahub.core.model.SessionState
import kotlinx.coroutines.flow.StateFlow

interface SessionManager {
    val sessionState: StateFlow<SessionState>

    suspend fun awaitInitialSession(): SessionState

    suspend fun saveSession(
        accountId: Int,
        username: String,
        sessionId: String,
        avatarUrl: String? = null
    )

    suspend fun clearSession()
}
