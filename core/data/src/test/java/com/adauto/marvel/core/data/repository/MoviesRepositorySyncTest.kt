package com.adauto.marvel.core.data.repository

import com.adauto.marvel.core.database.MarvelDatabase
import com.adauto.marvel.core.database.dao.FavoriteDao
import com.adauto.marvel.core.database.dao.MovieDao
import com.adauto.marvel.core.database.dao.WatchedDao
import com.adauto.marvel.core.database.entity.FavoriteMovieEntity
import com.adauto.marvel.core.database.entity.WatchedMovieEntity
import com.adauto.marvel.core.model.MovieSummary
import com.adauto.marvel.core.model.Outcome
import com.adauto.marvel.core.model.SessionState
import com.adauto.marvel.core.network.api.TmdbApi
import com.adauto.marvel.core.network.model.TmdbActionResponseDto
import com.adauto.marvel.core.network.model.TmdbFavoriteRequestDto
import com.adauto.marvel.core.network.model.TmdbMovieDto
import com.adauto.marvel.core.network.model.TmdbPageResponseDto
import com.adauto.marvel.core.network.model.TmdbWatchlistRequestDto
import com.adauto.marvel.core.network.security.session.SessionManager
import com.google.common.truth.Truth.assertThat
import com.skydoves.sandwich.ApiResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class MoviesRepositorySyncTest {

    private val api: TmdbApi = mockk()
    private val database: MarvelDatabase = mockk()
    private val favoriteDao: FavoriteDao = mockk(relaxed = true)
    private val watchedDao: WatchedDao = mockk(relaxed = true)
    private val movieDao: MovieDao = mockk(relaxed = true)
    private val sessionManager: SessionManager = mockk()
    private val fixedClock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC)

    private val sessionStateFlow = MutableStateFlow<SessionState>(SessionState.Guest)

    private lateinit var repository: MoviesRepositoryImpl

    private val sampleMovie = MovieSummary(
        id = 101,
        title = "Iron Man",
        posterUrl = "https://image.tmdb.org/t/p/w500/ironman.jpg",
        backdropUrl = null,
        voteAverage = 8.2,
        releaseDate = "2008-05-02"
    )

    @Before
    fun setUp() {
        every { database.favoriteDao() } returns favoriteDao
        every { database.watchedDao() } returns watchedDao
        every { database.movieDao() } returns movieDao
        every { sessionManager.sessionState } returns sessionStateFlow

        repository = MoviesRepositoryImpl(
            api = api,
            database = database,
            sessionManager = sessionManager,
            clock = fixedClock
        )
    }

    // region toggleFavorite tests

    @Test
    fun toggleFavorite_when_guest_updates_room_only_without_network_calls() = runTest {
        sessionStateFlow.value = SessionState.Guest
        every { favoriteDao.isFavorite(101) } returns flowOf(false)

        repository.toggleFavorite(sampleMovie)

        coVerify(exactly = 1) {
            favoriteDao.upsert(
                FavoriteMovieEntity(
                    movieId = 101,
                    title = "Iron Man",
                    posterPath = "/ironman.jpg",
                    voteAverage = 8.2,
                    releaseDate = "2008-05-02",
                    addedAt = 1_700_000_000_000L
                )
            )
        }
        coVerify(exactly = 0) { api.markAsFavorite(any(), any(), any()) }
    }

    @Test
    fun toggleFavorite_when_authenticated_adds_favorite_in_room_and_calls_tmdb_api() = runTest {
        sessionStateFlow.value = SessionState.Authenticated(
            accountId = 42,
            username = "stark",
            sessionId = "sess_iron"
        )
        every { favoriteDao.isFavorite(101) } returns flowOf(false)
        coEvery {
            api.markAsFavorite(any(), any(), any())
        } returns ApiResponse.Success(TmdbActionResponseDto(1, "Success", true))

        repository.toggleFavorite(sampleMovie)

        coVerify(exactly = 1) {
            favoriteDao.upsert(
                FavoriteMovieEntity(
                    movieId = 101,
                    title = "Iron Man",
                    posterPath = "/ironman.jpg",
                    voteAverage = 8.2,
                    releaseDate = "2008-05-02",
                    addedAt = 1_700_000_000_000L
                )
            )
        }
        coVerify(exactly = 1) {
            api.markAsFavorite(
                accountId = 42,
                sessionId = "sess_iron",
                body = TmdbFavoriteRequestDto(media_id = 101, favorite = true)
            )
        }
    }

    @Test
    fun toggleFavorite_when_authenticated_and_already_favorite_deletes_from_room_and_calls_tmdb_api_with_false() = runTest {
        sessionStateFlow.value = SessionState.Authenticated(
            accountId = 42,
            username = "stark",
            sessionId = "sess_iron"
        )
        every { favoriteDao.isFavorite(101) } returns flowOf(true)
        coEvery {
            api.markAsFavorite(any(), any(), any())
        } returns ApiResponse.Success(TmdbActionResponseDto(1, "Success", true))

        repository.toggleFavorite(sampleMovie)

        coVerify(exactly = 1) { favoriteDao.delete(101) }
        coVerify(exactly = 1) {
            api.markAsFavorite(
                accountId = 42,
                sessionId = "sess_iron",
                body = TmdbFavoriteRequestDto(media_id = 101, favorite = false)
            )
        }
    }

    // endregion

    // region toggleWatchlist & toggleWatched tests

    @Test
    fun toggleWatchlist_when_guest_updates_room_only_without_network_calls() = runTest {
        sessionStateFlow.value = SessionState.Guest
        every { watchedDao.isWatched(101) } returns flowOf(false)

        repository.toggleWatchlist(sampleMovie)

        coVerify(exactly = 1) {
            watchedDao.upsert(
                WatchedMovieEntity(
                    movieId = 101,
                    title = "Iron Man",
                    posterPath = "/ironman.jpg",
                    voteAverage = 8.2,
                    releaseDate = "2008-05-02",
                    watchedAt = 1_700_000_000_000L
                )
            )
        }
        coVerify(exactly = 0) { api.addToWatchlist(any(), any(), any()) }
    }

    @Test
    fun toggleWatchlist_when_authenticated_adds_in_room_and_calls_tmdb_api() = runTest {
        sessionStateFlow.value = SessionState.Authenticated(
            accountId = 42,
            username = "stark",
            sessionId = "sess_iron"
        )
        every { watchedDao.isWatched(101) } returns flowOf(false)
        coEvery {
            api.addToWatchlist(any(), any(), any())
        } returns ApiResponse.Success(TmdbActionResponseDto(1, "Success", true))

        repository.toggleWatchlist(sampleMovie)

        coVerify(exactly = 1) {
            watchedDao.upsert(
                WatchedMovieEntity(
                    movieId = 101,
                    title = "Iron Man",
                    posterPath = "/ironman.jpg",
                    voteAverage = 8.2,
                    releaseDate = "2008-05-02",
                    watchedAt = 1_700_000_000_000L
                )
            )
        }
        coVerify(exactly = 1) {
            api.addToWatchlist(
                accountId = 42,
                sessionId = "sess_iron",
                body = TmdbWatchlistRequestDto(media_id = 101, watchlist = true)
            )
        }
    }

    @Test
    fun toggleWatchlist_when_authenticated_and_already_in_watchlist_deletes_from_room_and_calls_tmdb_api_with_false() = runTest {
        sessionStateFlow.value = SessionState.Authenticated(
            accountId = 42,
            username = "stark",
            sessionId = "sess_iron"
        )
        every { watchedDao.isWatched(101) } returns flowOf(true)
        coEvery {
            api.addToWatchlist(any(), any(), any())
        } returns ApiResponse.Success(TmdbActionResponseDto(1, "Success", true))

        repository.toggleWatchlist(sampleMovie)

        coVerify(exactly = 1) { watchedDao.delete(101) }
        coVerify(exactly = 1) {
            api.addToWatchlist(
                accountId = 42,
                sessionId = "sess_iron",
                body = TmdbWatchlistRequestDto(media_id = 101, watchlist = false)
            )
        }
    }

    @Test
    fun toggleWatched_delegates_to_toggleWatchlist() = runTest {
        sessionStateFlow.value = SessionState.Guest
        every { watchedDao.isWatched(101) } returns flowOf(true)

        repository.toggleWatched(sampleMovie)

        coVerify(exactly = 1) { watchedDao.delete(101) }
    }

    // endregion

    // region syncCloudWithLocal tests

    @Test
    fun syncCloudWithLocal_when_guest_returns_success_without_network_calls() = runTest {
        sessionStateFlow.value = SessionState.Guest

        val result = repository.syncCloudWithLocal()

        assertThat(result).isInstanceOf(Outcome.Success::class.java)
        coVerify(exactly = 0) { api.markAsFavorite(any(), any(), any()) }
        coVerify(exactly = 0) { api.addToWatchlist(any(), any(), any()) }
        coVerify(exactly = 0) { api.getFavoriteMovies(any(), any(), any()) }
        coVerify(exactly = 0) { api.getWatchlistMovies(any(), any(), any()) }
    }

    @Test
    fun syncCloudWithLocal_when_authenticated_pushes_local_items_and_pulls_remote_items() = runTest {
        sessionStateFlow.value = SessionState.Authenticated(
            accountId = 42,
            username = "stark",
            sessionId = "sess_iron"
        )

        val localFav = FavoriteMovieEntity(
            movieId = 10,
            title = "Movie 10",
            posterPath = "/10.jpg",
            voteAverage = 7.5,
            releaseDate = "2020-01-01",
            addedAt = 1000L
        )
        val localWatch = WatchedMovieEntity(
            movieId = 20,
            title = "Movie 20",
            posterPath = "/20.jpg",
            voteAverage = 8.0,
            releaseDate = "2021-01-01",
            watchedAt = 2000L
        )
        every { favoriteDao.getAllFavorites() } returns flowOf(listOf(localFav))
        every { watchedDao.getAllWatched() } returns flowOf(listOf(localWatch))

        coEvery {
            api.markAsFavorite(any(), any(), any())
        } returns ApiResponse.Success(TmdbActionResponseDto(1, "Success", true))
        coEvery {
            api.addToWatchlist(any(), any(), any())
        } returns ApiResponse.Success(TmdbActionResponseDto(1, "Success", true))

        val remoteFav = TmdbMovieDto(
            id = 30,
            title = "Remote Fav",
            overview = "Overview 30",
            posterPath = "/30.jpg",
            backdropPath = null,
            voteAverage = 9.0,
            releaseDate = "2022-01-01"
        )
        val remoteWatch = TmdbMovieDto(
            id = 40,
            title = "Remote Watch",
            overview = "Overview 40",
            posterPath = "/40.jpg",
            backdropPath = null,
            voteAverage = 6.5,
            releaseDate = "2023-01-01"
        )
        coEvery {
            api.getFavoriteMovies(42, "sess_iron", 1)
        } returns ApiResponse.Success(
            TmdbPageResponseDto(page = 1, results = listOf(remoteFav), totalPages = 1, totalResults = 1)
        )
        coEvery {
            api.getWatchlistMovies(42, "sess_iron", 1)
        } returns ApiResponse.Success(
            TmdbPageResponseDto(page = 1, results = listOf(remoteWatch), totalPages = 1, totalResults = 1)
        )

        val result = repository.syncCloudWithLocal()

        assertThat(result).isInstanceOf(Outcome.Success::class.java)

        // Verifies push
        coVerify(exactly = 1) {
            api.markAsFavorite(
                accountId = 42,
                sessionId = "sess_iron",
                body = TmdbFavoriteRequestDto(media_id = 10, favorite = true)
            )
        }
        coVerify(exactly = 1) {
            api.addToWatchlist(
                accountId = 42,
                sessionId = "sess_iron",
                body = TmdbWatchlistRequestDto(media_id = 20, watchlist = true)
            )
        }

        // Verifies pull into Room
        coVerify(exactly = 1) {
            favoriteDao.upsert(
                FavoriteMovieEntity(
                    movieId = 30,
                    title = "Remote Fav",
                    posterPath = "/30.jpg",
                    voteAverage = 9.0,
                    releaseDate = "2022-01-01",
                    addedAt = 1_700_000_000_000L
                )
            )
        }
        coVerify(exactly = 1) {
            watchedDao.upsert(
                WatchedMovieEntity(
                    movieId = 40,
                    title = "Remote Watch",
                    posterPath = "/40.jpg",
                    voteAverage = 6.5,
                    releaseDate = "2023-01-01",
                    watchedAt = 1_700_000_000_000L
                )
            )
        }
    }

    @Test
    fun syncCloudWithLocal_handles_network_exceptions_gracefully() = runTest {
        sessionStateFlow.value = SessionState.Authenticated(
            accountId = 42,
            username = "stark",
            sessionId = "sess_iron"
        )
        every { favoriteDao.getAllFavorites() } returns flowOf(emptyList())
        every { watchedDao.getAllWatched() } returns flowOf(emptyList())

        coEvery {
            api.getFavoriteMovies(any(), any(), any())
        } returns ApiResponse.Failure.Exception(IOException("No connection"))
        coEvery {
            api.getWatchlistMovies(any(), any(), any())
        } returns ApiResponse.Failure.Exception(IOException("No connection"))

        val result = repository.syncCloudWithLocal()

        assertThat(result).isNotNull()
    }

    // endregion
}
