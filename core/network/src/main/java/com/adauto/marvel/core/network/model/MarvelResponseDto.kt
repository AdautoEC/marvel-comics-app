package com.adauto.marvel.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class MarvelResponseDto<T>(
    val code: Int = 0,
    val status: String = "",
    val attributionText: String = "",
    val data: MarvelDataContainer<T> = MarvelDataContainer()
)

@Serializable
data class MarvelDataContainer<T>(
    val offset: Int = 0,
    val limit: Int = 0,
    val total: Int = 0,
    val count: Int = 0,
    val results: List<T> = emptyList()
)
