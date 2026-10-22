package com.adauto.marvel.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class ComicSummary(
    val id: Int,
    val title: String,
    val issueNumber: String,
    val coverUrl: String?
)
