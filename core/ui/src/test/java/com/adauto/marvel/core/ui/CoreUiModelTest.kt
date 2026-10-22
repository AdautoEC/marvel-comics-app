package com.adauto.marvel.core.ui

import com.adauto.marvel.core.designsystem.theme.MarvelColors
import com.adauto.marvel.core.model.ComicSummary
import com.adauto.marvel.core.model.CoverImage
import com.adauto.marvel.core.model.ImageVariant
import com.adauto.marvel.core.model.UiText
import com.adauto.marvel.core.ui.component.DEFAULT_ATTRIBUTION_TEXT
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CoreUiModelTest {

    @Test
    fun attributionText_containsMarvelCopyright() {
        assertThat(DEFAULT_ATTRIBUTION_TEXT).contains("Data provided by Marvel")
        assertThat(DEFAULT_ATTRIBUTION_TEXT).contains("© 2026 MARVEL")
    }

    @Test
    fun comicSummary_propertiesMatchExpectedValues() {
        val summary = ComicSummary(
            id = 42,
            title = "Avengers Forever",
            issueNumber = "#1",
            coverUrl = "https://example.com/cover.jpg"
        )
        assertThat(summary.id).isEqualTo(42)
        assertThat(summary.title).isEqualTo("Avengers Forever")
        assertThat(summary.issueNumber).isEqualTo("#1")
        assertThat(summary.coverUrl).isEqualTo("https://example.com/cover.jpg")
    }

    @Test
    fun coverImage_buildsUrlForVariantsCorrectly() {
        val cover = CoverImage(
            path = "http://i.annihil.us/u/prod/marvel/i/mg/c/e0/535fecbbb9784",
            extension = "jpg"
        )
        val url = cover.buildUrl(ImageVariant.PORTRAIT_UNCANNY)
        assertThat(url).startsWith("https://")
        assertThat(url).endsWith("/portrait_uncanny.jpg")
    }

    @Test
    fun uiText_dynamicProducesCorrectValue() {
        val text = UiText.Dynamic("Spider-Man")
        assertThat(text.value).isEqualTo("Spider-Man")
    }

    @Test
    fun marvelColors_haveExpectedValues() {
        assertThat(MarvelColors.MarvelRed).isNotNull()
        assertThat(MarvelColors.SurfaceDark).isNotNull()
        assertThat(MarvelColors.SurfaceCard).isNotNull()
    }
}
