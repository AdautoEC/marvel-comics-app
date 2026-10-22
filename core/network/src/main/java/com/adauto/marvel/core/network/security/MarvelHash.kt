package com.adauto.marvel.core.network.security

import java.security.MessageDigest

/**
 * Computes the MD5 hash required by Marvel API authentication:
 * md5(ts + privateKey + publicKey).
 *
 * Returned as a 32-character lowercase hexadecimal string.
 */
fun computeMarvelHash(ts: String, privateKey: String, publicKey: String): String {
    val input = "$ts$privateKey$publicKey"
    val md = MessageDigest.getInstance("MD5")
    val digest = md.digest(input.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}
