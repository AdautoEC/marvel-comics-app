package com.adauto.marvel.core.ui

import com.adauto.marvel.core.model.MovieSummary
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MovieCardModelTest {

    @Test
    fun movieSummary_providesCorrectDisplayValues() {
        val movie = MovieSummary(
            id = 100,
            title = "Spider-Man",
            posterUrl = "https://image.tmdb.org/t/p/w500/spiderman.jpg",
            voteAverage = 8.46,
            releaseDate = "2026-07-29",
            isFavorite = true,
            isWatched = true
        )

        assertThat(movie.ratingFormatted).isEqualTo("8.5")
        assertThat(movie.releaseYear).isEqualTo("2026")
        assertThat(movie.isFavorite).isTrue()
        assertThat(movie.isWatched).isTrue()
    }
}
