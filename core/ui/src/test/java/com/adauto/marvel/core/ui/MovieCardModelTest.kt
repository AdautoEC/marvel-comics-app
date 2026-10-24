package com.adauto.marvel.core.ui

import androidx.compose.ui.graphics.Color
import com.adauto.marvel.core.model.MovieSummary
import com.adauto.marvel.core.ui.component.FavoritePillBackground
import com.adauto.marvel.core.ui.component.MovieCardBottomGradient
import com.adauto.marvel.core.ui.component.MovieCardGradientColors
import com.adauto.marvel.core.ui.component.RatingPillBackground
import com.adauto.marvel.core.ui.component.StarYellowColor
import com.adauto.marvel.core.ui.component.WatchedGreenColor
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

    @Test
    fun movieCardConstants_areConfiguredCorrectly() {
        assertThat(StarYellowColor).isEqualTo(Color(0xFFFFD700))
        assertThat(WatchedGreenColor).isEqualTo(Color(0xFF2E7D32))
        assertThat(RatingPillBackground).isEqualTo(Color.Black.copy(alpha = 0.7f))
        assertThat(FavoritePillBackground).isEqualTo(Color.Black.copy(alpha = 0.5f))
        assertThat(MovieCardGradientColors).hasSize(3)
        assertThat(MovieCardBottomGradient).isNotNull()
    }
}
