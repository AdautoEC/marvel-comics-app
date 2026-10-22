package com.adauto.marvel.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

val DarkColorScheme = darkColorScheme(
    primary = MarvelColors.MarvelRed,
    background = MarvelColors.DarkBackground,
    surface = MarvelColors.SurfaceDark,
    surfaceVariant = MarvelColors.SurfaceCard,
    onPrimary = MarvelColors.TextPrimary,
    onBackground = MarvelColors.TextPrimary,
    onSurface = MarvelColors.TextPrimary,
    onSurfaceVariant = MarvelColors.TextSecondary
)

@Composable
fun MarvelTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalSpacing provides Spacing()
    ) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            typography = MarvelTypography,
            shapes = MarvelShapes,
            content = content
        )
    }
}

object MarvelTheme {
    val colors: MarvelColors
        get() = MarvelColors

    val spacing: Spacing
        @Composable get() = LocalSpacing.current

    val typography: Typography
        @Composable get() = MaterialTheme.typography

    val shapes: Shapes
        @Composable get() = MaterialTheme.shapes
}
