package com.adauto.marvel.core.database

import com.adauto.marvel.core.database.entity.FavoriteMovieEntity
import com.adauto.marvel.core.database.entity.MovieEntity
import com.adauto.marvel.core.database.entity.WatchlistMovieEntity
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MovieEntityUnitTest {

    @Test
    fun movieEntity_creationAndPropertyAccess() {
        val entity = MovieEntity(
            id = 550,
            category = "POPULAR",
            position = 1,
            title = "Fight Club",
            overview = "An insomniac office worker...",
            posterPath = "/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg",
            backdropPath = "/hZkgoQYus5vegHoetLkCJzb17zJ.jpg",
            voteAverage = 8.4,
            releaseDate = "1999-10-15",
            runtime = 139,
            genresJson = "[\"Drama\", \"Thriller\"]",
            castJson = "[\"Brad Pitt\", \"Edward Norton\"]",
            fetchedAt = 123456789L
        )

        assertThat(entity.id).isEqualTo(550)
        assertThat(entity.category).isEqualTo("POPULAR")
        assertThat(entity.position).isEqualTo(1)
        assertThat(entity.title).isEqualTo("Fight Club")
        assertThat(entity.posterPath).isEqualTo("/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg")
        assertThat(entity.voteAverage).isEqualTo(8.4)
    }

    @Test
    fun favoriteMovieEntity_creationAndEquality() {
        val fav = FavoriteMovieEntity(
            movieId = 550,
            title = "Fight Club",
            posterPath = "/poster.jpg",
            voteAverage = 8.4,
            releaseDate = "1999-10-15",
            addedAt = 1000L
        )

        assertThat(fav.movieId).isEqualTo(550)
        assertThat(fav.title).isEqualTo("Fight Club")
        assertThat(fav.addedAt).isEqualTo(1000L)
    }

    @Test
    fun watchlistMovieEntity_creationAndEquality() {
        val watchlist = WatchlistMovieEntity(
            movieId = 550,
            title = "Fight Club",
            posterPath = "/poster.jpg",
            voteAverage = 8.4,
            releaseDate = "1999-10-15",
            watchedAt = 2000L
        )

        assertThat(watchlist.movieId).isEqualTo(550)
        assertThat(watchlist.title).isEqualTo("Fight Club")
        assertThat(watchlist.watchedAt).isEqualTo(2000L)
    }
}
