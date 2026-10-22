package com.adauto.marvel.feature.detail.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.adauto.marvel.core.model.ComicDetail
import com.adauto.marvel.core.model.UiText
import com.adauto.marvel.feature.detail.ComicDetailUiState

class ComicDetailUiStatePreviewParameterProvider : PreviewParameterProvider<ComicDetailUiState> {
    override val values: Sequence<ComicDetailUiState> = sequenceOf(
        // 1. Loading State
        ComicDetailUiState(
            isLoading = true
        ),
        // 2. Populated Success State (Full)
        ComicDetailUiState(
            comic = ComicDetail(
                id = 1001,
                title = "The Amazing Spider-Man #800: Go Down Swinging",
                description = "Dan Slott's historic run reaches its climactic conclusion! Norman Osborn has bonded with the Carnage symbiote to become the Red Goblin. Can Spider-Man survive his deadliest adversary's ultimate form?",
                issueNumber = "#800",
                pageCount = 80,
                printPrice = "$9.99",
                onsaleDate = "2018-05-30",
                coverUrl = "https://i.annihil.us/u/prod/marvel/i/mg/c/50/5b55018a1a361.jpg",
                creators = listOf("Dan Slott (Writer)", "Stuart Immonen (Penciler)", "Nick Bradshaw (Inker)")
            )
        ),
        // 3. Populated Minimal (No Description, No Creators)
        ComicDetailUiState(
            comic = ComicDetail(
                id = 1002,
                title = "Daredevil: Born Again #1",
                description = null,
                issueNumber = "#1",
                pageCount = 32,
                printPrice = "$3.99",
                onsaleDate = "1986-02-01",
                coverUrl = null,
                creators = emptyList()
            )
        ),
        // 4. Error State
        ComicDetailUiState(
            errorMessage = UiText.Dynamic("Não foi possível carregar os detalhes do quadrinho. Verifique sua conexão.")
        )
    )
}
