package com.adauto.marvel.core.ui.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.adauto.marvel.core.model.ComicSummary

class ComicCardPreviewParameterProvider : PreviewParameterProvider<ComicSummary> {
    override val values: Sequence<ComicSummary> = sequenceOf(
        ComicSummary(
            id = 1001,
            title = "The Amazing Spider-Man #800",
            issueNumber = "#800",
            coverUrl = "https://i.annihil.us/u/prod/marvel/i/mg/c/50/5b55018a1a361.jpg"
        ),
        ComicSummary(
            id = 1002,
            title = "Avengers: Infinity Gauntlet - The Complete Epic Collection Volume 1 With Extra Long Title",
            issueNumber = "#1",
            coverUrl = "https://i.annihil.us/u/prod/marvel/i/mg/c/50/5b55018a1a362.jpg"
        ),
        ComicSummary(
            id = 1003,
            title = "Daredevil: Born Again #1",
            issueNumber = "#1",
            coverUrl = null
        )
    )
}
