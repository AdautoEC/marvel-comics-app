@file:Suppress("ForbiddenImport")

package com.adauto.marvel.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.adauto.marvel.core.designsystem.theme.MarvelColors
import com.adauto.marvel.core.model.CoverImage
import com.adauto.marvel.core.model.ImageVariant

/**
 * Centralized image loading component using Coil's [AsyncImage].
 *
 * All image rendering in the application must go through this component
 * to enforce centralized ImageLoader usage, crossfade transitions,
 * aspect ratio preservation, and detekt forbidden-import governance.
 */
@Composable
fun ComicCover(
    coverUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    Box(
        modifier = modifier.background(MarvelColors.SurfaceDark),
        contentAlignment = Alignment.Center
    ) {
        if (!coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(coverUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Overload of [ComicCover] that takes a domain [CoverImage] and [ImageVariant].
 */
@Composable
fun ComicCover(
    coverImage: CoverImage?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    variant: ImageVariant = ImageVariant.PORTRAIT_UNCANNY,
    contentScale: ContentScale = ContentScale.Crop
) {
    ComicCover(
        coverUrl = coverImage?.buildUrl(variant),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
    )
}
