package com.adauto.cinemahub.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TmdbRequestTokenDto(
    @SerialName("success") val success: Boolean,
    @SerialName("request_token") val request_token: String,
    @SerialName("expires_at") val expires_at: String
)

@Serializable
data class TmdbCreateSessionRequestDto(
    @SerialName("request_token") val request_token: String
)

@Serializable
data class TmdbSessionResponseDto(
    @SerialName("success") val success: Boolean,
    @SerialName("session_id") val session_id: String
)

@Serializable
data class TmdbAccountDto(
    @SerialName("id") val id: Int,
    @SerialName("username") val username: String,
    @SerialName("name") val name: String? = null,
    @SerialName("include_adult") val includeAdult: Boolean = false,
    @SerialName("avatar") val avatar: TmdbAvatarDto? = null
)

@Serializable
data class TmdbAvatarDto(
    @SerialName("tmdb") val tmdb: TmdbAvatarPathDto? = null
)

@Serializable
data class TmdbAvatarPathDto(
    @SerialName("avatar_path") val avatar_path: String? = null
)

@Serializable
data class TmdbFavoriteRequestDto(
    @SerialName("media_type") val media_type: String = "movie",
    @SerialName("media_id") val media_id: Int,
    @SerialName("favorite") val favorite: Boolean
)

@Serializable
data class TmdbWatchlistRequestDto(
    @SerialName("media_type") val media_type: String = "movie",
    @SerialName("media_id") val media_id: Int,
    @SerialName("watchlist") val watchlist: Boolean
)

@Serializable
data class TmdbActionResponseDto(
    @SerialName("status_code") val status_code: Int,
    @SerialName("status_message") val status_message: String,
    @SerialName("success") val success: Boolean? = null
)
