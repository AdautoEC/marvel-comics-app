package com.adauto.marvel.core.network.integrity

import android.content.Context
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClientIntegrityManager @Inject constructor(
    private val detector: HostileEnvironmentDetector
) {
    fun initializeAppCheck(context: Context, isDebug: Boolean = false) {
        val firebaseAppCheck = FirebaseAppCheck.getInstance()
        val factory = if (isDebug) {
            DebugAppCheckProviderFactory.getInstance()
        } else {
            PlayIntegrityAppCheckProviderFactory.getInstance()
        }
        firebaseAppCheck.installAppCheckProviderFactory(factory)
    }

    fun checkDeviceSignals(): Map<String, Boolean> {
        return mapOf(
            "is_debugger" to detector.isDebuggerAttached(),
            "is_emulator" to detector.isEmulator(),
            "is_rooted" to detector.isRooted()
        )
    }
}
