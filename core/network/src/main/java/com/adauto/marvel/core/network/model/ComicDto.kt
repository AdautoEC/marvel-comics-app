package com.adauto.marvel.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class ComicDto(
    val id: Int,
    val title: String = "",
    val issueNumber: Double = 0.0,
    val description: String? = null,
    val pageCount: Int = 0,
    val thumbnail: ThumbnailDto? = null,
    val dates: List<ComicDateDto> = emptyList(),
    val prices: List<ComicPriceDto> = emptyList(),
    val creators: ComicResourceListDto = ComicResourceListDto(),
    val characters: ComicResourceListDto = ComicResourceListDto()
)

@Serializable
data class ThumbnailDto(
    val path: String = "",
    val extension: String = ""
)

@Serializable
data class ComicDateDto(
    val type: String = "",
    val date: String = ""
)

@Serializable
data class ComicPriceDto(
    val type: String = "",
    val price: Float = 0.0f
)

@Serializable
data class ComicResourceListDto(
    val available: Int = 0,
    val items: List<ComicItemSummaryDto> = emptyList()
)

@Serializable
data class ComicItemSummaryDto(
    val name: String = "",
    val role: String? = null
)
