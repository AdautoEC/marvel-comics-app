package com.adauto.cinemahub.core.designsystem.preview

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/**
 * Multi-preview annotation that renders composables in both Light and Dark themes.
 *
 * Adheres to Skydoves' Compose Preview guidelines by bundling theme configurations
 * into a single reusable annotation.
 */
@Preview(
    name = "Dark Mode",
    group = "Theme",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
    backgroundColor = 0xFF0F0F14
)
@Preview(
    name = "Light Mode",
    group = "Theme",
    uiMode = Configuration.UI_MODE_NIGHT_NO,
    showBackground = true,
    backgroundColor = 0xFFFFFFFF
)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class ThemePreviews
