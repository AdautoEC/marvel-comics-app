package com.adauto.cinemahub.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class AccountProfile(
    val id: Int,
    val username: String,
    val name: String? = null,
    val includeAdult: Boolean = false
)
