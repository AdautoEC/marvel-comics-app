package com.adauto.marvel.benchmark

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
 * 1. Cold app startup to comic list rendering.
 * 2. Flinging and scrolling through the paginated comic list.
 * 3. Navigating to comic detail and scrolling hero artwork & stats chips.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generateBaselineProfile() {
        baselineProfileRule.collect(
            packageName = "com.marvel.comics",
            includeInStartupProfile = true,
        ) {
            // Cold start journey
            pressHome()
            startActivityAndWait()

            // Wait until the comics grid is rendered and items are loaded
            device.wait(Until.hasObject(By.res("comic_card")), 5_000)

            // Scroll comics feed
            val comicsList = device.findObject(By.res("comics_grid"))
            comicsList?.let { list ->
                list.setGestureMargin(device.displayWidth / 5)
                list.fling(Direction.DOWN)
                device.waitForIdle()
                list.fling(Direction.UP)
                device.waitForIdle()
            }

            // Navigate to detail screen
            val firstCard = device.findObject(By.res("comic_card"))
            firstCard?.click()
            device.waitForIdle()

            // Scroll detail screen
            val detailScroll = device.findObject(By.res("detail_scroll"))
            detailScroll?.let { scroll ->
                scroll.fling(Direction.DOWN)
                device.waitForIdle()
            }

            pressBack()
            device.waitForIdle()
        }
    }
}
