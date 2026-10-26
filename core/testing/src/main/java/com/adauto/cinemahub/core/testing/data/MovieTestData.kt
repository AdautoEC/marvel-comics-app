package com.adauto.cinemahub.core.testing.data

import com.adauto.cinemahub.core.model.AccountProfile
import com.adauto.cinemahub.core.model.MovieDetail
import com.adauto.cinemahub.core.model.MovieSummary

object MovieTestData {

    @Suppress("LongParameterList")
    fun createMovieSummary(
        id: Int = 1,
        title: String = "Test Movie $id",
        posterUrl: String? = "https://image.tmdb.org/t/p/w500/test_$id.jpg",
        backdropUrl: String? = null,
        voteAverage: Double = 8.5,
        releaseDate: String? = "2024-05-01",
        isFavorite: Boolean = false,
        isInWatchlist: Boolean = false,
    ): MovieSummary = MovieSummary(
        id = id,
        title = title,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        voteAverage = voteAverage,
        releaseDate = releaseDate,
        isFavorite = isFavorite,
        isInWatchlist = isInWatchlist,
    )

    @Suppress("LongParameterList")
    fun createMovieDetail(
        id: Int = 1,
        title: String = "Test Movie $id",
        overview: String = "Test movie overview for movie $id.",
        posterUrl: String? = "https://image.tmdb.org/t/p/w500/test_$id.jpg",
        backdropUrl: String? = "https://image.tmdb.org/t/p/w1280/backdrop_$id.jpg",
        voteAverage: Double = 8.5,
        runtime: Int? = 120,
        releaseDate: String? = "2024-05-01",
        genres: List<String> = listOf("Action", "Adventure"),
        cast: List<String> = listOf("Actor One", "Actor Two"),
        isFavorite: Boolean = false,
        isInWatchlist: Boolean = false,
    ): MovieDetail = MovieDetail(
        id = id,
        title = title,
        overview = overview,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        voteAverage = voteAverage,
        runtime = runtime,
        releaseDate = releaseDate,
        genres = genres,
        cast = cast,
        isFavorite = isFavorite,
        isInWatchlist = isInWatchlist,
    )

    fun createAccountProfile(
        id: Int = 42,
        username: String = "testuser",
        name: String? = "Test User",
        includeAdult: Boolean = false,
    ): AccountProfile = AccountProfile(
        id = id,
        username = username,
        name = name,
        includeAdult = includeAdult,
    )
}
