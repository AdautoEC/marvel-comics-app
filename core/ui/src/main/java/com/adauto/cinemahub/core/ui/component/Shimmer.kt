package com.adauto.cinemahub.core.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.adauto.cinemahub.core.designsystem.preview.ThemePreviews
import com.adauto.cinemahub.core.designsystem.theme.CinemaHubColors
import com.adauto.cinemahub.core.designsystem.theme.CinemaHubTheme

/**
 * Creates an animated linear gradient brush for shimmer effects.
 * Uses theme tokens ([CinemaHubColors.SurfaceDark] and [CinemaHubColors.SurfaceCard]),
 * avoiding raw or hardcoded light gray colors.
 */
@Composable
fun rememberShimmerBrush(
    colors: List<Color> = listOf(
        CinemaHubColors.SurfaceDark,
        CinemaHubColors.SurfaceCard,
        CinemaHubColors.SurfaceDark
    ),
    durationMillis: Int = 1200
): Brush {
    val transition = rememberInfiniteTransition(label = "ShimmerTransition")
    val translateAnimation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShimmerTranslateAnimation"
    )

    return Brush.linearGradient(
        colors = colors,
        start = Offset(x = translateAnimation - 300f, y = translateAnimation - 300f),
        end = Offset(x = translateAnimation, y = translateAnimation)
    )
}

/**
 * Applies an animated shimmer brush background to the modifier.
 */
@Composable
fun Modifier.shimmer(
    colors: List<Color> = listOf(
        CinemaHubColors.SurfaceDark,
        CinemaHubColors.SurfaceCard,
        CinemaHubColors.SurfaceDark
    ),
    shape: Shape = RoundedCornerShape(0.dp)
): Modifier {
    val brush = rememberShimmerBrush(colors = colors)
    return this.clip(shape).background(brush)
}

/**
 * Placeholder skeleton card matching the 2:3 aspect ratio and rounded corners of [MovieCard].
 */
@Composable
fun ShimmerMovieCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp)
) {
    val brush = rememberShimmerBrush()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .clip(shape)
            .background(brush)
    )
}

@ThemePreviews
@Composable
private fun ShimmerMovieCardPreview() {
    CinemaHubTheme {
        Box(modifier = Modifier.padding(16.dp).width(160.dp)) {
            ShimmerMovieCard()
        }
    }
}
