package com.adauto.cinemahub.core.network.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.GeneralSecurityException

@RunWith(AndroidJUnit4::class)
class KeystoreCipherTest {
    private lateinit var cipher: KeystoreCipher

    @Before
    fun setUp() {
        cipher = KeystoreCipher(alias = "test_marvel_keys_alias")
        cipher.deleteKey()
    }

    @After
    fun tearDown() {
        cipher.deleteKey()
    }

    @Test
    fun encryptAndDecryptRoundTrip_matchesOriginalPlainText() {
        val originalText = "my_secret_marvel_private_key_12345"
        val plainBytes = originalText.toByteArray(Charsets.UTF_8)

        val encryptedBlob = cipher.encrypt(plainBytes)
        assertFalse(plainBytes.contentEquals(encryptedBlob))

        val decryptedBytes = cipher.decrypt(encryptedBlob)
        assertArrayEquals(plainBytes, decryptedBytes)
    }

    @Test
    fun decrypt_withInvalidPayloadSize_throwsGeneralSecurityException() {
        val shortBlob = byteArrayOf(1, 2, 3)
        assertThrows(GeneralSecurityException::class.java) {
            cipher.decrypt(shortBlob)
        }
    }
}
