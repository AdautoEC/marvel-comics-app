package com.adauto.marvel.feature.detail

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FormatOnsaleDateTest {

    @Test
    fun `null and blank returns null`() {
        assertThat(formatOnsaleDate(null)).isNull()
        assertThat(formatOnsaleDate("")).isNull()
        assertThat(formatOnsaleDate("   ")).isNull()
    }

    @Test
    fun `iso date string formats to dd-MM-yyyy`() {
        assertThat(formatOnsaleDate("2026-05-15")).isEqualTo("15/05/2026")
        assertThat(formatOnsaleDate("2026-10-22T08:00:00-0400")).isEqualTo("22/10/2026")
    }

    @Test
    fun `arbitrary string without dashes returns string`() {
        assertThat(formatOnsaleDate("Upcoming")).isEqualTo("Upcoming")
    }
}
