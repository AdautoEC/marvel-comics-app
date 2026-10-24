package com.adauto.marvel.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AccountProfileTest {

    @Test
    fun accountProfile_holdsExpectedPropertiesAndDefaults() {
        val profile = AccountProfile(
            id = 42,
            username = "stan_lee"
        )

        assertThat(profile.id).isEqualTo(42)
        assertThat(profile.username).isEqualTo("stan_lee")
        assertThat(profile.name).isNull()
        assertThat(profile.includeAdult).isFalse()
    }

    @Test
    fun accountProfile_customProperties() {
        val profile = AccountProfile(
            id = 100,
            username = "peter_parker",
            name = "Peter Parker",
            includeAdult = true
        )

        assertThat(profile.id).isEqualTo(100)
        assertThat(profile.username).isEqualTo("peter_parker")
        assertThat(profile.name).isEqualTo("Peter Parker")
        assertThat(profile.includeAdult).isTrue()
    }
}
