package com.adauto.marvel.core.model

import kotlinx.serialization.Serializable

@Serializable
data class MarvelKeys(
    val publicKey: String,
    val privateKey: String,
    val keyVersion: Int
)
