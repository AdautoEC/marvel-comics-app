package com.adauto.cinemahub.core.network.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

open class KeystoreCipher(private val alias: String = "marvel_keys_v1") {

    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")

        fun createSpec(useStrongBox: Boolean): KeyGenParameterSpec {
            return KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .apply {
                    if (useStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        setIsStrongBoxBacked(true)
                    }
                }
                .build()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                keyGenerator.init(createSpec(useStrongBox = true))
                return keyGenerator.generateKey()
            } catch (_: StrongBoxUnavailableException) {
                // Fall back to standard KeyStore when StrongBox is not available
            }
        }

        keyGenerator.init(createSpec(useStrongBox = false))
        return keyGenerator.generateKey()
    }

    @Throws(GeneralSecurityException::class)
    open fun encrypt(plainBytes: ByteArray): ByteArray {
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, secretKey)
        }
        val iv = cipher.iv // 12 bytes for GCM
        val cipherText = cipher.doFinal(plainBytes)
        return iv + cipherText
    }

    @Throws(GeneralSecurityException::class)
    open fun decrypt(encryptedBlob: ByteArray): ByteArray {
        // Minimum GCM payload: 12-byte IV + 16-byte (128-bit) authentication tag
        if (encryptedBlob.size < 28) {
            throw GeneralSecurityException("Invalid encrypted payload size: minimum size is 28 bytes")
        }
        val secretKey = getOrCreateSecretKey()
        val iv = encryptedBlob.copyOfRange(0, 12)
        val cipherText = encryptedBlob.copyOfRange(12, encryptedBlob.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        }
        return cipher.doFinal(cipherText)
    }

    open fun deleteKey() {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (ks.containsAlias(alias)) {
            ks.deleteEntry(alias)
        }
    }
}
