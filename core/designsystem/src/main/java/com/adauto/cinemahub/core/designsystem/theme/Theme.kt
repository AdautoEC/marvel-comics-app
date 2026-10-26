package com.adauto.cinemahub.core.designsystem.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val DarkColorScheme = darkColorScheme(
    primary = CinemaHubColors.CinemaHubRed,
    onPrimary = CinemaHubColors.TextPrimary,
    primaryContainer = CinemaHubColors.SurfaceCard,
    onPrimaryContainer = CinemaHubColors.TextPrimary,
    secondary = CinemaHubColors.CinemaHubRed,
    onSecondary = CinemaHubColors.TextPrimary,
    background = CinemaHubColors.DarkBackground,
    onBackground = CinemaHubColors.TextPrimary,
    surface = CinemaHubColors.SurfaceDark,
    onSurface = CinemaHubColors.TextPrimary,
    surfaceVariant = CinemaHubColors.SurfaceCard,
    onSurfaceVariant = CinemaHubColors.TextSecondary,
    outline = CinemaHubColors.TextTertiary,
    outlineVariant = CinemaHubColors.SurfaceCard,
    error = CinemaHubColors.CinemaHubRed,
    onError = CinemaHubColors.TextPrimary
)

val LightColorScheme = lightColorScheme(
    primary = CinemaHubColors.CinemaHubRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF1F3F5),
    onPrimaryContainer = Color(0xFF1B1B22),
    secondary = CinemaHubColors.CinemaHubRed,
    onSecondary = Color.White,
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF1B1B22),
    surface = Color.White,
    onSurface = Color(0xFF1B1B22),
    surfaceVariant = Color(0xFFE9ECEF),
    onSurfaceVariant = Color(0xFF495057),
    outline = Color(0xFFCED4DA),
    outlineVariant = Color(0xFFDEE2E6),
    error = CinemaHubColors.CinemaHubRed,
    onError = Color.White
)

@Composable
fun CinemaHubTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(
        LocalSpacing provides Spacing()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CinemaHubTypography,
            shapes = CinemaHubShapes,
            content = content
        )
    }
}

object CinemaHubTheme {
    val colorScheme: ColorScheme
        @Composable get() = MaterialTheme.colorScheme

    val spacing: Spacing
        @Composable get() = LocalSpacing.current

    val typography: Typography
        @Composable get() = MaterialTheme.typography

    val shapes: Shapes
        @Composable get() = MaterialTheme.shapes
}
