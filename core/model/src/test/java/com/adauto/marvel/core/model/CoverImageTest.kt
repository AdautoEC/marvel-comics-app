package com.adauto.marvel.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CoverImageTest {

    @Test
    fun buildUrl_convertsHttpToHttps() {
        val image = CoverImage(path = "http://i.annihil.us/u/prod/marvel/i/mg/c/e0/535fecbbb9784", extension = "jpg")
        val expected = "https://i.annihil.us/u/prod/marvel/i/mg/c/e0/535fecbbb9784/portrait_uncanny.jpg"
        assertEquals(expected, image.buildUrl(ImageVariant.PORTRAIT_UNCANNY))
    }

    @Test
    fun buildUrl_preservesExistingHttps() {
        val image = CoverImage(path = "https://i.annihil.us/u/prod/marvel/i/mg/c/e0/535fecbbb9784", extension = "jpg")
        val expected = "https://i.annihil.us/u/prod/marvel/i/mg/c/e0/535fecbbb9784/detail.jpg"
        assertEquals(expected, image.buildUrl(ImageVariant.DETAIL))
    }

    @Test
    fun buildUrl_formatsAllVariantsCorrectly() {
        val image = CoverImage(path = "https://example.com/image", extension = "png")
        assertEquals("https://example.com/image/portrait_small.png", image.buildUrl(ImageVariant.PORTRAIT_SMALL))
        assertEquals("https://example.com/image/portrait_medium.png", image.buildUrl(ImageVariant.PORTRAIT_MEDIUM))
        assertEquals("https://example.com/image/portrait_xlarge.png", image.buildUrl(ImageVariant.PORTRAIT_XLARGE))
        assertEquals("https://example.com/image/portrait_fantastic.png", image.buildUrl(ImageVariant.PORTRAIT_FANTASTIC))
        assertEquals("https://example.com/image/portrait_uncanny.png", image.buildUrl(ImageVariant.PORTRAIT_UNCANNY))
        assertEquals("https://example.com/image/detail.png", image.buildUrl(ImageVariant.DETAIL))
    }

    @Test
    fun fromRaw_returnsNullWhenPathIsNullEmptyOrBlank() {
        assertNull(CoverImage.fromRaw(null, "jpg"))
        assertNull(CoverImage.fromRaw("", "jpg"))
        assertNull(CoverImage.fromRaw("   ", "jpg"))
    }

    @Test
    fun fromRaw_returnsNullWhenExtensionIsNullEmptyOrBlank() {
        assertNull(CoverImage.fromRaw("https://example.com/image", null))
        assertNull(CoverImage.fromRaw("https://example.com/image", ""))
        assertNull(CoverImage.fromRaw("https://example.com/image", "   "))
    }

    @Test
    fun fromRaw_returnsNullWhenPathContainsImageNotAvailable() {
        val notAvailableUrl = "http://i.annihil.us/u/prod/marvel/i/mg/b/40/image_not_available"
        assertNull(CoverImage.fromRaw(notAvailableUrl, "jpg"))
    }

    @Test
    fun fromRaw_returnsValidTrimmedCoverImage() {
        val image = CoverImage.fromRaw("  https://example.com/image  ", "  jpg  ")
        assertNotNull(image)
        assertEquals("https://example.com/image", image?.path)
        assertEquals("jpg", image?.extension)
    }
}
