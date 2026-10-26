package com.adauto.cinemahub.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Macrobenchmark measuring frame timing metrics and jank during movie feed scrolling.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollFeedCompilationBaselineProfile() = measureScroll(CompilationMode.Partial())

    private fun measureScroll(compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = compilationMode,
            iterations = ITERATIONS,
            startupMode = StartupMode.WARM
        ) {
            startActivityAndWait()

            val cardPattern = java.util.regex.Pattern.compile(".*MovieCard_.*")
            device.wait(Until.hasObject(By.res(cardPattern)), RENDER_TIMEOUT_MS)
                ?: device.wait(Until.hasObject(By.descContains("Ver detalhes")), RENDER_TIMEOUT_MS)

            val scrollable = device.findObject(By.scrollable(true))
            scrollable?.let { list ->
                list.setGestureMargin(device.displayWidth / GESTURE_MARGIN_DIVISOR)
                list.fling(Direction.DOWN)
                device.waitForIdle()
                list.fling(Direction.UP)
                device.waitForIdle()
            }
        }
    }

    companion object {
        private const val PACKAGE_NAME = "com.adauto.cinemahub"
        private const val ITERATIONS = 5
        private const val RENDER_TIMEOUT_MS = 5_000L
        private const val GESTURE_MARGIN_DIVISOR = 5
    }
}
