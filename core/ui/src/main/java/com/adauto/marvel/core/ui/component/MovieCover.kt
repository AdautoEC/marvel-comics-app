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

/**
 * Centralized movie poster / backdrop artwork component using Coil's [AsyncImage].
 *
 * All image rendering in Cinema Hub goes through this component to enforce
 * crossfade transitions, aspect ratio preservation, and design-time preview support.
 */
@Composable
fun MovieCover(
    imageUrl: String?,
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
                    text = "POSTER PREVIEW",
                    color = MarvelColors.TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@ThemePreviews
@Composable
private fun MovieCoverPreview() {
    MarvelTheme {
        Box(modifier = Modifier.size(150.dp, 225.dp)) {
            MovieCover(
                imageUrl = "https://example.com/mock.jpg",
                contentDescription = "Movie Cover Preview"
            )
        }
    }
}
