package com.adauto.cinemahub.core.model

import androidx.compose.runtime.Immutable
import java.util.Locale

@Immutable
data class MovieSummary(
    val id: Int,
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String? = null,
    val voteAverage: Double = 0.0,
    val releaseDate: String? = null,
    val isFavorite: Boolean = false,
    val isInWatchlist: Boolean = false
) {
    val ratingFormatted: String
        get() = String.format(Locale.US, "%.1f", voteAverage)

    val releaseYear: String?
        get() = releaseDate?.takeIf { it.length >= 4 }?.substring(0, 4)
}
