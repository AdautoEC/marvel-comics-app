package com.adauto.cinemahub.core.data.fake

import com.adauto.cinemahub.core.network.api.TmdbApi
import com.adauto.cinemahub.core.network.model.TmdbAccountDto
import com.adauto.cinemahub.core.network.model.TmdbActionResponseDto
import com.adauto.cinemahub.core.network.model.TmdbCreateSessionRequestDto
import com.adauto.cinemahub.core.network.model.TmdbDeleteSessionRequestDto
import com.adauto.cinemahub.core.network.model.TmdbDeleteSessionResponseDto
import com.adauto.cinemahub.core.network.model.TmdbFavoriteRequestDto
import com.adauto.cinemahub.core.network.model.TmdbMovieDetailDto
import com.adauto.cinemahub.core.network.model.TmdbMovieDto
import com.adauto.cinemahub.core.network.model.TmdbPageResponseDto
import com.adauto.cinemahub.core.network.model.TmdbRequestTokenDto
import com.adauto.cinemahub.core.network.model.TmdbSessionResponseDto
import com.adauto.cinemahub.core.network.model.TmdbWatchlistRequestDto
import com.skydoves.sandwich.ApiResponse

@Suppress("TooManyFunctions", "ReturnCount")
class FakeTmdbApi : TmdbApi {

    val favorites = mutableListOf<TmdbMovieDto>()
    val watchlist = mutableListOf<TmdbMovieDto>()
    val popular = mutableListOf<TmdbMovieDto>()
    val topRated = mutableListOf<TmdbMovieDto>()
    val movieDetails = mutableMapOf<Int, TmdbMovieDetailDto>()

    var failureResponse: ApiResponse.Failure<*>? = null
    var createRequestTokenResponse: ApiResponse<TmdbRequestTokenDto>? = null
    var createSessionResponse: ApiResponse<TmdbSessionResponseDto>? = null
    var getAccountDetailsResponse: ApiResponse<TmdbAccountDto>? = null
    var deleteSessionHandler: (
        suspend (TmdbDeleteSessionRequestDto) -> ApiResponse<TmdbDeleteSessionResponseDto>
    )? = null
    var markAsFavoriteHandler: (
        suspend (Int, String?, TmdbFavoriteRequestDto) -> ApiResponse<TmdbActionResponseDto>
    )? = null
    var addToWatchlistHandler: (
        suspend (Int, String?, TmdbWatchlistRequestDto) -> ApiResponse<TmdbActionResponseDto>
    )? = null
    val deletedSessions = mutableListOf<String>()

    override suspend fun getPopularMovies(
        page: Int,
        language: String
    ): ApiResponse<TmdbPageResponseDto<TmdbMovieDto>> {
        failureResponse?.let {
            @Suppress("UNCHECKED_CAST")
            return it as ApiResponse<TmdbPageResponseDto<TmdbMovieDto>>
        }
        return ApiResponse.Success(
            TmdbPageResponseDto(
                page = page,
                results = popular,
                totalPages = 1,
                totalResults = popular.size
            )
        )
    }

    override suspend fun getTopRatedMovies(
        page: Int,
        language: String
    ): ApiResponse<TmdbPageResponseDto<TmdbMovieDto>> {
        failureResponse?.let {
            @Suppress("UNCHECKED_CAST")
            return it as ApiResponse<TmdbPageResponseDto<TmdbMovieDto>>
        }
        return ApiResponse.Success(
            TmdbPageResponseDto(
                page = page,
                results = topRated,
                totalPages = 1,
                totalResults = topRated.size
            )
        )
    }

    override suspend fun getStudioMovies(
        companyId: Int,
        page: Int,
        language: String
    ): ApiResponse<TmdbPageResponseDto<TmdbMovieDto>> {
        failureResponse?.let {
            @Suppress("UNCHECKED_CAST")
            return it as ApiResponse<TmdbPageResponseDto<TmdbMovieDto>>
        }
        return ApiResponse.Success(
            TmdbPageResponseDto(
                page = page,
                results = popular,
                totalPages = 1,
                totalResults = popular.size
            )
        )
    }

    override suspend fun getMovieDetail(
        movieId: Int,
        append: String,
        language: String
    ): ApiResponse<TmdbMovieDetailDto> {
        failureResponse?.let {
            @Suppress("UNCHECKED_CAST")
            return it as ApiResponse<TmdbMovieDetailDto>
        }
        val detail = movieDetails[movieId] ?: TmdbMovieDetailDto(
            id = movieId,
            title = "Movie $movieId",
            overview = "Overview $movieId",
            posterPath = "/poster_$movieId.jpg",
            backdropPath = "/backdrop_$movieId.jpg",
            voteAverage = 8.0,
            runtime = 120,
            releaseDate = "2024-01-01"
        )
        return ApiResponse.Success(detail)
    }

