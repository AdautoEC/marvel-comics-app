package com.adauto.marvel.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.adauto.marvel.core.designsystem.theme.DarkColorScheme
import com.adauto.marvel.core.designsystem.theme.MarvelColors
import com.adauto.marvel.core.designsystem.theme.MarvelShapes
import com.adauto.marvel.core.designsystem.theme.MarvelTheme
import com.adauto.marvel.core.designsystem.theme.MarvelTypography
import com.adauto.marvel.core.designsystem.theme.Spacing
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTokensTest {

    @Test
    fun `primary red should match Marvel red hex code`() {
        assertEquals(Color(0xFFED1D24), MarvelColors.MarvelRed)
    }

    @Test
    fun `dark theme palette matches Figma token specifications`() {
        assertThat(MarvelColors.DarkBackground).isEqualTo(Color(0xFF0F0F14))
        assertThat(MarvelColors.SurfaceDark).isEqualTo(Color(0xFF1B1B22))
        assertThat(MarvelColors.SurfaceCard).isEqualTo(Color(0xFF22222B))
        assertThat(MarvelColors.TextPrimary).isEqualTo(Color(0xFFFFFFFF))
        assertThat(MarvelColors.TextSecondary).isEqualTo(Color(0xFFB7B7C8))
        assertThat(MarvelColors.TextTertiary).isEqualTo(Color(0xFF727288))
        assertThat(MarvelColors.HeroGradientStart).isEqualTo(Color(0xFF005BEA))
        assertThat(MarvelColors.HeroGradientEnd).isEqualTo(Color(0xFF00C6FB))
        assertThat(MarvelColors.VillainGradientStart).isEqualTo(Color(0xFFED1D24))
        assertThat(MarvelColors.VillainGradientEnd).isEqualTo(Color(0xFF380036))
    }

    @Test
    fun `darkColorScheme maps MarvelColors correctly`() {
        assertThat(DarkColorScheme.primary).isEqualTo(MarvelColors.MarvelRed)
        assertThat(DarkColorScheme.background).isEqualTo(MarvelColors.DarkBackground)
        assertThat(DarkColorScheme.surface).isEqualTo(MarvelColors.SurfaceDark)
        assertThat(DarkColorScheme.surfaceVariant).isEqualTo(MarvelColors.SurfaceCard)
        assertThat(DarkColorScheme.onPrimary).isEqualTo(MarvelColors.TextPrimary)
        assertThat(DarkColorScheme.onBackground).isEqualTo(MarvelColors.TextPrimary)
        assertThat(DarkColorScheme.onSurface).isEqualTo(MarvelColors.TextPrimary)
        assertThat(MarvelTheme.colors).isEqualTo(MarvelColors)
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
        assertThat(MarvelTypography.titleLarge).isNotNull()
        assertThat(MarvelTypography.bodyLarge).isNotNull()
        assertThat(MarvelShapes.small).isNotNull()
        assertThat(MarvelShapes.medium).isNotNull()
        assertThat(MarvelShapes.large).isNotNull()
    }
}
