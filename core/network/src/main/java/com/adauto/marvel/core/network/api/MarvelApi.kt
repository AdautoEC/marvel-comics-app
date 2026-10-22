package com.adauto.marvel.core.network.api

import com.adauto.marvel.core.network.model.ComicDto
import com.adauto.marvel.core.network.model.MarvelResponseDto
import com.skydoves.sandwich.ApiResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface MarvelApi {
    @GET("v1/public/comics")
    suspend fun getComics(
        @Query("offset") offset: Int,
        @Query("limit") limit: Int,
        @Query("orderBy") orderBy: String = "-focDate",
        @Query("noVariants") noVariants: Boolean = true
    ): ApiResponse<MarvelResponseDto<ComicDto>>

    @GET("v1/public/comics/{comicId}")
    suspend fun getComicDetail(
        @Path("comicId") comicId: Int
    ): ApiResponse<MarvelResponseDto<ComicDto>>
}
