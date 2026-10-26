package com.adauto.cinemahub.core.designsystem.preview

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PreviewAnnotationsTest {

    @Test
    fun themePreviews_isAnnotatedProperly() {
        val annotations = ThemePreviews::class.java.annotations
        assertThat(annotations).isNotEmpty()
    }

    @Test
    fun devicePreviews_isAnnotatedProperly() {
        val annotations = DevicePreviews::class.java.annotations
        assertThat(annotations).isNotEmpty()
    }
}
