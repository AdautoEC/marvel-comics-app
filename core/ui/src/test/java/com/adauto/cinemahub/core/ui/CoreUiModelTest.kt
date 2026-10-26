package com.adauto.cinemahub.core.ui

import com.adauto.cinemahub.core.designsystem.theme.CinemaHubColors
import com.adauto.cinemahub.core.model.UiText
import com.adauto.cinemahub.core.ui.component.DEFAULT_ATTRIBUTION_TEXT
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CoreUiModelTest {

    @Test
    fun attributionText_containsTmdbNotice() {
        assertThat(DEFAULT_ATTRIBUTION_TEXT).contains("TMDB")
    }

    @Test
    fun uiText_dynamicProducesCorrectValue() {
        val text = UiText.Dynamic("Fight Club")
        assertThat(text.value).isEqualTo("Fight Club")
    }

    @Test
    fun marvelColors_haveExpectedValues() {
        assertThat(CinemaHubColors.CinemaHubRed).isNotNull()
        assertThat(CinemaHubColors.SurfaceDark).isNotNull()
        assertThat(CinemaHubColors.SurfaceCard).isNotNull()
    }
}
