package com.adauto.cinemahub.core.network.integrity

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Debug
import java.io.File
import javax.inject.Inject

class HostileEnvironmentDetector(
    private val debuggerChecker: () -> Boolean = { Debug.isDebuggerConnected() },
    private val rootPathChecker: (String) -> Boolean = { File(it).exists() },
    private val fingerprintSupplier: () -> String = { Build.FINGERPRINT ?: "" },
    private val modelSupplier: () -> String = { Build.MODEL ?: "" }
) {
    fun isDebuggerAttached(): Boolean = debuggerChecker()

    fun isEmulator(): Boolean {
        val fingerprint = fingerprintSupplier()
        val model = modelSupplier()
        return (fingerprint.startsWith("generic")
                || fingerprint.startsWith("unknown")
                || model.contains("google_sdk")
                || model.contains("Emulator")
                || model.contains("Android SDK built for x86"))
    }

    fun isRooted(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su"
        )
        return paths.any { rootPathChecker(it) }
    }

    fun isDebuggable(): Boolean = false

    fun isDebuggable(context: Context): Boolean {
        return (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
}
