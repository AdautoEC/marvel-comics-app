package com.adauto.marvel.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class ComicDetail(
    val id: Int,
    val title: String,
    val description: String? = null,
    val issueNumber: String? = null,
    val pageCount: Int = 0,
    val printPrice: String? = null,
    val onsaleDate: String? = null,
    val coverUrl: String? = null,
    val creators: List<String> = emptyList()
)
