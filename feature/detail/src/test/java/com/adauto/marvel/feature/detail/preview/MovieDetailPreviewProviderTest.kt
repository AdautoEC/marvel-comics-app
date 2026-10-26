package com.adauto.marvel.feature.detail.preview

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MovieDetailPreviewProviderTest {

    @Test
    fun movieDetailUiStatePreviewParameterProvider_suppliesAllCoreUiStates() {
        val provider = MovieDetailUiStatePreviewParameterProvider()
        val values = provider.values.toList()

        assertThat(values).hasSize(5)

        // 1. Loading
        assertThat(values[0].isLoading).isTrue()
        assertThat(values[0].movie).isNull()

        // 2. Error
        assertThat(values[1].errorMessage).isNotNull()

        // 3. Populated Movie (Active Actions)
        assertThat(values[2].movie).isNotNull()
        assertThat(values[2].movie?.title).isEqualTo("Clube da Luta")
        assertThat(values[2].movie?.isFavorite).isTrue()
        assertThat(values[2].movie?.isInWatchlist).isTrue()

        // 4. Populated Movie (Inactive Actions)
        assertThat(values[3].movie).isNotNull()
        assertThat(values[3].movie?.title).isEqualTo("Inception")
        assertThat(values[3].movie?.isFavorite).isFalse()
        assertThat(values[3].movie?.isInWatchlist).isFalse()

        // 5. Populated Movie Minimal
        assertThat(values[4].movie).isNotNull()
        assertThat(values[4].movie?.genres).isEmpty()
    }
}
