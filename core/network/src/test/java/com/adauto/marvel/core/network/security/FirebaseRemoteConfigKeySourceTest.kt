package com.adauto.marvel.core.network.security

import com.adauto.marvel.core.model.MarvelKeys
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class FirebaseRemoteConfigKeySourceTest {

    private val remoteConfig: FirebaseRemoteConfig = mockk()
    private val task: Task<Boolean> = mockk()
    private lateinit var source: FirebaseRemoteConfigKeySource

    @Before
    fun setUp() {
        source = FirebaseRemoteConfigKeySource(remoteConfig)
    }

    @Test
    fun `fetchKeys parses and returns MarvelKeys on success`() = runTest {
        every { remoteConfig.fetchAndActivate() } returns task
        every { task.addOnSuccessListener(any()) } answers {
            val listener = firstArg<OnSuccessListener<Boolean>>()
            listener.onSuccess(true)
            task
        }
        every { task.addOnFailureListener(any()) } returns task
        every { remoteConfig.getString(FirebaseRemoteConfigKeySource.KEY_MARVEL_KEYS) } returns
            """{"publicKey":"pub123","privateKey":"priv456","keyVersion":4}"""

        val result = source.fetchKeys()
        assertEquals(MarvelKeys("pub123", "priv456", 4), result)
    }

    @Test
    fun `fetchKeys returns null when remote config string is blank`() = runTest {
        every { remoteConfig.fetchAndActivate() } returns task
        every { task.addOnSuccessListener(any()) } answers {
            val listener = firstArg<OnSuccessListener<Boolean>>()
            listener.onSuccess(true)
            task
        }
        every { task.addOnFailureListener(any()) } returns task
        every { remoteConfig.getString(FirebaseRemoteConfigKeySource.KEY_MARVEL_KEYS) } returns "   "

        val result = source.fetchKeys()
        assertNull(result)
    }

    @Test
    fun `fetchKeys returns null when remote config string is invalid JSON`() = runTest {
        every { remoteConfig.fetchAndActivate() } returns task
        every { task.addOnSuccessListener(any()) } answers {
            val listener = firstArg<OnSuccessListener<Boolean>>()
            listener.onSuccess(true)
            task
        }
        every { task.addOnFailureListener(any()) } returns task
        every { remoteConfig.getString(FirebaseRemoteConfigKeySource.KEY_MARVEL_KEYS) } returns "{ invalid json"

        val result = source.fetchKeys()
        assertNull(result)
    }

    @Test
    fun `fetchKeys returns null when task fails`() = runTest {
        every { remoteConfig.fetchAndActivate() } returns task
        every { task.addOnSuccessListener(any()) } returns task
        every { task.addOnFailureListener(any()) } answers {
            val listener = firstArg<OnFailureListener>()
            listener.onFailure(RuntimeException("Fetch failed"))
            task
        }

        val result = source.fetchKeys()
        assertNull(result)
    }
}
