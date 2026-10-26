package com.adauto.marvel.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MovieModelTest {

    @Test
    fun movieSummary_storesAndExposesProperties() {
        val movie = MovieSummary(
            id = 550,
            title = "Fight Club",
            posterUrl = "https://image.tmdb.org/t/p/w500/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w780/hZkgoQYus5vegHoetLkCJzb17zJ.jpg",
            voteAverage = 8.433,
            releaseDate = "1999-10-15",
            isFavorite = true,
            isInWatchlist = false
        )

        assertThat(movie.id).isEqualTo(550)
        assertThat(movie.title).isEqualTo("Fight Club")
        assertThat(movie.isFavorite).isTrue()
        assertThat(movie.isInWatchlist).isFalse()
        assertThat(movie.ratingFormatted).isEqualTo("8.4")
        assertThat(movie.releaseYear).isEqualTo("1999")
    }

    @Test
    fun movieDetail_storesAndExposesDetails() {
        val detail = MovieDetail(
            id = 550,
            title = "Fight Club",
            overview = "An insomniac office worker and a devil-may-care soap maker form an underground fight club.",
            posterUrl = "https://image.tmdb.org/t/p/w500/poster.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w780/backdrop.jpg",
            voteAverage = 8.4,
            runtime = 139,
            releaseDate = "1999-10-15",
            genres = listOf("Drama", "Thriller"),
            cast = listOf("Brad Pitt", "Edward Norton", "Helena Bonham Carter"),
            isFavorite = true,
            isInWatchlist = true
        )

        assertThat(detail.runtimeFormatted).isEqualTo("139 min")
        assertThat(detail.genres).containsExactly("Drama", "Thriller")
        assertThat(detail.cast).hasSize(3)
        assertThat(detail.isFavorite).isTrue()
        assertThat(detail.isInWatchlist).isTrue()
    }

    @Test
    fun movieCategory_containsExpectedCategories() {
        val categories = MovieCategory.entries
        assertThat(categories).containsExactly(
            MovieCategory.POPULAR,
            MovieCategory.TOP_RATED,
            MovieCategory.FAVORITES,
            MovieCategory.WATCHLIST
        ).inOrder()
    }
}
