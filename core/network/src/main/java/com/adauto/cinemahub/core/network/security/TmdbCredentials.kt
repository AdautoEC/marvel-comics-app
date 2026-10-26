package com.adauto.cinemahub.core.network.security

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class TmdbCredentials(
    val apiKey: String? = null,
    val readAccessToken: String? = null
) {
    val isNotEmpty: Boolean
        get() = !apiKey.isNullOrBlank() || !readAccessToken.isNullOrBlank()

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun parse(raw: String): TmdbCredentials {
            if (raw.isBlank()) return TmdbCredentials()
            return try {
                json.decodeFromString<TmdbCredentials>(raw)
            } catch (_: Exception) {
                TmdbCredentials(apiKey = raw)
            }
        }

        fun fromString(raw: String): TmdbCredentials = parse(raw)
    }
}
