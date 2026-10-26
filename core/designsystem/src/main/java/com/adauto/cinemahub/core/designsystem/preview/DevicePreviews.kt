package com.adauto.cinemahub.core.designsystem.preview

import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview

/**
 * Multi-preview annotation that renders composables across multiple screen form factors:
 * Phone Portrait, Phone Landscape, Foldable, and Tablet.
 *
 * Adheres to Skydoves' Compose Preview guidelines for layout responsiveness.
 */
@Preview(
    name = "Phone - Portrait",
    group = "Device",
    device = Devices.PIXEL_7,
    showSystemUi = true
)
@Preview(
    name = "Phone - Landscape",
    group = "Device",
    device = "spec:width=891dp,height=411dp",
    showSystemUi = true
)
@Preview(
    name = "Foldable",
    group = "Device",
    device = Devices.FOLDABLE,
    showSystemUi = true
)
@Preview(
    name = "Tablet",
    group = "Device",
    device = Devices.PIXEL_TABLET,
    showSystemUi = true
)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class DevicePreviews
