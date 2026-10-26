package com.adauto.cinemahub.core.ui.preview

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UiPreviewProvidersTest {

    @Test
    fun errorStatePreviewParameterProvider_returnsAllExpectedVariants() {
        val provider = ErrorStatePreviewParameterProvider()
        val values = provider.values.toList()

        assertThat(values).hasSize(3)
    }
}
