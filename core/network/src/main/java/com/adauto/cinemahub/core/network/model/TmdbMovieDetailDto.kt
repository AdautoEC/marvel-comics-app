package com.adauto.cinemahub.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TmdbGenreDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String
)

@Serializable
data class TmdbCastDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("character") val character: String? = null
)

@Serializable
data class TmdbCreditsDto(
    @SerialName("cast") val cast: List<TmdbCastDto> = emptyList()
)

@Serializable
data class TmdbMovieDetailDto(
    @SerialName("id") val id: Int,
    @SerialName("title") val title: String,
    @SerialName("overview") val overview: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("runtime") val runtime: Int? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("genres") val genres: List<TmdbGenreDto> = emptyList(),
    @SerialName("credits") val credits: TmdbCreditsDto? = null
)
