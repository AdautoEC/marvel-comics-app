package com.adauto.marvel.feature.detail.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.adauto.marvel.core.model.ComicDetail
import com.adauto.marvel.core.model.MovieDetail
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
        ),
        // 5. Populated Movie (Active Actions: Favorito + Quero Assistir)
        ComicDetailUiState(
            movie = MovieDetail(
                id = 550,
                title = "Fight Club",
                overview = "An insomniac office worker and a devil-may-care soap maker form an underground fight club.",
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
        // 6. Populated Movie (Inactive Actions)
        ComicDetailUiState(
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
        )
    )
}
