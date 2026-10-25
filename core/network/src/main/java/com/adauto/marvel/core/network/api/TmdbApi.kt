package com.adauto.marvel.core.network.api

import com.adauto.marvel.core.network.model.TmdbActionResponseDto
import com.adauto.marvel.core.network.model.TmdbAccountDto
import com.adauto.marvel.core.network.model.TmdbCreateSessionRequestDto
import com.adauto.marvel.core.network.model.TmdbDeleteSessionRequestDto
import com.adauto.marvel.core.network.model.TmdbDeleteSessionResponseDto
import com.adauto.marvel.core.network.model.TmdbFavoriteRequestDto
import com.adauto.marvel.core.network.model.TmdbMovieDetailDto
import com.adauto.marvel.core.network.model.TmdbMovieDto
import com.adauto.marvel.core.network.model.TmdbPageResponseDto
import com.adauto.marvel.core.network.model.TmdbRequestTokenDto
import com.adauto.marvel.core.network.model.TmdbSessionResponseDto
import com.adauto.marvel.core.network.model.TmdbWatchlistRequestDto
import com.skydoves.sandwich.ApiResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
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

    @GET("3/authentication/token/new")
    suspend fun createRequestToken(): ApiResponse<TmdbRequestTokenDto>

    @POST("3/authentication/session/new")
    suspend fun createSession(
        @Body body: TmdbCreateSessionRequestDto
    ): ApiResponse<TmdbSessionResponseDto>

    @HTTP(method = "DELETE", path = "3/authentication/session", hasBody = true)
    suspend fun deleteSession(
        @Body body: TmdbDeleteSessionRequestDto
    ): ApiResponse<TmdbDeleteSessionResponseDto>

    @GET("3/account")
    suspend fun getAccountDetails(
        @Query("session_id") sessionId: String
    ): ApiResponse<TmdbAccountDto>

    @POST("3/account/{account_id}/favorite")
    suspend fun markAsFavorite(
        @Path("account_id") accountId: Int,
        @Query("session_id") sessionId: String,
        @Body body: TmdbFavoriteRequestDto
    ): ApiResponse<TmdbActionResponseDto>

    @POST("3/account/{account_id}/watchlist")
    suspend fun addToWatchlist(
        @Path("account_id") accountId: Int,
        @Query("session_id") sessionId: String,
        @Body body: TmdbWatchlistRequestDto
    ): ApiResponse<TmdbActionResponseDto>

    @GET("3/account/{account_id}/favorite/movies")
    suspend fun getFavoriteMovies(
        @Path("account_id") accountId: Int,
        @Query("session_id") sessionId: String,
        @Query("page") page: Int = 1
    ): ApiResponse<TmdbPageResponseDto<TmdbMovieDto>>

    @GET("3/account/{account_id}/watchlist/movies")
    suspend fun getWatchlistMovies(
        @Path("account_id") accountId: Int,
        @Query("session_id") sessionId: String,
        @Query("page") page: Int = 1
    ): ApiResponse<TmdbPageResponseDto<TmdbMovieDto>>
}
