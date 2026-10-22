package com.adauto.marvel.core.model

fun stripHtml(html: String?): String {
    if (html.isNullOrBlank()) return ""
    return html
        .replace(Regex("<[^>]*>"), "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .trim()
}
