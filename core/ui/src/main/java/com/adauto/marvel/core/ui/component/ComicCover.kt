@file:Suppress("ForbiddenImport")

package com.adauto.marvel.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.adauto.marvel.core.designsystem.preview.ThemePreviews
import com.adauto.marvel.core.designsystem.theme.MarvelColors
import com.adauto.marvel.core.designsystem.theme.MarvelTheme
import com.adauto.marvel.core.model.CoverImage
import com.adauto.marvel.core.model.ImageVariant

/**
 * Centralized image loading component using Coil's [AsyncImage].
 *
 * All image rendering in the application must go through this component
 * to enforce centralized ImageLoader usage, crossfade transitions,
 * aspect ratio preservation, and detekt forbidden-import governance.
 *
 * Handles design-time previews cleanly via [LocalInspectionMode].
 */
@Composable
fun ComicCover(
    coverUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val isInspectionMode = LocalInspectionMode.current

    Box(
        modifier = modifier.background(MarvelColors.SurfaceDark),
        contentAlignment = Alignment.Center
    ) {
        if (isInspectionMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MarvelColors.SurfaceCard),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "MARVEL COVER",
                    color = MarvelColors.TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else if (!coverUrl.isNullOrBlank()) {
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

@ThemePreviews
@Composable
private fun ComicCoverPreview() {
    MarvelTheme {
        Box(modifier = Modifier.size(150.dp, 225.dp)) {
            ComicCover(
                coverUrl = "https://example.com/mock.jpg",
                contentDescription = "Marvel Cover Preview"
            )
        }
    }
}
