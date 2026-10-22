package com.adauto.marvel.core.network.security

import com.adauto.marvel.core.model.MarvelKeys
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.GeneralSecurityException

class MarvelKeyProviderImplTest {
    private val remoteConfigSource: RemoteConfigKeySource = mockk()
    private val cipher: KeystoreCipher = mockk()
    private val storage: EncryptedKeyStorage = mockk()

    private lateinit var keyProvider: MarvelKeyProviderImpl

    @Before
    fun setUp() {
        keyProvider = MarvelKeyProviderImpl(remoteConfigSource, cipher, storage)
    }

    @Test
    fun `current returns cached key without calling network when storage has key`() = runTest {
        val keys = MarvelKeys(publicKey = "pub", privateKey = "priv", keyVersion = 1)
        coEvery { storage.loadEncryptedBlob() } returns byteArrayOf(1, 2, 3)
        coEvery { cipher.decrypt(byteArrayOf(1, 2, 3)) } returns """{"publicKey":"pub","privateKey":"priv","keyVersion":1}""".toByteArray()

        val result = keyProvider.current()
        assertEquals(keys, result)
        coVerify(exactly = 0) { remoteConfigSource.fetchKeys() }
    }

    @Test
    fun `current returns cached in-memory key on subsequent calls without accessing storage or remote`() = runTest {
        val keys = MarvelKeys(publicKey = "pub", privateKey = "priv", keyVersion = 1)
        coEvery { storage.loadEncryptedBlob() } returns byteArrayOf(1, 2, 3)
        coEvery { cipher.decrypt(byteArrayOf(1, 2, 3)) } returns """{"publicKey":"pub","privateKey":"priv","keyVersion":1}""".toByteArray()

        val firstResult = keyProvider.current()
        val secondResult = keyProvider.current()

        assertEquals(keys, firstResult)
        assertEquals(keys, secondResult)
        coVerify(exactly = 1) { storage.loadEncryptedBlob() }
        coVerify(exactly = 0) { remoteConfigSource.fetchKeys() }
    }

    @Test
    fun `current fetches from Remote Config and saves encrypted when no local key exists`() = runTest {
        val keys = MarvelKeys(publicKey = "pub_remote", privateKey = "priv_remote", keyVersion = 1)
        coEvery { storage.loadEncryptedBlob() } returns null
        coEvery { remoteConfigSource.fetchKeys() } returns keys
        coEvery { cipher.encrypt(any()) } returns byteArrayOf(4, 5, 6)
        coEvery { storage.saveEncryptedBlob(any()) } returns Unit

        val result = keyProvider.current()
        assertEquals(keys, result)
        coVerify(exactly = 1) { storage.saveEncryptedBlob(byteArrayOf(4, 5, 6)) }
    }

    @Test
    fun `current returns null when no local key exists and remote config returns null`() = runTest {
        coEvery { storage.loadEncryptedBlob() } returns null
        coEvery { remoteConfigSource.fetchKeys() } returns null

        val result = keyProvider.current()
        assertNull(result)
        coVerify(exactly = 0) { storage.saveEncryptedBlob(any()) }
    }

    @Test
    fun `current clears storage and deletes keystore key when decryption throws GeneralSecurityException then fetches remote`() = runTest {
        val keys = MarvelKeys(publicKey = "pub_remote", privateKey = "priv_remote", keyVersion = 2)
        coEvery { storage.loadEncryptedBlob() } returns byteArrayOf(9, 9, 9)
        coEvery { cipher.decrypt(byteArrayOf(9, 9, 9)) } throws GeneralSecurityException("Corrupted keystore entry")
        coEvery { storage.clear() } returns Unit
        coEvery { cipher.deleteKey() } returns Unit
        coEvery { remoteConfigSource.fetchKeys() } returns keys
        coEvery { cipher.encrypt(any()) } returns byteArrayOf(7, 8, 9)
        coEvery { storage.saveEncryptedBlob(any()) } returns Unit

        val result = keyProvider.current()
        assertEquals(keys, result)
        coVerify(exactly = 1) { storage.clear() }
        coVerify(exactly = 1) { cipher.deleteKey() }
        coVerify(exactly = 1) { storage.saveEncryptedBlob(byteArrayOf(7, 8, 9)) }
    }

    @Test
    fun `forceRefresh returns false when remote returns null and does not overwrite existing key`() = runTest {
        val existingKeys = MarvelKeys(publicKey = "pub", privateKey = "priv", keyVersion = 1)
        coEvery { storage.loadEncryptedBlob() } returns byteArrayOf(1, 2)
        coEvery { cipher.decrypt(byteArrayOf(1, 2)) } returns """{"publicKey":"pub","privateKey":"priv","keyVersion":1}""".toByteArray()

        assertEquals(existingKeys, keyProvider.current())

        coEvery { remoteConfigSource.fetchKeys() } returns null
        val refreshed = keyProvider.forceRefresh()

        assertFalse(refreshed)
        assertEquals(existingKeys, keyProvider.current())
    }

