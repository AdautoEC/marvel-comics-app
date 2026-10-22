package com.adauto.marvel.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TextSanitizerTest {

    @Test
    fun stripHtml_removesTagsAndConvertsEntitiesInPureKotlin() {
        val rawHtml = "<p>Witness the <b>origin</b> of Marvel Heroes! &amp; more.</p><br/>"
        val expected = "Witness the origin of Marvel Heroes! & more."
        val actual = stripHtml(rawHtml)
        assertEquals(expected, actual)
    }

    @Test
    fun stripHtml_handlesAllSupportedEntities() {
        val rawHtml = "&lt;tag&gt; &quot;quote&quot; &#39;apostrophe&#39; &amp; &nbsp;space"
        val expected = "<tag> \"quote\" 'apostrophe' &  space"
        val actual = stripHtml(rawHtml)
        assertEquals(expected, actual)
    }

    @Test
    fun stripHtml_returnsEmptyStringForNullOrBlank() {
        assertEquals("", stripHtml(null))
        assertEquals("", stripHtml(""))
        assertEquals("", stripHtml("   "))
    }
}
