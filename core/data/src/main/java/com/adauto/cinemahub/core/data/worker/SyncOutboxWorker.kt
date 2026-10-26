package com.adauto.cinemahub.core.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.adauto.cinemahub.core.data.sync.SyncCoordinator
import com.adauto.cinemahub.core.data.sync.SyncEventBus
import com.adauto.cinemahub.core.data.sync.SyncFailureEvent
import com.adauto.cinemahub.core.database.CinemaHubDatabase
import com.adauto.cinemahub.core.database.entity.SyncOutboxEntity
import com.adauto.cinemahub.core.model.SessionState
import com.adauto.cinemahub.core.network.api.TmdbApi
import com.adauto.cinemahub.core.network.model.TmdbActionResponseDto
import com.adauto.cinemahub.core.network.model.TmdbFavoriteRequestDto
import com.adauto.cinemahub.core.network.model.TmdbWatchlistRequestDto
import com.adauto.cinemahub.core.network.security.session.SessionManager
import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.retrofit.statusCode
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock
import retrofit2.Response
import java.io.IOException

@HiltWorker
class SyncOutboxWorker @Suppress("LongParameterList") @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val database: CinemaHubDatabase,
    private val syncCoordinator: SyncCoordinator,
    private val syncEventBus: SyncEventBus,
    private val api: TmdbApi,
    private val sessionManager: SessionManager
) : CoroutineWorker(appContext, workerParams) {

    internal var delayProvider: suspend (Long) -> Unit = { delay(it) }

    override suspend fun doWork(): Result = syncCoordinator.mutex.withLock {
        val session = sessionManager.awaitInitialSession()
        if (session !is SessionState.Authenticated) {
            return@withLock Result.success()
        }

        var workerResult: Result? = null
        while (workerResult == null) {
            val pending = database.syncOutboxDao().getAllPending()
            if (pending.isEmpty()) {
                workerResult = Result.success()
                break
            }

            for (item in pending) {
                val itemResult = processItem(item, session)
                if (itemResult != null) {
                    workerResult = itemResult
                    break
                }
            }
        }
        workerResult ?: Result.success()
    }

    private suspend fun processItem(
        item: SyncOutboxEntity,
        session: SessionState.Authenticated
    ): Result? {
        val capturedVersion = item.version
        var apiResponse = executeApiCall(item, session) ?: return handlePermanentFailure(
            item = item,
            capturedVersion = capturedVersion,
            errorMsg = "Unknown target: ${item.target}"
        )

        if (apiResponse is ApiResponse.Failure.Error && apiResponse.statusCode.code == HTTP_TOO_MANY_REQUESTS) {
            val retrofitResponse = apiResponse.payload as? Response<*>
            val retryAfterHeader = retrofitResponse?.headers()?.get(HEADER_RETRY_AFTER)
            val retryDelaySeconds = retryAfterHeader?.toLongOrNull() ?: DEFAULT_RETRY_AFTER_SECONDS
            val retryDelayMs = retryDelaySeconds * SECONDS_TO_MILLIS
            if (retryDelayMs <= MAX_IN_PASS_DELAY_MS) {
                delayProvider(retryDelayMs)
                val retriedResponse = executeApiCall(item, session)
                if (retriedResponse != null) {
                    apiResponse = retriedResponse
                }
            }
        }

        return when (apiResponse) {
            is ApiResponse.Success -> {
                database.syncOutboxDao().deleteVersion(item.movieId, item.target, capturedVersion)
                null
            }
            is ApiResponse.Failure.Error -> handleHttpError(item, capturedVersion, apiResponse)
            is ApiResponse.Failure.Exception -> handleExceptionError(item, capturedVersion, apiResponse)
        }
    }

    private suspend fun executeApiCall(
        item: SyncOutboxEntity,
        session: SessionState.Authenticated
    ): ApiResponse<TmdbActionResponseDto>? {
        return try {
            when (item.target) {
                TARGET_FAVORITE -> api.markAsFavorite(
                    accountId = session.accountId,
                    sessionId = session.sessionId,
                    body = TmdbFavoriteRequestDto(
                        media_id = item.movieId,
                        favorite = item.desired
                    )
                )
                TARGET_WATCHLIST -> api.addToWatchlist(
                    accountId = session.accountId,
                    sessionId = session.sessionId,
                    body = TmdbWatchlistRequestDto(
                        media_id = item.movieId,
                        watchlist = item.desired
                    )
                )
                else -> null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            ApiResponse.Failure.Exception(e)
        }
    }

    private suspend fun handleHttpError(
        item: SyncOutboxEntity,
        capturedVersion: Int,
        response: ApiResponse.Failure.Error
    ): Result? {
        val statusCode = response.statusCode.code
        return when {
            statusCode == HTTP_UNAUTHORIZED -> {
                sessionManager.clearSession()
                WorkManager.getInstance(applicationContext).cancelUniqueWork(WORK_NAME)
                Result.failure()
            }
            statusCode == HTTP_NOT_FOUND -> {
                database.syncOutboxDao().deleteVersion(item.movieId, item.target, capturedVersion)
                null
            }
            isTransientError(statusCode) -> {
                val baseMsg = extractErrorMessage(response)
                val errorMsg = if (statusCode == HTTP_TOO_MANY_REQUESTS) {
                    val retrofitResponse = response.payload as? Response<*>
                    val retryAfter = retrofitResponse?.headers()?.get(HEADER_RETRY_AFTER)
                    if (retryAfter != null) "$baseMsg (Retry-After: $retryAfter)" else baseMsg
                } else {
                    baseMsg
                }
                handleTransientOrPermanent(item, capturedVersion, errorMsg)
            }
            else -> {
                val errorMsg = extractErrorMessage(response)
                handlePermanentFailure(item, capturedVersion, errorMsg)
            }
        }
    }

    private suspend fun handleExceptionError(
        item: SyncOutboxEntity,
        capturedVersion: Int,
        response: ApiResponse.Failure.Exception
    ): Result? {
        val errorMsg = response.throwable.message ?: response.throwable.javaClass.simpleName
        return if (response.throwable is IOException) {
            handleTransientOrPermanent(item, capturedVersion, errorMsg)
        } else {
            handlePermanentFailure(item, capturedVersion, errorMsg)
        }
    }

    private suspend fun handleTransientOrPermanent(
        item: SyncOutboxEntity,
        capturedVersion: Int,
        errorMsg: String
    ): Result? {
        val newAttempts = item.attempts + 1
        return if (newAttempts < MAX_ATTEMPTS) {
            database.syncOutboxDao().updateFailure(item.movieId, item.target, capturedVersion, errorMsg)
            Result.retry()
        } else {
            handlePermanentFailure(item, capturedVersion, errorMsg)
        }
    }

    private suspend fun handlePermanentFailure(
        item: SyncOutboxEntity,
        capturedVersion: Int,
        errorMsg: String
    ): Result? {
        val rowsAffected = database.withTransaction {
            val deleted = database.syncOutboxDao().deleteVersion(item.movieId, item.target, capturedVersion)
            if (deleted == 1 && item.desired) {
                when (item.target) {
                    TARGET_FAVORITE -> database.favoriteDao().delete(item.movieId)
                    TARGET_WATCHLIST -> database.watchlistDao().delete(item.movieId)
                }
            }
            deleted
        }

        if (rowsAffected == 1) {
            syncEventBus.emitFailure(SyncFailureEvent(item.movieId, item.target, errorMsg))
            if (!item.desired) {
                syncCoordinator.requestPull()
            }
        }

        return null
    }

    private fun isTransientError(statusCode: Int): Boolean {
        return statusCode == HTTP_TOO_MANY_REQUESTS || statusCode in HTTP_SERVER_ERROR_MIN..HTTP_SERVER_ERROR_MAX
    }

    private fun extractErrorMessage(response: ApiResponse.Failure.Error): String {
        val retrofitResponse = response.payload as? Response<*>
        val rawMsg = retrofitResponse?.message()?.takeIf { it.isNotBlank() }
        return rawMsg ?: "HTTP ${response.statusCode.code}"
    }

    companion object {
        const val WORK_NAME = "sync_outbox_work"
        const val MAX_ATTEMPTS = 5
        const val TARGET_FAVORITE = SyncOutboxEntity.TARGET_FAVORITE
        const val TARGET_WATCHLIST = SyncOutboxEntity.TARGET_WATCHLIST

        private const val HEADER_RETRY_AFTER = "Retry-After"
        private const val SECONDS_TO_MILLIS = 1000L
        private const val DEFAULT_RETRY_AFTER_SECONDS = 5L
        private const val MAX_IN_PASS_DELAY_MS = 10_000L
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_SERVER_ERROR_MIN = 500
        private const val HTTP_SERVER_ERROR_MAX = 599
    }
}
