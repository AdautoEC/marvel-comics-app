package com.adauto.marvel.core.network.security

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertEquals
import org.junit.Test

class MarvelHashTest {

    @Test
    fun `marvelHash matches official Marvel documentation test vector`() {
        val expected = "ffd275c5130566a2916217b101f26150"
        val actual = computeMarvelHash(ts = "1", privateKey = "abcd", publicKey = "1234")
        assertEquals(expected, actual)
    }

    @Test
    fun `marvelHash returns 32 character lowercase hex string`() {
        val actual = computeMarvelHash(ts = "99999", privateKey = "my_private_key", publicKey = "my_public_key")
        assertThat(actual.matches(Regex("^[a-f0-9]{32}$"))).isTrue()
    }
}
