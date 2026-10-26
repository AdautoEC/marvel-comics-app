package com.adauto.cinemahub.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TmdbDeleteSessionRequestDto(
    @SerialName("session_id") val sessionId: String
)
