package com.adauto.cinemahub.core.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.util.Locale

@Immutable
data class MovieDetail(
    val id: Int,
    val title: String,
    val overview: String,
    val posterUrl: String?,
    val backdropUrl: String? = null,
    val voteAverage: Double = 0.0,
    val runtime: Int? = null,
    val releaseDate: String? = null,
    val genres: ImmutableList<String> = persistentListOf(),
    val cast: ImmutableList<String> = persistentListOf(),
    val isFavorite: Boolean = false,
    val isInWatchlist: Boolean = false
) {
    val ratingFormatted: String
        get() = String.format(Locale.US, "%.1f", voteAverage)

    val runtimeFormatted: String?
        get() = runtime?.let { "$it min" }

    val releaseYear: String?
        get() = releaseDate?.takeIf { it.length >= 4 }?.substring(0, 4)
}
