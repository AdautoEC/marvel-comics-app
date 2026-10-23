package com.adauto.marvel.feature.detail.preview

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ComicDetailPreviewProviderTest {

    @Test
    fun comicDetailUiStatePreviewParameterProvider_suppliesAllCoreUiStates() {
        val provider = ComicDetailUiStatePreviewParameterProvider()
        val values = provider.values.toList()

        assertThat(values).hasSize(6)

        // 1. Loading
        assertThat(values[0].isLoading).isTrue()
        assertThat(values[0].comic).isNull()

        // 2. Populated Full
        assertThat(values[1].comic).isNotNull()
        assertThat(values[1].comic?.title).contains("Spider-Man")
        assertThat(values[1].comic?.creators).isNotEmpty()

        // 3. Populated Minimal
        assertThat(values[2].comic).isNotNull()
        assertThat(values[2].comic?.description).isNull()
        assertThat(values[2].comic?.creators).isEmpty()

        // 4. Error
        assertThat(values[3].errorMessage).isNotNull()

        // 5. Populated Movie (Active Actions)
        assertThat(values[4].movie).isNotNull()
        assertThat(values[4].movie?.isFavorite).isTrue()
        assertThat(values[4].movie?.isWatched).isTrue()

        // 6. Populated Movie (Inactive Actions)
        assertThat(values[5].movie).isNotNull()
        assertThat(values[5].movie?.isFavorite).isFalse()
        assertThat(values[5].movie?.isWatched).isFalse()
    }
}
