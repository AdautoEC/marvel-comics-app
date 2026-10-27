package com.adauto.cinemahub.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Macrobenchmark measuring application startup latency with and without Baseline Profiles.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupNoCompilation() = measureStartup(CompilationMode.None())

    @Test
    fun startupBaselineProfile() = measureStartup(CompilationMode.Partial())

    @Test
    fun startupWarmBaselineProfile() = measureStartup(
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM
    )

    private fun measureStartup(
        compilationMode: CompilationMode,
        startupMode: StartupMode = StartupMode.COLD
    ) {
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            iterations = ITERATIONS,
            startupMode = startupMode
        ) {
            pressHome()
            startActivityAndWait()
        }
    }

    companion object {
        private const val PACKAGE_NAME = "com.adauto.cinemahub"
        private const val ITERATIONS = 5
    }
}
