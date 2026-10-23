package com.adauto.marvel.core.ui

import com.adauto.marvel.core.designsystem.theme.MarvelColors
import com.adauto.marvel.core.model.UiText
import com.adauto.marvel.core.ui.component.DEFAULT_ATTRIBUTION_TEXT
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
        assertThat(MarvelColors.MarvelRed).isNotNull()
        assertThat(MarvelColors.SurfaceDark).isNotNull()
        assertThat(MarvelColors.SurfaceCard).isNotNull()
    }
}
