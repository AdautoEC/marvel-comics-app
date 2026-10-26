package com.adauto.marvel.core.data.mapper

import com.adauto.marvel.core.database.entity.FavoriteMovieEntity
import com.adauto.marvel.core.database.entity.MovieEntity
import com.adauto.marvel.core.database.entity.WatchlistMovieEntity
import com.adauto.marvel.core.model.MovieDetail
import com.adauto.marvel.core.model.MovieSummary
import com.adauto.marvel.core.network.model.TmdbMovieDetailDto
import com.adauto.marvel.core.network.model.TmdbMovieDto

private const val TMDB_POSTER_BASE_URL = "https://image.tmdb.org/t/p/w500"
private const val TMDB_BACKDROP_BASE_URL = "https://image.tmdb.org/t/p/w780"

fun TmdbMovieDto.toMovieEntity(
    category: String,
    position: Int,
    fetchedAt: Long
): MovieEntity {
    return MovieEntity(
        id = id,
        category = category,
        position = position,
        title = title,
        overview = overview,
        posterPath = posterPath,
        backdropPath = backdropPath,
        voteAverage = voteAverage,
        releaseDate = releaseDate,
        runtime = null,
        genresJson = null,
        castJson = null,
        fetchedAt = fetchedAt
    )
}

fun MovieEntity.toMovieSummary(
    isFavorite: Boolean = false,
    isInWatchlist: Boolean = false
): MovieSummary {
    return MovieSummary(
        id = id,
        title = title,
        posterUrl = posterPath?.let { "$TMDB_POSTER_BASE_URL$it" },
        backdropUrl = backdropPath?.let { "$TMDB_BACKDROP_BASE_URL$it" },
        voteAverage = voteAverage,
        releaseDate = releaseDate,
        isFavorite = isFavorite,
        isInWatchlist = isInWatchlist
    )
}

fun TmdbMovieDetailDto.toMovieDetail(
    isFavorite: Boolean = false,
    isInWatchlist: Boolean = false
): MovieDetail {
    return MovieDetail(
        id = id,
        title = title,
        overview = overview,
        posterUrl = posterPath?.let { "$TMDB_POSTER_BASE_URL$it" },
        backdropUrl = backdropPath?.let { "$TMDB_BACKDROP_BASE_URL$it" },
        voteAverage = voteAverage,
        runtime = runtime,
        releaseDate = releaseDate,
        genres = genres.map { it.name },
        cast = credits?.cast?.map { it.name } ?: emptyList(),
        isFavorite = isFavorite,
        isInWatchlist = isInWatchlist
    )
}

fun FavoriteMovieEntity.toMovieSummary(): MovieSummary {
    return MovieSummary(
        id = movieId,
        title = title,
        posterUrl = posterPath?.let { "$TMDB_POSTER_BASE_URL$it" },
        backdropUrl = null,
        voteAverage = voteAverage,
        releaseDate = releaseDate,
        isFavorite = true,
        isInWatchlist = false
    )
}

fun WatchlistMovieEntity.toMovieSummary(): MovieSummary {
    return MovieSummary(
        id = movieId,
        title = title,
        posterUrl = posterPath?.let { "$TMDB_POSTER_BASE_URL$it" },
        backdropUrl = null,
        voteAverage = voteAverage,
        releaseDate = releaseDate,
        isFavorite = false,
        isInWatchlist = true
    )
}
