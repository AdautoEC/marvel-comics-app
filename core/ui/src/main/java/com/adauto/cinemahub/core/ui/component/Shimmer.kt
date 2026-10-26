package com.adauto.cinemahub.core.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.adauto.cinemahub.core.designsystem.preview.ThemePreviews
import com.adauto.cinemahub.core.designsystem.theme.CinemaHubTheme

/**
 * Creates an animated shimmer progress state.
 * Reading this state inside draw phase (e.g. [drawWithCache] / onDrawBehind) prevents
 * recomposition churn on every animation frame.
 */
@Composable
fun rememberShimmerProgress(durationMillis: Int = 1200): State<Float> {
    val transition = rememberInfiniteTransition(label = "ShimmerTransition")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShimmerTranslateAnimation"
    )
}

/**
 * Applies an animated shimmer brush background reading animation progress in draw phase.
 */
fun Modifier.shimmer(
    progress: State<Float>,
    colors: List<Color>,
    shape: Shape = RoundedCornerShape(0.dp)
): Modifier = this
    .clip(shape)
    .drawWithCache {
        onDrawBehind {
            val offset = progress.value
            val brush = Brush.linearGradient(
                colors = colors,
                start = Offset(x = offset - 300f, y = offset - 300f),
                end = Offset(x = offset, y = offset)
            )
            drawRect(brush = brush)
        }
    }

/**
 * Applies an animated shimmer brush using theme colorScheme and an optional progress state.
 */
@Composable
fun Modifier.shimmer(
    progress: State<Float>,
    shape: Shape = RoundedCornerShape(0.dp)
): Modifier = this.shimmer(
    progress = progress,
    colors = listOf(
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.surface
    ),
    shape = shape
)

/**
 * Applies an animated shimmer brush using theme colorScheme and a self-contained progress state.
 */
@Composable
fun Modifier.shimmer(
    shape: Shape = RoundedCornerShape(0.dp)
): Modifier = this.shimmer(
    progress = rememberShimmerProgress(),
    shape = shape
)

/**
 * Placeholder skeleton card matching the 2:3 aspect ratio and rounded corners of [MovieCard].
 * Accepts an optional shared [progress] state to synchronize all cards without recompositions.
 */
@Composable
fun ShimmerMovieCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    progress: State<Float> = rememberShimmerProgress()
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .shimmer(shape = shape, progress = progress)
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
