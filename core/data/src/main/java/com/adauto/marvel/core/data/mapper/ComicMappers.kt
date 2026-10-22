package com.adauto.marvel.core.data.mapper

import com.adauto.marvel.core.database.entity.ComicEntity
import com.adauto.marvel.core.model.ComicDetail
import com.adauto.marvel.core.model.ComicSummary
import com.adauto.marvel.core.model.CoverImage
import com.adauto.marvel.core.model.ImageVariant
import com.adauto.marvel.core.model.stripHtml
import com.adauto.marvel.core.network.model.ComicDto
import java.util.Locale

fun formatIssueNumber(issueNumber: Double): String {
    if (issueNumber <= 0.0) return ""
    return if (issueNumber % 1.0 == 0.0) {
        "#${issueNumber.toLong()}"
    } else {
        "#$issueNumber"
    }
}

fun ComicEntity.toSummary(): ComicSummary {
    return ComicSummary(
        id = id,
        title = title,
        issueNumber = formatIssueNumber(issueNumber),
        coverUrl = CoverImage.fromRaw(thumbnailPath, thumbnailExt)?.buildUrl(ImageVariant.PORTRAIT_UNCANNY)
    )
}

fun ComicEntity.toDetail(): ComicDetail {
    val formattedPrice = priceCents?.takeIf { it > 0 }?.let {
        String.format(Locale.US, "$%.2f", it / 100.0)
    }
    return ComicDetail(
        id = id,
        title = title,
        description = stripHtml(description).takeIf { it.isNotBlank() },
        issueNumber = formatIssueNumber(issueNumber).takeIf { it.isNotBlank() },
        pageCount = pageCount,
        printPrice = formattedPrice,
        onsaleDate = onsaleDate,
        coverUrl = CoverImage.fromRaw(thumbnailPath, thumbnailExt)?.buildUrl(ImageVariant.PORTRAIT_UNCANNY),
        creators = creatorsJson?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    )
}

fun ComicDto.toDetail(): ComicDetail {
    val printPriceDto = prices.firstOrNull { it.type == "printPrice" } ?: prices.firstOrNull()
    val formattedPrice = printPriceDto?.price?.takeIf { it > 0f }?.let {
        String.format(Locale.US, "$%.2f", it)
    }
    val onsale = dates.firstOrNull { it.type == "onsaleDate" }?.date
    return ComicDetail(
        id = id,
        title = title,
        description = stripHtml(description).takeIf { it.isNotBlank() },
        issueNumber = formatIssueNumber(issueNumber).takeIf { it.isNotBlank() },
        pageCount = pageCount,
        printPrice = formattedPrice,
        onsaleDate = onsale,
        coverUrl = CoverImage.fromRaw(thumbnail?.path, thumbnail?.extension)?.buildUrl(ImageVariant.PORTRAIT_UNCANNY),
        creators = creators.items.map { it.name }.filter { it.isNotBlank() }
    )
}

fun ComicDto.toEntity(position: Int, fetchedAt: Long): ComicEntity {
    val priceDto = prices.firstOrNull { it.type == "printPrice" } ?: prices.firstOrNull()
    return ComicEntity(
        id = id,
        position = position,
        title = title,
        issueNumber = issueNumber,
        description = description,
        thumbnailPath = thumbnail?.path,
        thumbnailExt = thumbnail?.extension,
        pageCount = pageCount,
        priceCents = priceDto?.let { kotlin.math.round(it.price * 100).toInt() },
        onsaleDate = dates.firstOrNull { it.type == "onsaleDate" }?.date,
        creatorsJson = creators.items.joinToString(", ") { it.name }.takeIf { it.isNotBlank() },
        charactersJson = characters.items.joinToString(", ") { it.name }.takeIf { it.isNotBlank() },
        fetchedAt = fetchedAt
    )
}
