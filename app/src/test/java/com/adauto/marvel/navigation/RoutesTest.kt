package com.adauto.marvel.navigation

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

class RoutesTest {

    @Test
    fun movieListRoute_serializesAndDeserializesCorrectly() {
        val encoded = Json.encodeToString(MovieListRoute)
        val decoded = Json.decodeFromString<MovieListRoute>(encoded)

        assertThat(decoded).isEqualTo(MovieListRoute)
    }

    @Test
    fun movieDetailRoute_serializesAndDeserializesWithParameters() {
        val original = MovieDetailRoute(movieId = 42)
        val encoded = Json.encodeToString(original)
        val decoded = Json.decodeFromString<MovieDetailRoute>(encoded)

        assertThat(decoded.movieId).isEqualTo(42)
        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun movieDetailRoute_supportsCopyAndEquality() {
        val route1 = MovieDetailRoute(movieId = 100)
        val route2 = route1.copy(movieId = 200)

        assertThat(route1.movieId).isEqualTo(100)
        assertThat(route2.movieId).isEqualTo(200)
        assertThat(route1).isNotEqualTo(route2)
    }
}
