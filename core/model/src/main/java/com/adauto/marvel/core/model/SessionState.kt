package com.adauto.marvel.core.model

import kotlinx.serialization.Serializable

sealed interface SessionState {
    data object Guest : SessionState

    @Serializable
    data class Authenticated(
        val accountId: Int,
        val username: String,
        val sessionId: String,
        val avatarUrl: String? = null
    ) : SessionState
}
