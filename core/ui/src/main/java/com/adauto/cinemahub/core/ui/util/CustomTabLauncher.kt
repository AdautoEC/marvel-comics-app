package com.adauto.cinemahub.core.ui.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent

object CustomTabLauncher {

    private const val HEIGHT_RATIO = 0.65f
    private const val DARK_TOOLBAR_COLOR = 0xFF0F0F14.toInt()

    fun launchPartialCustomTab(context: Context, url: Uri) {
        val displayMetrics = context.resources.displayMetrics
        val heightPx = (displayMetrics.heightPixels * HEIGHT_RATIO).toInt()

        val colorSchemeParams = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(DARK_TOOLBAR_COLOR)
            .build()

        val customTabsIntent = CustomTabsIntent.Builder()
            .setInitialActivityHeightPx(heightPx, CustomTabsIntent.ACTIVITY_HEIGHT_DEFAULT)
            .setDefaultColorSchemeParams(colorSchemeParams)
            .setShowTitle(true)
            .setUrlBarHidingEnabled(true)
            .build()

        try {
            customTabsIntent.launchUrl(context, url)
        } catch (_: ActivityNotFoundException) {
            launchFallback(context, url)
        } catch (_: SecurityException) {
            launchFallback(context, url)
        }
    }

    private fun launchFallback(context: Context, url: Uri) {
        val fallbackIntent = Intent(Intent.ACTION_VIEW, url).apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        try {
            context.startActivity(fallbackIntent)
        } catch (_: ActivityNotFoundException) {
            // Ignored if no browser/handler is available
        } catch (_: SecurityException) {
            // Ignored if permission is restricted
        }
    }
}
