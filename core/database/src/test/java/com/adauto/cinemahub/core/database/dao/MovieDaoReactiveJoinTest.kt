package com.adauto.cinemahub.core.database.dao

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.adauto.cinemahub.core.database.CinemaHubDatabase
import com.adauto.cinemahub.core.database.entity.FavoriteMovieEntity
import com.adauto.cinemahub.core.database.entity.MovieEntity
import com.adauto.cinemahub.core.database.entity.WatchlistMovieEntity
import com.adauto.cinemahub.core.database.model.MovieWithUserData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MovieDaoReactiveJoinTest {

    private lateinit var db: CinemaHubDatabase
    private lateinit var movieDao: MovieDao
    private lateinit var favoriteDao: FavoriteDao
    private lateinit var watchlistDao: WatchlistDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, CinemaHubDatabase::class.java)
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .allowMainThreadQueries()
            .build()
        movieDao = db.movieDao()
        favoriteDao = db.favoriteDao()
        watchlistDao = db.watchlistDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    private fun createMovie(id: Int, category: String = "POPULAR", position: Int = 0): MovieEntity {
        return MovieEntity(
            id = id,
            category = category,
            position = position,
            title = "Movie $id",
            overview = "Overview $id",
            posterPath = "/poster$id.jpg",
            backdropPath = "/backdrop$id.jpg",
            voteAverage = 7.5,
            releaseDate = "2024-01-01",
            fetchedAt = 1000L
        )
    }

    @Test
    fun pagingSource_reflectsFavoriteAndWatchedStateViaLeftJoin() = runTest {
        val movie = createMovie(id = 42)
        movieDao.upsertAll(listOf(movie))

        // Initial load: neither favorite nor watched
        val pagingSource1 = movieDao.pagingSource("POPULAR")
        val result1 = pagingSource1.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 10, placeholdersEnabled = false)
        ) as PagingSource.LoadResult.Page
        assertThat(result1.data).hasSize(1)
        val initialItem = result1.data.first()
        assertThat(initialItem.movie.id).isEqualTo(42)
        assertThat(initialItem.isFavorite).isFalse()
        assertThat(initialItem.isInWatchlist).isFalse()

        // Insert favorite: verify new pagingSource reflects isFavorite = true
        favoriteDao.upsert(
            FavoriteMovieEntity(
                movieId = 42,
                title = "Movie 42",
                posterPath = "/poster42.jpg",
                voteAverage = 7.5,
                releaseDate = "2024-01-01",
                addedAt = 2000L
            )
        )

        val pagingSource2 = movieDao.pagingSource("POPULAR")
        val result2 = pagingSource2.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 10, placeholdersEnabled = false)
        ) as PagingSource.LoadResult.Page
        val favoriteItem = result2.data.first()
        assertThat(favoriteItem.isFavorite).isTrue()
        assertThat(favoriteItem.isInWatchlist).isFalse()

        // Insert watchlist: verify new pagingSource reflects both isFavorite = true and isInWatchlist = true
        watchlistDao.upsert(
            WatchlistMovieEntity(
                movieId = 42,
                title = "Movie 42",
                posterPath = "/poster42.jpg",
                voteAverage = 7.5,
                releaseDate = "2024-01-01",
                watchedAt = 3000L
            )
        )

        val pagingSource3 = movieDao.pagingSource("POPULAR")
        val result3 = pagingSource3.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 10, placeholdersEnabled = false)
        ) as PagingSource.LoadResult.Page
        val bothItem = result3.data.first()
        assertThat(bothItem.isFavorite).isTrue()
        assertThat(bothItem.isInWatchlist).isTrue()

        // Delete favorite: verify isFavorite reverts to false
        favoriteDao.delete(42)

        val pagingSource4 = movieDao.pagingSource("POPULAR")
        val result4 = pagingSource4.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 10, placeholdersEnabled = false)
        ) as PagingSource.LoadResult.Page
        val revertedItem = result4.data.first()
        assertThat(revertedItem.isFavorite).isFalse()
        assertThat(revertedItem.isInWatchlist).isTrue()
    }

    @Test
    fun pagingSource_isInvalidatedWhenFavoriteOrWatchedChanges() = runTest {
        val movie = createMovie(id = 99)
        movieDao.upsertAll(listOf(movie))

        val pagingSource = movieDao.pagingSource("POPULAR")
        // Load to activate invalidation tracking observer in Room LimitOffsetPagingSource
        pagingSource.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 10, placeholdersEnabled = false)
        )
        assertThat(pagingSource.invalid).isFalse()

        var invalidated = false
        pagingSource.registerInvalidatedCallback {
            invalidated = true
        }

        // Toggling favorite should invalidate the Room PagingSource automatically
        favoriteDao.upsert(
            FavoriteMovieEntity(
                movieId = 99,
                title = "Movie 99",
                posterPath = null,
                voteAverage = 8.0,
                releaseDate = "2024-01-01",
                addedAt = 1000L
            )
        )

        // In Robolectric/Room, trigger invalidation tracker refresh to execute table observers
        db.invalidationTracker.refreshVersionsSync()

        assertThat(pagingSource.invalid).isTrue()
        assertThat(invalidated).isTrue()
    }
}