    @Test
    fun `forceRefresh updates storage and memory and returns true when key version changed`() = runTest {
        val existingKeys = MarvelKeys(publicKey = "pub", privateKey = "priv", keyVersion = 1)
        val rotatedKeys = MarvelKeys(publicKey = "pub_v2", privateKey = "priv_v2", keyVersion = 2)
        coEvery { storage.loadEncryptedBlob() } returns byteArrayOf(1, 2)
        coEvery { cipher.decrypt(byteArrayOf(1, 2)) } returns """{"publicKey":"pub","privateKey":"priv","keyVersion":1}""".toByteArray()

        assertEquals(existingKeys, keyProvider.current())

        coEvery { remoteConfigSource.fetchKeys() } returns rotatedKeys
        coEvery { cipher.encrypt(any()) } returns byteArrayOf(3, 4)
        coEvery { storage.saveEncryptedBlob(any()) } returns Unit

        val refreshed = keyProvider.forceRefresh()

        assertTrue(refreshed)
        assertEquals(rotatedKeys, keyProvider.current())
        coVerify(exactly = 1) { storage.saveEncryptedBlob(byteArrayOf(3, 4)) }
    }

    @Test
    fun `forceRefresh updates storage and returns false when key version is unchanged`() = runTest {
        val existingKeys = MarvelKeys(publicKey = "pub", privateKey = "priv", keyVersion = 1)
        val sameVersionKeys = MarvelKeys(publicKey = "pub_alt", privateKey = "priv_alt", keyVersion = 1)
        coEvery { storage.loadEncryptedBlob() } returns byteArrayOf(1, 2)
        coEvery { cipher.decrypt(byteArrayOf(1, 2)) } returns """{"publicKey":"pub","privateKey":"priv","keyVersion":1}""".toByteArray()

        assertEquals(existingKeys, keyProvider.current())

        coEvery { remoteConfigSource.fetchKeys() } returns sameVersionKeys
        coEvery { cipher.encrypt(any()) } returns byteArrayOf(5, 6)
        coEvery { storage.saveEncryptedBlob(any()) } returns Unit

        val refreshed = keyProvider.forceRefresh()

        assertFalse(refreshed)
        assertEquals(sameVersionKeys, keyProvider.current())
        coVerify(exactly = 1) { storage.saveEncryptedBlob(byteArrayOf(5, 6)) }
    }

    @Test
    fun `refreshIfNewer updates keys when remote has higher version`() = runTest {
        val existingKeys = MarvelKeys(publicKey = "pub", privateKey = "priv", keyVersion = 1)
        val newerKeys = MarvelKeys(publicKey = "pub_v3", privateKey = "priv_v3", keyVersion = 3)
        coEvery { storage.loadEncryptedBlob() } returns byteArrayOf(1, 2)
        coEvery { cipher.decrypt(byteArrayOf(1, 2)) } returns """{"publicKey":"pub","privateKey":"priv","keyVersion":1}""".toByteArray()

        assertEquals(existingKeys, keyProvider.current())

        coEvery { remoteConfigSource.fetchKeys() } returns newerKeys
        coEvery { cipher.encrypt(any()) } returns byteArrayOf(7, 8)
        coEvery { storage.saveEncryptedBlob(any()) } returns Unit

        keyProvider.refreshIfNewer()

        assertEquals(newerKeys, keyProvider.current())
        coVerify(exactly = 1) { storage.saveEncryptedBlob(byteArrayOf(7, 8)) }
    }

    @Test
    fun `refreshIfNewer does not update keys when remote has same or lower version`() = runTest {
        val existingKeys = MarvelKeys(publicKey = "pub", privateKey = "priv", keyVersion = 2)
        val olderKeys = MarvelKeys(publicKey = "pub_v1", privateKey = "priv_v1", keyVersion = 1)
        coEvery { storage.loadEncryptedBlob() } returns byteArrayOf(1, 2)
        coEvery { cipher.decrypt(byteArrayOf(1, 2)) } returns """{"publicKey":"pub","privateKey":"priv","keyVersion":2}""".toByteArray()

        assertEquals(existingKeys, keyProvider.current())

        coEvery { remoteConfigSource.fetchKeys() } returns olderKeys

        keyProvider.refreshIfNewer()

        assertEquals(existingKeys, keyProvider.current())
        coVerify(exactly = 0) { cipher.encrypt(any()) }
        coVerify(exactly = 0) { storage.saveEncryptedBlob(any()) }
    }

    @Test
    fun `refreshIfNewer catches and ignores exceptions from remote config source`() = runTest {
        val existingKeys = MarvelKeys(publicKey = "pub", privateKey = "priv", keyVersion = 1)
        coEvery { storage.loadEncryptedBlob() } returns byteArrayOf(1, 2)
        coEvery { cipher.decrypt(byteArrayOf(1, 2)) } returns """{"publicKey":"pub","privateKey":"priv","keyVersion":1}""".toByteArray()

        assertEquals(existingKeys, keyProvider.current())

        coEvery { remoteConfigSource.fetchKeys() } throws RuntimeException("Network down")

        // Should not throw
        keyProvider.refreshIfNewer()

        assertEquals(existingKeys, keyProvider.current())
    }
}
