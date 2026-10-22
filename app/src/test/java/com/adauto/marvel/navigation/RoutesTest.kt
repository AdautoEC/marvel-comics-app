package com.adauto.marvel.navigation

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

class RoutesTest {

    @Test
    fun comicListRoute_serializesAndDeserializesCorrectly() {
        val encoded = Json.encodeToString(ComicListRoute)
        val decoded = Json.decodeFromString<ComicListRoute>(encoded)

        assertThat(decoded).isEqualTo(ComicListRoute)
    }

    @Test
    fun comicDetailRoute_serializesAndDeserializesWithParameters() {
        val original = ComicDetailRoute(comicId = 42)
        val encoded = Json.encodeToString(original)
        val decoded = Json.decodeFromString<ComicDetailRoute>(encoded)

        assertThat(decoded.comicId).isEqualTo(42)
        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun comicDetailRoute_supportsCopyAndEquality() {
        val route1 = ComicDetailRoute(comicId = 100)
        val route2 = route1.copy(comicId = 200)

        assertThat(route1.comicId).isEqualTo(100)
        assertThat(route2.comicId).isEqualTo(200)
        assertThat(route1).isNotEqualTo(route2)
    }
}
