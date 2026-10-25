package com.adauto.cinemahub.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline profile generator for optimizing startup time and scroll jank.
 *
 * Captures critical user journeys (CUJs):
 * 1. Cold app startup to movie list rendering.
 * 2. Flinging and scrolling through the paginated movie list.
 * 3. Navigating to movie detail and scrolling hero artwork & stats chips.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generateBaselineProfile() {
        baselineProfileRule.collect(
            packageName = "com.adauto.cinemahub",
            includeInStartupProfile = true,
        ) {
            // Cold start journey
            pressHome()
            startActivityAndWait()

            // Wait until movie items are rendered
            device.wait(Until.hasObject(By.descContains("Ver detalhes")), RENDER_TIMEOUT_MS)

            // Scroll movies feed
            val moviesList = device.findObject(By.scrollable(true))
            moviesList?.let { list ->
                list.setGestureMargin(device.displayWidth / GESTURE_MARGIN_DIVISOR)
                list.fling(Direction.DOWN)
                device.waitForIdle()
                list.fling(Direction.UP)
                device.waitForIdle()
            }

            // Navigate to detail screen
            val firstCard = device.findObject(By.descContains("Ver detalhes"))
            firstCard?.click()
            device.waitForIdle()

            // Scroll detail screen
            val detailScroll = device.findObject(By.scrollable(true))
            detailScroll?.let { scroll ->
                scroll.fling(Direction.DOWN)
                device.waitForIdle()
            }

            device.pressBack()
            device.waitForIdle()
        }
    }

    companion object {
        private const val RENDER_TIMEOUT_MS = 5_000L
        private const val GESTURE_MARGIN_DIVISOR = 5
    }
}
