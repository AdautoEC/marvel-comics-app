package com.adauto.cinemahub.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TmdbDeleteSessionResponseDto(
    @SerialName("success") val success: Boolean
)
