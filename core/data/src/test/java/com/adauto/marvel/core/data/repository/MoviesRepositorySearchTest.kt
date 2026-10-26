package com.adauto.marvel.core.data.repository

import androidx.paging.PagingSource
import com.adauto.marvel.core.data.sync.SyncCoordinator
import com.adauto.marvel.core.database.MarvelDatabase
import com.adauto.marvel.core.database.dao.FavoriteDao
import com.adauto.marvel.core.database.dao.MovieDao
import com.adauto.marvel.core.database.dao.SyncOutboxDao
import com.adauto.marvel.core.database.dao.WatchlistDao
import com.adauto.marvel.core.database.model.MovieWithUserData
import com.adauto.marvel.core.model.MovieCategory
import com.adauto.marvel.core.model.SessionState
import com.adauto.marvel.core.network.api.TmdbApi
import com.adauto.marvel.core.network.security.session.SessionManager
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class MoviesRepositorySearchTest {

    private val api: TmdbApi = mockk(relaxed = true)
    private val database: MarvelDatabase = mockk()
    private val favoriteDao: FavoriteDao = mockk(relaxed = true)
    private val watchlistDao: WatchlistDao = mockk(relaxed = true)
    private val movieDao: MovieDao = mockk(relaxed = true)
    private val syncOutboxDao: SyncOutboxDao = mockk(relaxed = true)
    private val sessionManager: SessionManager = mockk(relaxed = true)
    private val workManager: androidx.work.WorkManager = mockk(relaxed = true)
    private val syncCoordinator = SyncCoordinator()
    private val fixedClock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC)

    private val sessionStateFlow = MutableStateFlow<SessionState>(SessionState.Guest)
    private lateinit var repository: MoviesRepositoryImpl

    @Before
    fun setUp() {
        every { database.movieDao() } returns movieDao
        every { database.favoriteDao() } returns favoriteDao
        every { database.watchlistDao() } returns watchlistDao
        every { database.syncOutboxDao() } returns syncOutboxDao
        every { sessionManager.sessionState } returns sessionStateFlow

        repository = MoviesRepositoryImpl(
            api = api,
            database = database,
            sessionManager = sessionManager,
            workManager = workManager,
            syncCoordinator = syncCoordinator,
            externalScope = TestScope(),
            clock = fixedClock
        )
    }

    @Test
    fun movies_withSearchQuery_invokesSearchPagingSourceOnMovieDao() = runTest {
        val mockPagingSource: PagingSource<Int, MovieWithUserData> = mockk(relaxed = true)
        every { movieDao.searchPagingSource("POPULAR", "Spider") } returns mockPagingSource

        val pagingDataFlow = repository.movies(MovieCategory.POPULAR, "Spider")
        val pagingData = pagingDataFlow.first()

        assertThat(pagingData).isNotNull()
        io.mockk.verify { movieDao.searchPagingSource("POPULAR", "Spider") }
    }
}
