package com.adauto.cinemahub.core.model

private val HTML_TAG_REGEX = Regex("<[^>]*>")

fun stripHtml(html: String?): String {
    if (html.isNullOrBlank()) return ""
    return html
        .replace(HTML_TAG_REGEX, "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .trim()
}
