package com.adauto.marvel.core.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.adauto.marvel.core.data.mapper.toDetail
import com.adauto.marvel.core.data.mapper.toSummary
import com.adauto.marvel.core.data.mediator.ComicsRemoteMediator
import com.adauto.marvel.core.database.MarvelDatabase
import com.adauto.marvel.core.model.ComicDetail
import com.adauto.marvel.core.model.ComicSummary
import com.adauto.marvel.core.model.DomainError
import com.adauto.marvel.core.model.Outcome
import com.adauto.marvel.core.network.api.MarvelApi
import com.skydoves.sandwich.onError
import com.skydoves.sandwich.onException
import com.skydoves.sandwich.onSuccess
import com.skydoves.sandwich.retrofit.statusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ComicsRepositoryImpl @Inject constructor(
    private val api: MarvelApi,
    private val database: MarvelDatabase,
    private val clock: Clock = Clock.systemUTC()
) : ComicsRepository {

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }

    @OptIn(ExperimentalPagingApi::class)
    override fun comics(): Flow<PagingData<ComicSummary>> {
        return Pager(
            config = PagingConfig(
                pageSize = DEFAULT_PAGE_SIZE,
                enablePlaceholders = false
            ),
            remoteMediator = ComicsRemoteMediator(
                api = api,
                database = database,
                clock = clock
            ),
            pagingSourceFactory = { database.comicDao().pagingSource() }
        ).flow.map { pagingData ->
            pagingData.map { it.toSummary() }
        }
    }

    override suspend fun getComicDetail(id: Int): Outcome<ComicDetail> {
        val cached = database.comicDao().getComicById(id)
        if (cached != null) {
            return Outcome.Success(cached.toDetail())
        }

        val response = api.getComicDetail(id)
        var outcome: Outcome<ComicDetail> = Outcome.Failure(DomainError.Unknown())

        response.onSuccess {
            val comic = data.data.results.firstOrNull()
            outcome = if (comic != null) {
                Outcome.Success(comic.toDetail())
            } else {
                Outcome.Failure(DomainError.NotFound)
            }
        }.onError {
            val code = statusCode.code
            val domainError = when (code) {
                404 -> DomainError.NotFound
                401, 403 -> DomainError.InvalidKey
                429 -> DomainError.RateLimited
                in 500..599 -> DomainError.Server(code)
                else -> DomainError.Server(code)
            }
            outcome = Outcome.Failure(domainError)
        }.onException {
            val domainError = when (throwable) {
                is SocketTimeoutException -> DomainError.Timeout
                is IOException -> DomainError.NoConnection
                else -> DomainError.Unknown(throwable)
            }
            outcome = Outcome.Failure(domainError)
        }

        return outcome
    }
}
