package com.adauto.cinemahub.feature.movies.component

import com.adauto.cinemahub.core.model.TmdbApiException
import com.adauto.cinemahub.core.model.UiText
import com.adauto.cinemahub.core.ui.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.IOException

class FormatPagingErrorTest {

    private val fallback = UiText.Resource(R.string.error_load_movies_fallback)

    @Test
    fun formatPagingError_whenTmdbApi401_returnsSessionExpiredResource() {
        val error = TmdbApiException(code = 401, message = "Invalid API key")
        val result = formatPagingError(error, fallback)

        assertThat(result).isEqualTo(UiText.Resource(R.string.error_session_expired))
    }

    @Test
    fun formatPagingError_whenTmdbApi429_returnsRateLimitedResource() {
        val error = TmdbApiException(code = 429, message = "Too many requests")
        val result = formatPagingError(error, fallback)

        assertThat(result).isEqualTo(UiText.Resource(R.string.error_rate_limited))
    }

    @Test
    fun formatPagingError_whenTmdbApi500_withMessage_returnsDynamicMessage() {
        val error = TmdbApiException(code = 500, message = "Internal TMDB server failure")
        val result = formatPagingError(error, fallback)

        assertThat(result).isEqualTo(UiText.Dynamic("Internal TMDB server failure"))
    }

    @Test
    fun formatPagingError_whenTmdbApi500_withBlankMessage_returnsFallback() {
        val error = TmdbApiException(code = 500, message = "")
        val result = formatPagingError(error, fallback)

        assertThat(result).isEqualTo(fallback)
    }

    @Test
    fun formatPagingError_whenIOException_withMessage_returnsDynamicMessage() {
        val error = IOException("Network connection timed out")
        val result = formatPagingError(error, fallback)

        assertThat(result).isEqualTo(UiText.Dynamic("Network connection timed out"))
    }

    @Test
    fun formatPagingError_whenException_withoutMessage_returnsFallback() {
        val error = RuntimeException()
        val result = formatPagingError(error, fallback)

        assertThat(result).isEqualTo(fallback)
    }
}