    override suspend fun createRequestToken(): ApiResponse<TmdbRequestTokenDto> {
        createRequestTokenResponse?.let { return it }
        failureResponse?.let {
            @Suppress("UNCHECKED_CAST")
            return it as ApiResponse<TmdbRequestTokenDto>
        }
        return ApiResponse.Success(
            TmdbRequestTokenDto(
                success = true,
                expires_at = "2026-12-31",
                request_token = "fake_request_token"
            )
        )
    }

    override suspend fun createSession(
        body: TmdbCreateSessionRequestDto
    ): ApiResponse<TmdbSessionResponseDto> {
        createSessionResponse?.let { return it }
        failureResponse?.let {
            @Suppress("UNCHECKED_CAST")
            return it as ApiResponse<TmdbSessionResponseDto>
        }
        return ApiResponse.Success(
            TmdbSessionResponseDto(
                success = true,
                session_id = "fake_session_123"
            )
        )
    }

    override suspend fun deleteSession(
        body: TmdbDeleteSessionRequestDto
    ): ApiResponse<TmdbDeleteSessionResponseDto> {
        deleteSessionHandler?.let { return it(body) }
        deletedSessions.add(body.sessionId)
        return ApiResponse.Success(
            TmdbDeleteSessionResponseDto(success = true)
        )
    }

    override suspend fun getAccountDetails(
        sessionId: String?
    ): ApiResponse<TmdbAccountDto> {
        getAccountDetailsResponse?.let { return it }
        failureResponse?.let {
            @Suppress("UNCHECKED_CAST")
            return it as ApiResponse<TmdbAccountDto>
        }
        return ApiResponse.Success(
            TmdbAccountDto(
                id = 42,
                username = "adauto",
                name = "Adauto",
                includeAdult = false
            )
        )
    }

    override suspend fun markAsFavorite(
        accountId: Int,
        sessionId: String?,
        body: TmdbFavoriteRequestDto
    ): ApiResponse<TmdbActionResponseDto> {
        markAsFavoriteHandler?.let { return it(accountId, sessionId, body) }
        failureResponse?.let {
            @Suppress("UNCHECKED_CAST")
            return it as ApiResponse<TmdbActionResponseDto>
        }
        if (body.favorite) {
            if (favorites.none { it.id == body.media_id }) {
                favorites.add(TmdbMovieDto(id = body.media_id, title = "Movie ${body.media_id}"))
            }
        } else {
            favorites.removeAll { it.id == body.media_id }
        }
        return ApiResponse.Success(TmdbActionResponseDto(status_code = 1, status_message = "Success"))
    }

    override suspend fun addToWatchlist(
        accountId: Int,
        sessionId: String?,
        body: TmdbWatchlistRequestDto
    ): ApiResponse<TmdbActionResponseDto> {
        addToWatchlistHandler?.let { return it(accountId, sessionId, body) }
        failureResponse?.let {
            @Suppress("UNCHECKED_CAST")
            return it as ApiResponse<TmdbActionResponseDto>
        }
        if (body.watchlist) {
            if (watchlist.none { it.id == body.media_id }) {
                watchlist.add(TmdbMovieDto(id = body.media_id, title = "Movie ${body.media_id}"))
            }
        } else {
            watchlist.removeAll { it.id == body.media_id }
        }
        return ApiResponse.Success(TmdbActionResponseDto(status_code = 1, status_message = "Success"))
    }

    override suspend fun getFavoriteMovies(
        accountId: Int,
        sessionId: String?,
        page: Int
    ): ApiResponse<TmdbPageResponseDto<TmdbMovieDto>> {
        failureResponse?.let {
            @Suppress("UNCHECKED_CAST")
            return it as ApiResponse<TmdbPageResponseDto<TmdbMovieDto>>
        }
        return ApiResponse.Success(
            TmdbPageResponseDto(
                page = page,
                results = favorites.toList(),
                totalPages = 1,
                totalResults = favorites.size
            )
        )
    }

    override suspend fun getWatchlistMovies(
        accountId: Int,
        sessionId: String?,
        page: Int
    ): ApiResponse<TmdbPageResponseDto<TmdbMovieDto>> {
        failureResponse?.let {
            @Suppress("UNCHECKED_CAST")
            return it as ApiResponse<TmdbPageResponseDto<TmdbMovieDto>>
        }
        return ApiResponse.Success(
            TmdbPageResponseDto(
                page = page,
                results = watchlist.toList(),
                totalPages = 1,
                totalResults = watchlist.size
            )
        )
    }
}
