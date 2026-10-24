package com.adauto.marvel.core.ui.util

import com.adauto.marvel.core.model.MovieCategory
import com.adauto.marvel.core.ui.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MovieCategoryExtensionsTest {

    @Test
    fun titleRes_returnsNonZeroResourceId_forAllCategories() {
        MovieCategory.entries.forEach { category ->
            assertThat(category.titleRes).isNotEqualTo(0)
        }
    }

    @Test
    fun titleRes_mapsExpectedResourceIds() {
        assertThat(MovieCategory.POPULAR.titleRes).isEqualTo(R.string.category_popular)
        assertThat(MovieCategory.TOP_RATED.titleRes).isEqualTo(R.string.category_top_rated)
        assertThat(MovieCategory.FAVORITES.titleRes).isEqualTo(R.string.category_favorites)
        assertThat(MovieCategory.WATCHLIST.titleRes).isEqualTo(R.string.category_watchlist)
    }
}
