package com.adauto.marvel.core.network.integrity

import android.content.Context
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClientIntegrityManagerTest {

    private val detector: HostileEnvironmentDetector = mockk()
    private lateinit var manager: ClientIntegrityManager

    @Before
    fun setUp() {
        manager = ClientIntegrityManager(detector)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun checkDeviceSignals_returnsCleanSignalsWhenNoThreatDetected() {
        every { detector.isDebuggerAttached() } returns false
        every { detector.isEmulator() } returns false
        every { detector.isRooted() } returns false

        val signals = manager.checkDeviceSignals()

        assertEquals(3, signals.size)
        assertFalse(signals.getValue("is_debugger"))
        assertFalse(signals.getValue("is_emulator"))
        assertFalse(signals.getValue("is_rooted"))
    }

    @Test
    fun checkDeviceSignals_returnsHostileSignalsWhenThreatsDetected() {
        every { detector.isDebuggerAttached() } returns true
        every { detector.isEmulator() } returns true
        every { detector.isRooted() } returns true

        val signals = manager.checkDeviceSignals()

        assertEquals(3, signals.size)
        assertTrue(signals.getValue("is_debugger"))
        assertTrue(signals.getValue("is_emulator"))
        assertTrue(signals.getValue("is_rooted"))
    }

    @Test
    fun initializeAppCheck_installsPlayIntegrityProvider() {
        val context = mockk<Context>()
        val firebaseAppCheck = mockk<FirebaseAppCheck>(relaxed = true)
        val factory = mockk<PlayIntegrityAppCheckProviderFactory>()

        mockkStatic(FirebaseAppCheck::class)
        mockkStatic(PlayIntegrityAppCheckProviderFactory::class)

        every { FirebaseAppCheck.getInstance() } returns firebaseAppCheck
        every { PlayIntegrityAppCheckProviderFactory.getInstance() } returns factory

        manager.initializeAppCheck(context)

        verify { firebaseAppCheck.installAppCheckProviderFactory(factory) }
    }

    @Test
    fun initializeAppCheck_whenDebug_installsDebugProvider() {
        val context = mockk<Context>()
        val firebaseAppCheck = mockk<FirebaseAppCheck>(relaxed = true)
        val factory = mockk<DebugAppCheckProviderFactory>()

        mockkStatic(FirebaseAppCheck::class)
        mockkStatic(DebugAppCheckProviderFactory::class)

        every { FirebaseAppCheck.getInstance() } returns firebaseAppCheck
        every { DebugAppCheckProviderFactory.getInstance() } returns factory

        manager.initializeAppCheck(context, isDebug = true)

        verify { firebaseAppCheck.installAppCheckProviderFactory(factory) }
    }
}
