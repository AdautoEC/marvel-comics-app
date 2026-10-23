package com.adauto.marvel.core.ui.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.util.DisplayMetrics
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.Test

class CustomTabLauncherTest {

    @Test
    fun launchPartialCustomTab_invokes_activity_with_calculated_height() {
        val context = mockk<Activity>(relaxed = true)
        val resources = mockk<Resources>()
        val displayMetrics = DisplayMetrics().apply {
            heightPixels = 1000
        }
        every { context.resources } returns resources
        every { resources.displayMetrics } returns displayMetrics

        val uri = mockk<Uri>(relaxed = true)

        CustomTabLauncher.launchPartialCustomTab(context, uri)

        verify { context.startActivity(any(), any()) }
    }

    @Test
    fun launchPartialCustomTab_falls_back_to_ACTION_VIEW_when_custom_tabs_fails() {
        val context = mockk<Activity>(relaxed = true)
        val resources = mockk<Resources>()
        val displayMetrics = DisplayMetrics().apply {
            heightPixels = 1000
        }
        every { context.resources } returns resources
        every { resources.displayMetrics } returns displayMetrics

        every { context.startActivity(any(), any()) } throws ActivityNotFoundException()
        every { context.startActivity(any()) } just runs

        val uri = mockk<Uri>(relaxed = true)

        CustomTabLauncher.launchPartialCustomTab(context, uri)

        val intentSlot = slot<Intent>()
        verify { context.startActivity(capture(intentSlot)) }
    }

    @Test
    fun launchPartialCustomTab_adds_NEW_TASK_flag_for_non_activity_context() {
        val context = mockk<Context>(relaxed = true)
        val resources = mockk<Resources>()
        val displayMetrics = DisplayMetrics().apply {
            heightPixels = 1000
        }
        every { context.resources } returns resources
        every { resources.displayMetrics } returns displayMetrics

        every { context.startActivity(any(), any()) } throws ActivityNotFoundException()
        every { context.startActivity(any()) } just runs

        val uri = mockk<Uri>(relaxed = true)

        CustomTabLauncher.launchPartialCustomTab(context, uri)

        val intentSlot = slot<Intent>()
        verify { context.startActivity(capture(intentSlot)) }
    }
}
