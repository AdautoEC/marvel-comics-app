package com.adauto.marvel.core.data.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class ConnectivityManagerNetworkMonitorTest {

    private val context: Context = mockk()
    private val connectivityManager: ConnectivityManager = mockk(relaxed = true)
    private val callbackSlot = slot<ConnectivityManager.NetworkCallback>()

    @Before
    fun setUp() {
        mockkConstructor(NetworkRequest.Builder::class)
        val mockRequest = mockk<NetworkRequest>(relaxed = true)
        every { anyConstructed<NetworkRequest.Builder>().addCapability(any()) } returns mockk {
            every { build() } returns mockRequest
        }
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        every { context.getSystemService(ConnectivityManager::class.java) } returns connectivityManager
        every {
            connectivityManager.registerNetworkCallback(any<NetworkRequest>(), capture(callbackSlot))
        } answers { nothing }
        every { connectivityManager.unregisterNetworkCallback(any<ConnectivityManager.NetworkCallback>()) } answers { nothing }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun when_connectivityManager_is_null_emits_false() = runTest {
        val noCmContext = mockk<Context>()
        every { noCmContext.getSystemService(Context.CONNECTIVITY_SERVICE) } returns null
        every { noCmContext.getSystemService(ConnectivityManager::class.java) } returns null

        val monitor = ConnectivityManagerNetworkMonitor(noCmContext)

        monitor.isOnline.test {
            assertThat(awaitItem()).isFalse()
            awaitComplete()
        }
    }

    @Test
    fun initial_state_when_connected_emits_true() = runTest {
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true

        val monitor = ConnectivityManagerNetworkMonitor(context)

        monitor.isOnline.test {
            assertThat(awaitItem()).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun initial_state_when_disconnected_emits_false() = runTest {
        every { connectivityManager.activeNetwork } returns null

        val monitor = ConnectivityManagerNetworkMonitor(context)

        monitor.isOnline.test {
            assertThat(awaitItem()).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun callbacks_update_online_status() = runTest {
        every { connectivityManager.activeNetwork } returns null

        val monitor = ConnectivityManagerNetworkMonitor(context)

        monitor.isOnline.test {
            assertThat(awaitItem()).isFalse()

            val mockNetwork1 = mockk<Network>()
            callbackSlot.captured.onAvailable(mockNetwork1)
            assertThat(awaitItem()).isTrue()

            val mockNetwork2 = mockk<Network>()
            callbackSlot.captured.onAvailable(mockNetwork2)
            // Still online with multiple networks
            assertThat(awaitItem()).isTrue()

            // One lost, but one remains
            callbackSlot.captured.onLost(mockNetwork1)
            assertThat(awaitItem()).isTrue()

            // Last network lost -> offline
            callbackSlot.captured.onLost(mockNetwork2)
            assertThat(awaitItem()).isFalse()

            cancelAndIgnoreRemainingEvents()
        }

        verify { connectivityManager.unregisterNetworkCallback(any<ConnectivityManager.NetworkCallback>()) }
    }
}
