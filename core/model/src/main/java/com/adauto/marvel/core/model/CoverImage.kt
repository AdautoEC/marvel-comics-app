package com.adauto.marvel.core.model

enum class ImageVariant(val suffix: String) {
    PORTRAIT_SMALL("portrait_small"),
    PORTRAIT_MEDIUM("portrait_medium"),
    PORTRAIT_XLARGE("portrait_xlarge"),
    PORTRAIT_FANTASTIC("portrait_fantastic"),
    PORTRAIT_UNCANNY("portrait_uncanny"),
    DETAIL("detail")
}

data class CoverImage(val path: String, val extension: String) {
    fun buildUrl(variant: ImageVariant): String {
        val securePath = if (path.startsWith("http://")) path.replaceFirst("http://", "https://") else path
        return "$securePath/${variant.suffix}.$extension"
    }

    companion object {
        private const val NOT_AVAILABLE_PATH = "image_not_available"

        fun fromRaw(path: String?, extension: String?): CoverImage? {
            if (path.isNullOrBlank() || extension.isNullOrBlank() || path.contains(NOT_AVAILABLE_PATH)) {
                return null
            }
            return CoverImage(path = path.trim(), extension = extension.trim())
        }
    }
}
