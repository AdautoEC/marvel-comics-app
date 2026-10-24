package com.adauto.marvel.core.network.security

import com.adauto.marvel.core.network.BuildConfig
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class TmdbCredentialsTest {

    @Test
    fun serializeAndDeserialize_credentialsPreserved() {
        val credentials = TmdbCredentials(
            apiKey = "test_api_key_12345",
            readAccessToken = "test_read_access_token_67890"
        )
        val jsonString = Json.encodeToString(TmdbCredentials.serializer(), credentials)
        val deserialized = Json.decodeFromString(TmdbCredentials.serializer(), jsonString)

        assertEquals(credentials, deserialized)
        assertEquals("test_api_key_12345", deserialized.apiKey)
        assertEquals("test_read_access_token_67890", deserialized.readAccessToken)
    }

    @Test
    fun parseLegacyString_fallbackToApiKey() {
        val legacyString = "legacy_raw_tmdb_api_key_abc"
        val credentials = TmdbCredentials.parse(legacyString)

        assertEquals("legacy_raw_tmdb_api_key_abc", credentials.apiKey)
        assertNull(credentials.readAccessToken)
    }

    @Test
    fun parseValidJsonString_returnsCredentials() {
        val json = """{"apiKey":"key1","readAccessToken":"token1"}"""
        val credentials = TmdbCredentials.parse(json)

        assertEquals("key1", credentials.apiKey)
        assertEquals("token1", credentials.readAccessToken)
    }

    @Test
    fun parseBlankString_returnsEmptyCredentials() {
        val credentials = TmdbCredentials.parse("   ")

        assertNull(credentials.apiKey)
        assertNull(credentials.readAccessToken)
    }

    @Test
    fun keysUnavailableException_defaultsAndInheritance() {
        val exception: Throwable = KeysUnavailableException()

        assertEquals("TMDB API credentials unavailable", exception.message)
        assertTrue(exception is IOException)
    }

    @Test
    fun buildConfigFields_accessibleInDebugBuild() {
        val apiKey = BuildConfig.TMDB_API_KEY
        val accessToken = BuildConfig.TMDB_READ_ACCESS_TOKEN

        assertNotNull(apiKey)
        assertNotNull(accessToken)
    }
}
