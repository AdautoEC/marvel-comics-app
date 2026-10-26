package com.adauto.cinemahub.core.ui.util

import androidx.annotation.StringRes
import com.adauto.cinemahub.core.model.MovieCategory
import com.adauto.cinemahub.core.ui.R

@get:StringRes
val MovieCategory.titleRes: Int
    get() = when (this) {
        MovieCategory.POPULAR -> R.string.category_popular
        MovieCategory.TOP_RATED -> R.string.category_top_rated
        MovieCategory.FAVORITES -> R.string.category_favorites
        MovieCategory.WATCHLIST -> R.string.category_watchlist
    }
