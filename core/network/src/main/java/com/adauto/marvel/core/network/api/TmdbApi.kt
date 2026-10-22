package com.adauto.marvel.core.network.api

import com.adauto.marvel.core.network.model.TmdbMovieDetailDto
import com.adauto.marvel.core.network.model.TmdbMovieDto
import com.adauto.marvel.core.network.model.TmdbPageResponseDto
import com.skydoves.sandwich.ApiResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApi {

    @GET("3/movie/popular")
    suspend fun getPopularMovies(
        @Query("page") page: Int = 1,
        @Query("language") language: String = "pt-BR"
    ): ApiResponse<TmdbPageResponseDto<TmdbMovieDto>>

    @GET("3/movie/top_rated")
    suspend fun getTopRatedMovies(
        @Query("page") page: Int = 1,
        @Query("language") language: String = "pt-BR"
    ): ApiResponse<TmdbPageResponseDto<TmdbMovieDto>>

    @GET("3/discover/movie")
    suspend fun getMarvelMovies(
        @Query("with_companies") companyId: Int = 420,
        @Query("page") page: Int = 1,
        @Query("language") language: String = "pt-BR"
    ): ApiResponse<TmdbPageResponseDto<TmdbMovieDto>>

    @GET("3/movie/{movie_id}")
    suspend fun getMovieDetail(
        @Path("movie_id") movieId: Int,
        @Query("append_to_response") append: String = "credits",
        @Query("language") language: String = "pt-BR"
    ): ApiResponse<TmdbMovieDetailDto>
}
