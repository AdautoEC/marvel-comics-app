package com.adauto.cinemahub.core.model

sealed interface UiText {
    data class Dynamic(val value: String) : UiText
    data class Resource(val resId: Int, val args: List<Any> = emptyList()) : UiText
}
