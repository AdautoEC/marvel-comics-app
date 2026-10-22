package com.adauto.marvel.core.network.telemetry

import com.adauto.marvel.core.network.integrity.ClientIntegrityManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class FirebaseTelemetryInitializerTest {

    private val clientIntegrityManager: ClientIntegrityManager = mockk()
    private val crashlytics: FirebaseCrashlytics = mockk(relaxed = true)

    @Test
    fun initialize_setsCrashlyticsCustomKeysFromDeviceSignals() {
        val signals = mapOf(
            "is_debugger" to false,
            "is_emulator" to true,
            "is_rooted" to false
        )
        every { clientIntegrityManager.checkDeviceSignals() } returns signals

        val initializer = FirebaseTelemetryInitializer(clientIntegrityManager)
        initializer.initialize(crashlytics)

        verify(exactly = 1) { crashlytics.setCustomKey("is_debugger", false) }
        verify(exactly = 1) { crashlytics.setCustomKey("is_emulator", true) }
        verify(exactly = 1) { crashlytics.setCustomKey("is_rooted", false) }
    }
}
