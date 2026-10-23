package com.adauto.marvel.feature.comics.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.adauto.marvel.core.model.ComicSummary
import com.adauto.marvel.core.model.MovieCategory
import com.adauto.marvel.core.model.SessionState
import com.adauto.marvel.feature.comics.ComicListUiState

data class ComicListPreviewState(
    val uiState: ComicListUiState,
    val comics: List<ComicSummary> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class ComicListUiStatePreviewParameterProvider : PreviewParameterProvider<ComicListPreviewState> {
    override val values: Sequence<ComicListPreviewState> = sequenceOf(
        // 1. Loading / Shimmer State
        ComicListPreviewState(
            uiState = ComicListUiState(),
            isLoading = true
        ),
        // 2. Populated Success State (Authenticated)
        ComicListPreviewState(
            uiState = ComicListUiState(
                sessionState = SessionState.Authenticated(
                    accountId = 42,
                    username = "tonystark",
                    sessionId = "sess_42"
                )
            ),
            comics = listOf(
                ComicSummary(
                    id = 1,
                    title = "The Amazing Spider-Man #800",
                    issueNumber = "#800",
                    coverUrl = "https://i.annihil.us/u/prod/marvel/i/mg/c/50/5b55018a1a361.jpg"
                ),
                ComicSummary(
                    id = 2,
                    title = "Avengers: Endgame Prelude #1",
                    issueNumber = "#1",
                    coverUrl = "https://i.annihil.us/u/prod/marvel/i/mg/c/50/5b55018a1a362.jpg"
                ),
                ComicSummary(
                    id = 3,
                    title = "X-Men: House of X #1",
                    issueNumber = "#1",
                    coverUrl = "https://i.annihil.us/u/prod/marvel/i/mg/c/50/5b55018a1a363.jpg"
                ),
                ComicSummary(
                    id = 4,
                    title = "Daredevil: Born Again #1",
                    issueNumber = "#1",
                    coverUrl = null
                )
            )
        ),
        // 3. Offline State with Comics
        ComicListPreviewState(
            uiState = ComicListUiState(isOffline = true),
            comics = listOf(
                ComicSummary(
                    id = 1,
                    title = "The Amazing Spider-Man #800",
                    issueNumber = "#800",
                    coverUrl = "https://i.annihil.us/u/prod/marvel/i/mg/c/50/5b55018a1a361.jpg"
                )
            )
        ),
        // 4. Empty State
        ComicListPreviewState(
            uiState = ComicListUiState(),
            comics = emptyList()
        ),
        // 5. Error State
        ComicListPreviewState(
            uiState = ComicListUiState(),
            errorMessage = "Limite de requisições diário da Marvel atingido. Tente novamente mais tarde."
        ),
        // 6. Watchlist Tab (Guest)
        ComicListPreviewState(
            uiState = ComicListUiState(
                selectedCategory = MovieCategory.WATCHLIST,
                sessionState = SessionState.Guest
            )
        )
    )
}
