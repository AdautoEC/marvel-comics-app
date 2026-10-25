package com.adauto.marvel.core.network.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Test

class TmdbDeleteSessionResponseDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun deserialization_fromTmdbDeleteSessionPayload_succeeds() {
        val payload = """{"success": true}"""
        val response = json.decodeFromString<TmdbDeleteSessionResponseDto>(payload)
        assertThat(response.success).isTrue()
    }
}
