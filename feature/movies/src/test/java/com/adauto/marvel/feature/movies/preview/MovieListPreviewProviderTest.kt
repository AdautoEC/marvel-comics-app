package com.adauto.marvel.feature.movies.preview

import com.adauto.marvel.core.model.MovieCategory
import com.adauto.marvel.core.model.SessionState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MovieListPreviewProviderTest {

    @Test
    fun movieListUiStatePreviewParameterProvider_suppliesAllCoreUiStates() {
        val provider = MovieListUiStatePreviewParameterProvider()
        val values = provider.values.toList()

        assertThat(values).hasSize(6)

        // 1. Loading
        assertThat(values[0].isLoading).isTrue()

        // 2. Populated (Authenticated)
        assertThat(values[1].movies).isNotEmpty()
        assertThat(values[1].isLoading).isFalse()
        assertThat(values[1].uiState.sessionState).isInstanceOf(SessionState.Authenticated::class.java)

        // 3. Offline
        assertThat(values[2].uiState.isOffline).isTrue()

        // 4. Empty
        assertThat(values[3].movies).isEmpty()
        assertThat(values[3].isLoading).isFalse()

        // 5. Error
        assertThat(values[4].errorMessage).isNotNull()

        // 6. Watchlist Tab
        assertThat(values[5].uiState.selectedCategory).isEqualTo(MovieCategory.WATCHLIST)
        assertThat(values[5].uiState.sessionState).isEqualTo(SessionState.Guest)
    }
}
