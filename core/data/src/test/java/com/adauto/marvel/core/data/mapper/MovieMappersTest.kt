package com.adauto.marvel.core.data.mapper

import com.adauto.marvel.core.database.entity.FavoriteMovieEntity
import com.adauto.marvel.core.database.entity.MovieEntity
import com.adauto.marvel.core.database.entity.WatchedMovieEntity
import com.adauto.marvel.core.network.model.TmdbCastDto
import com.adauto.marvel.core.network.model.TmdbCreditsDto
import com.adauto.marvel.core.network.model.TmdbGenreDto
import com.adauto.marvel.core.network.model.TmdbMovieDetailDto
import com.adauto.marvel.core.network.model.TmdbMovieDto
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MovieMappersTest {

    @Test
    fun tmdbMovieDto_toMovieEntity_mapsCorrectly() {
        val dto = TmdbMovieDto(
            id = 550,
            title = "Fight Club",
            overview = "An insomniac office worker...",
            posterPath = "/poster.jpg",
            backdropPath = "/backdrop.jpg",
            voteAverage = 8.4,
            releaseDate = "1999-10-15"
        )

        val entity = dto.toMovieEntity(category = "POPULAR", position = 0, fetchedAt = 1000L)

        assertThat(entity.id).isEqualTo(550)
        assertThat(entity.category).isEqualTo("POPULAR")
        assertThat(entity.position).isEqualTo(0)
        assertThat(entity.title).isEqualTo("Fight Club")
        assertThat(entity.posterPath).isEqualTo("/poster.jpg")
        assertThat(entity.fetchedAt).isEqualTo(1000L)
    }

    @Test
    fun movieEntity_toMovieSummary_constructsTmdbImageUrls() {
        val entity = MovieEntity(
            id = 550,
            category = "POPULAR",
            position = 0,
            title = "Fight Club",
            overview = "An insomniac office worker...",
            posterPath = "/poster.jpg",
            backdropPath = "/backdrop.jpg",
            voteAverage = 8.433,
            releaseDate = "1999-10-15",
            fetchedAt = 1000L
        )

        val summary = entity.toMovieSummary(isFavorite = true, isWatched = false)

        assertThat(summary.id).isEqualTo(550)
        assertThat(summary.posterUrl).isEqualTo("https://image.tmdb.org/t/p/w500/poster.jpg")
        assertThat(summary.backdropUrl).isEqualTo("https://image.tmdb.org/t/p/w780/backdrop.jpg")
        assertThat(summary.isFavorite).isTrue()
        assertThat(summary.isWatched).isFalse()
    }

    @Test
    fun tmdbMovieDetailDto_toMovieDetail_mapsCreditsAndGenres() {
        val dto = TmdbMovieDetailDto(
            id = 550,
            title = "Fight Club",
            overview = "Synopsis...",
            posterPath = "/poster.jpg",
            backdropPath = "/backdrop.jpg",
            voteAverage = 8.4,
            runtime = 139,
            releaseDate = "1999-10-15",
            genres = listOf(TmdbGenreDto(18, "Drama")),
            credits = TmdbCreditsDto(listOf(TmdbCastDto(1, "Brad Pitt", "Tyler Durden")))
        )

        val detail = dto.toMovieDetail(isFavorite = true, isWatched = true)

        assertThat(detail.id).isEqualTo(550)
        assertThat(detail.genres).containsExactly("Drama")
        assertThat(detail.cast).containsExactly("Brad Pitt")
        assertThat(detail.isFavorite).isTrue()
        assertThat(detail.isWatched).isTrue()
    }

    @Test
    fun favoriteAndWatchedEntities_mapToMovieSummary() {
        val fav = FavoriteMovieEntity(
            movieId = 101,
            title = "Inception",
            posterPath = "/inception.jpg",
            voteAverage = 8.8,
            releaseDate = "2010-07-16",
            addedAt = 5000L
        )

        val favSummary = fav.toMovieSummary()
        assertThat(favSummary.id).isEqualTo(101)
        assertThat(favSummary.isFavorite).isTrue()
        assertThat(favSummary.posterUrl).isEqualTo("https://image.tmdb.org/t/p/w500/inception.jpg")

        val watched = WatchedMovieEntity(
            movieId = 102,
            title = "Interstellar",
            posterPath = "/interstellar.jpg",
            voteAverage = 8.7,
            releaseDate = "2014-11-05",
            watchedAt = 6000L
        )

        val watchedSummary = watched.toMovieSummary()
        assertThat(watchedSummary.id).isEqualTo(102)
        assertThat(watchedSummary.isWatched).isTrue()
        assertThat(watchedSummary.posterUrl).isEqualTo("https://image.tmdb.org/t/p/w500/interstellar.jpg")
    }
}
