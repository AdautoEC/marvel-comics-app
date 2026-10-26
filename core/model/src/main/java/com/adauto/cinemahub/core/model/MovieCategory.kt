package com.adauto.cinemahub.core.model

enum class MovieCategory(val displayName: String) {
    POPULAR("Populares"),
    TOP_RATED("Mais Votados"),
    FAVORITES("Favoritos"),
    WATCHLIST("Quero Assistir");

    companion object {
        @Deprecated("Renamed to WATCHLIST", ReplaceWith("WATCHLIST"))
        val WATCHED: MovieCategory = WATCHLIST
    }
}
