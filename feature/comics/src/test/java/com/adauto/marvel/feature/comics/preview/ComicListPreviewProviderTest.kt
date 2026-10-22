package com.adauto.marvel.feature.comics.preview

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ComicListPreviewProviderTest {

    @Test
    fun comicListUiStatePreviewParameterProvider_suppliesAllCoreUiStates() {
        val provider = ComicListUiStatePreviewParameterProvider()
        val values = provider.values.toList()

        assertThat(values).hasSize(5)

        // 1. Loading
        assertThat(values[0].isLoading).isTrue()

        // 2. Populated
        assertThat(values[1].comics).isNotEmpty()
        assertThat(values[1].isLoading).isFalse()

        // 3. Offline
        assertThat(values[2].uiState.isOffline).isTrue()

        // 4. Empty
        assertThat(values[3].comics).isEmpty()
        assertThat(values[3].isLoading).isFalse()

        // 5. Error
        assertThat(values[4].errorMessage).isNotNull()
    }
}
