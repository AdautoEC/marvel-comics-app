package com.adauto.cinemahub.core.network.integrity

import android.content.Context
import android.content.pm.ApplicationInfo
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostileEnvironmentDetectorTest {

    @Test
    fun detector_evaluatesEnvironmentFlags() {
        val detector = HostileEnvironmentDetector()
        val isDebuggable = detector.isDebuggable()
        assertNotNull(isDebuggable)
        assertFalse(isDebuggable)
    }

    @Test
    fun isDebuggerAttached_returnsTrueWhenDebuggerConnected() {
        val detector = HostileEnvironmentDetector(
            debuggerChecker = { true }
        )
        assertTrue(detector.isDebuggerAttached())
    }

    @Test
    fun isDebuggerAttached_returnsFalseWhenDebuggerNotConnected() {
        val detector = HostileEnvironmentDetector(
            debuggerChecker = { false }
        )
        assertFalse(detector.isDebuggerAttached())
    }

    @Test
    fun isEmulator_returnsTrueWhenFingerprintStartsWithGeneric() {
        val detector = HostileEnvironmentDetector(
            fingerprintSupplier = { "generic_x86_arm" },
            modelSupplier = { "Pixel" }
        )
        assertTrue(detector.isEmulator())
    }

    @Test
    fun isEmulator_returnsTrueWhenFingerprintStartsWithUnknown() {
        val detector = HostileEnvironmentDetector(
            fingerprintSupplier = { "unknown" },
            modelSupplier = { "Pixel" }
        )
        assertTrue(detector.isEmulator())
    }

    @Test
    fun isEmulator_returnsTrueWhenModelContainsGoogleSdk() {
        val detector = HostileEnvironmentDetector(
            fingerprintSupplier = { "google/shiba" },
            modelSupplier = { "google_sdk phone" }
        )
        assertTrue(detector.isEmulator())
    }

    @Test
    fun isEmulator_returnsTrueWhenModelContainsEmulator() {
        val detector = HostileEnvironmentDetector(
            fingerprintSupplier = { "google/shiba" },
            modelSupplier = { "Android Emulator" }
        )
        assertTrue(detector.isEmulator())
    }

    @Test
    fun isEmulator_returnsTrueWhenModelContainsAndroidSdkBuiltForX86() {
        val detector = HostileEnvironmentDetector(
            fingerprintSupplier = { "google/shiba" },
            modelSupplier = { "Android SDK built for x86" }
        )
        assertTrue(detector.isEmulator())
    }

    @Test
    fun isEmulator_returnsFalseForPhysicalDevice() {
        val detector = HostileEnvironmentDetector(
            fingerprintSupplier = { "google/shiba/shiba:14/AP2A.240805.005/12025091:user/release-keys" },
            modelSupplier = { "Pixel 8" }
        )
        assertFalse(detector.isEmulator())
    }

    @Test
    fun isRooted_returnsTrueWhenSuBinaryFound() {
        val detector = HostileEnvironmentDetector(
            rootPathChecker = { path -> path == "/system/xbin/su" }
        )
        assertTrue(detector.isRooted())
    }

    @Test
    fun isRooted_returnsFalseWhenNoSuBinaryFound() {
        val detector = HostileEnvironmentDetector(
            rootPathChecker = { false }
        )
        assertFalse(detector.isRooted())
    }

    @Test
    fun isDebuggable_withContext_returnsTrueWhenFlagIsSet() {
        val context = mockk<Context>()
        val appInfo = ApplicationInfo().apply {
            flags = ApplicationInfo.FLAG_DEBUGGABLE
        }
        every { context.applicationInfo } returns appInfo

        val detector = HostileEnvironmentDetector()
        assertTrue(detector.isDebuggable(context))
    }

    @Test
    fun isDebuggable_withContext_returnsFalseWhenFlagIsNotSet() {
        val context = mockk<Context>()
        val appInfo = ApplicationInfo().apply {
            flags = 0
        }
        every { context.applicationInfo } returns appInfo

        val detector = HostileEnvironmentDetector()
        assertFalse(detector.isDebuggable(context))
    }
}
