package com.adauto.marvel.core.ui.preview

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UiPreviewProvidersTest {

    @Test
    fun comicCardPreviewParameterProvider_returnsAllExpectedVariants() {
        val provider = ComicCardPreviewParameterProvider()
        val values = provider.values.toList()

        assertThat(values).hasSize(3)
        assertThat(values[0].title).contains("Spider-Man")
        assertThat(values[1].title).contains("Extra Long")
        assertThat(values[2].coverUrl).isNull()
    }

    @Test
    fun errorStatePreviewParameterProvider_returnsAllExpectedVariants() {
        val provider = ErrorStatePreviewParameterProvider()
        val values = provider.values.toList()

        assertThat(values).hasSize(3)
    }
}
