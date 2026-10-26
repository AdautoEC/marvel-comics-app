package com.adauto.cinemahub.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

val DarkColorScheme = darkColorScheme(
    primary = CinemaHubColors.CinemaHubRed,
    background = CinemaHubColors.DarkBackground,
    surface = CinemaHubColors.SurfaceDark,
    surfaceVariant = CinemaHubColors.SurfaceCard,
    onPrimary = CinemaHubColors.TextPrimary,
    onBackground = CinemaHubColors.TextPrimary,
    onSurface = CinemaHubColors.TextPrimary,
    onSurfaceVariant = CinemaHubColors.TextSecondary
)

@Composable
fun CinemaHubTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalSpacing provides Spacing()
    ) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            typography = CinemaHubTypography,
            shapes = CinemaHubShapes,
            content = content
        )
    }
}

object CinemaHubTheme {
    val colors: CinemaHubColors
        get() = CinemaHubColors

    val spacing: Spacing
        @Composable get() = LocalSpacing.current

    val typography: Typography
        @Composable get() = MaterialTheme.typography

    val shapes: Shapes
        @Composable get() = MaterialTheme.shapes
}
