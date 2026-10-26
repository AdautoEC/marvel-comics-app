package com.adauto.marvel.feature.detail.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.adauto.marvel.core.model.MovieDetail
import com.adauto.marvel.core.model.UiText
import com.adauto.marvel.core.ui.R
import com.adauto.marvel.feature.detail.MovieDetailUiState

class MovieDetailUiStatePreviewParameterProvider : PreviewParameterProvider<MovieDetailUiState> {
    override val values: Sequence<MovieDetailUiState> = sequenceOf(
        // 1. Loading State
        MovieDetailUiState(
            isLoading = true
        ),
        // 2. Error State
        MovieDetailUiState(
            errorMessage = UiText.Resource(R.string.error_load_movies_fallback)
        ),
        // 3. Populated Movie (Active Actions: Favorito + Quero Assistir)
        MovieDetailUiState(
            movie = MovieDetail(
                id = 550,
                title = "Clube da Luta",
                overview = "Um homem deprimido que sofre de insônia conhece um vendedor estranho de sabonetes e funda um clube de luta clandestino que se transforma em algo muito mais grandioso.",
                posterUrl = "https://image.tmdb.org/t/p/w500/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/w1280/hZkgoQYus5vegHoetLkCJzb17zJ.jpg",
                voteAverage = 8.4,
                runtime = 139,
                releaseDate = "1999-10-15",
                genres = listOf("Drama", "Suspense"),
                cast = listOf("Edward Norton", "Brad Pitt", "Helena Bonham Carter"),
                isFavorite = true,
                isWatched = true
            )
        ),
        // 4. Populated Movie (Inactive Actions)
        MovieDetailUiState(
            movie = MovieDetail(
                id = 551,
                title = "Inception",
                overview = "Cobb steals information by entering the dreams of the targets.",
                posterUrl = "https://image.tmdb.org/t/p/w500/edv5CZvWj09upOsy2Y6IwDhK8bt.jpg",
                backdropUrl = null,
                voteAverage = 8.8,
                runtime = 148,
                releaseDate = "2010-07-16",
                genres = listOf("Ação", "Ficção Científica"),
                cast = listOf("Leonardo DiCaprio", "Joseph Gordon-Levitt"),
                isFavorite = false,
                isWatched = false
            )
        ),
        // 5. Populated Movie Minimal (No Overview, No Genres, No Cast)
        MovieDetailUiState(
            movie = MovieDetail(
                id = 552,
                title = "Filme Sem Sinopse",
                overview = "",
                posterUrl = null,
                backdropUrl = null,
                voteAverage = 6.0,
                runtime = null,
                releaseDate = null,
                genres = emptyList(),
                cast = emptyList(),
                isFavorite = false,
                isWatched = false
            )
        )
    )
}
