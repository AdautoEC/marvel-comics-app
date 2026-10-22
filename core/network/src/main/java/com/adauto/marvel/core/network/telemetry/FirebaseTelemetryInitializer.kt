package com.adauto.marvel.core.network.telemetry

import com.adauto.marvel.core.network.integrity.ClientIntegrityManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseTelemetryInitializer @Inject constructor(
    private val clientIntegrityManager: ClientIntegrityManager
) {
    fun initialize(crashlytics: FirebaseCrashlytics = FirebaseCrashlytics.getInstance()) {
        val signals = clientIntegrityManager.checkDeviceSignals()
        signals.forEach { (key, value) ->
            crashlytics.setCustomKey(key, value)
        }
    }
}
