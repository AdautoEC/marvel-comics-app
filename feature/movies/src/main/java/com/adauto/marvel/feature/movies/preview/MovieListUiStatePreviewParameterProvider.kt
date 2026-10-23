package com.adauto.marvel.feature.movies.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.adauto.marvel.core.model.MovieCategory
import com.adauto.marvel.core.model.MovieSummary
import com.adauto.marvel.core.model.SessionState
import com.adauto.marvel.feature.movies.MovieListUiState

data class MovieListPreviewState(
    val uiState: MovieListUiState,
    val movies: List<MovieSummary> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class MovieListUiStatePreviewParameterProvider : PreviewParameterProvider<MovieListPreviewState> {
    override val values: Sequence<MovieListPreviewState> = sequenceOf(
        // 1. Loading / Shimmer State
        MovieListPreviewState(
            uiState = MovieListUiState(),
            isLoading = true
        ),
        // 2. Populated Success State (Authenticated)
        MovieListPreviewState(
            uiState = MovieListUiState(
                sessionState = SessionState.Authenticated(
                    accountId = 42,
                    username = "adautocine",
                    sessionId = "sess_42"
                )
            ),
            movies = listOf(
                MovieSummary(
                    id = 1,
                    title = "Clube da Luta",
                    posterUrl = "https://image.tmdb.org/t/p/w500/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg",
                    voteAverage = 8.4,
                    releaseDate = "1999-10-15",
                    isFavorite = true,
                    isWatched = true
                ),
                MovieSummary(
                    id = 2,
                    title = "Inception",
                    posterUrl = "https://image.tmdb.org/t/p/w500/edv5CZvWj09upOsy2Y6IwDhK8bt.jpg",
                    voteAverage = 8.8,
                    releaseDate = "2010-07-16",
                    isFavorite = false,
                    isWatched = false
                ),
                MovieSummary(
                    id = 3,
                    title = "Interestelar",
                    posterUrl = null,
                    voteAverage = 8.6,
                    releaseDate = "2014-11-07",
                    isFavorite = false,
                    isWatched = false
                )
            )
        ),
        // 3. Offline State with Movies
        MovieListPreviewState(
            uiState = MovieListUiState(isOffline = true),
            movies = listOf(
                MovieSummary(
                    id = 1,
                    title = "Clube da Luta",
                    posterUrl = "https://image.tmdb.org/t/p/w500/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg",
                    voteAverage = 8.4,
                    releaseDate = "1999-10-15",
                    isFavorite = true,
                    isWatched = true
                )
            )
        ),
        // 4. Empty State
        MovieListPreviewState(
            uiState = MovieListUiState(),
            movies = emptyList()
        ),
        // 5. Error State
        MovieListPreviewState(
            uiState = MovieListUiState(),
            errorMessage = "Limite de requisições excedido. Tente novamente mais tarde."
        ),
        // 6. Watchlist Tab (Guest)
        MovieListPreviewState(
            uiState = MovieListUiState(
                selectedCategory = MovieCategory.WATCHLIST,
                sessionState = SessionState.Guest
            )
        ),
        // 7. Search Active State
        MovieListPreviewState(
            uiState = MovieListUiState(
                isSearchActive = true,
                searchQuery = "Clube"
            ),
            movies = listOf(
                MovieSummary(
                    id = 1,
                    title = "Clube da Luta",
                    posterUrl = "https://image.tmdb.org/t/p/w500/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg",
                    voteAverage = 8.4,
                    releaseDate = "1999-10-15"
                )
            )
        )
    )
}
