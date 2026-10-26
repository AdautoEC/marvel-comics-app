package com.adauto.cinemahub.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.adauto.cinemahub.core.designsystem.theme.DarkColorScheme
import com.adauto.cinemahub.core.designsystem.theme.CinemaHubColors
import com.adauto.cinemahub.core.designsystem.theme.CinemaHubShapes
import com.adauto.cinemahub.core.designsystem.theme.CinemaHubTheme
import com.adauto.cinemahub.core.designsystem.theme.CinemaHubTypography
import com.adauto.cinemahub.core.designsystem.theme.Spacing
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTokensTest {

    @Test
    fun `primary red should match Marvel red hex code`() {
        assertEquals(Color(0xFFED1D24), CinemaHubColors.CinemaHubRed)
    }

    @Test
    fun `dark theme palette matches Figma token specifications`() {
        assertThat(CinemaHubColors.DarkBackground).isEqualTo(Color(0xFF0F0F14))
        assertThat(CinemaHubColors.SurfaceDark).isEqualTo(Color(0xFF1B1B22))
        assertThat(CinemaHubColors.SurfaceCard).isEqualTo(Color(0xFF22222B))
        assertThat(CinemaHubColors.TextPrimary).isEqualTo(Color(0xFFFFFFFF))
        assertThat(CinemaHubColors.TextSecondary).isEqualTo(Color(0xFFB7B7C8))
        assertThat(CinemaHubColors.TextTertiary).isEqualTo(Color(0xFF727288))
        assertThat(CinemaHubColors.HeroGradientStart).isEqualTo(Color(0xFF005BEA))
        assertThat(CinemaHubColors.HeroGradientEnd).isEqualTo(Color(0xFF00C6FB))
        assertThat(CinemaHubColors.VillainGradientStart).isEqualTo(Color(0xFFED1D24))
        assertThat(CinemaHubColors.VillainGradientEnd).isEqualTo(Color(0xFF380036))
    }

    @Test
    fun `darkColorScheme maps CinemaHubColors correctly`() {
        assertThat(DarkColorScheme.primary).isEqualTo(CinemaHubColors.CinemaHubRed)
        assertThat(DarkColorScheme.background).isEqualTo(CinemaHubColors.DarkBackground)
        assertThat(DarkColorScheme.surface).isEqualTo(CinemaHubColors.SurfaceDark)
        assertThat(DarkColorScheme.surfaceVariant).isEqualTo(CinemaHubColors.SurfaceCard)
        assertThat(DarkColorScheme.onPrimary).isEqualTo(CinemaHubColors.TextPrimary)
        assertThat(DarkColorScheme.onBackground).isEqualTo(CinemaHubColors.TextPrimary)
        assertThat(DarkColorScheme.onSurface).isEqualTo(CinemaHubColors.TextPrimary)
        assertThat(CinemaHubTheme.colors).isEqualTo(CinemaHubColors)
    }

    @Test
    fun `spacing scale matches 4dp grid requirements`() {
        val spacing = Spacing()
        assertThat(spacing.extraSmall).isEqualTo(4.dp)
        assertThat(spacing.small).isEqualTo(8.dp)
        assertThat(spacing.medium).isEqualTo(16.dp)
        assertThat(spacing.large).isEqualTo(24.dp)
        assertThat(spacing.extraLarge).isEqualTo(32.dp)
    }

    @Test
    fun `typography and shapes are defined and valid`() {
        assertThat(CinemaHubTypography.titleLarge).isNotNull()
        assertThat(CinemaHubTypography.bodyLarge).isNotNull()
        assertThat(CinemaHubShapes.small).isNotNull()
        assertThat(CinemaHubShapes.medium).isNotNull()
        assertThat(CinemaHubShapes.large).isNotNull()
    }
}
