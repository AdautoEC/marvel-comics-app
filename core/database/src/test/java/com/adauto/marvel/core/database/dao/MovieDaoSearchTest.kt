package com.adauto.marvel.core.database.dao

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.adauto.marvel.core.database.MarvelDatabase
import com.adauto.marvel.core.database.entity.MovieEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MovieDaoSearchTest {

    private lateinit var db: MarvelDatabase
    private lateinit var movieDao: MovieDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MarvelDatabase::class.java)
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .allowMainThreadQueries()
            .build()
        movieDao = db.movieDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    private fun createMovie(id: Int, title: String, category: String = "POPULAR", position: Int = 0): MovieEntity {
        return MovieEntity(
            id = id,
            category = category,
            position = position,
            title = title,
            overview = "Overview $id",
            posterPath = "/poster$id.jpg",
            backdropPath = "/backdrop$id.jpg",
            voteAverage = 7.5,
            releaseDate = "2024-01-01",
            fetchedAt = 1000L
        )
    }

    @Test
    fun searchPagingSource_filtersByCategoryAndTitleQueryOrderedByPosition() = runTest {
        val movies = listOf(
            createMovie(id = 1, title = "Spider-Man: No Way Home", category = "POPULAR", position = 0),
            createMovie(id = 2, title = "Iron Man", category = "POPULAR", position = 1),
            createMovie(id = 3, title = "The Amazing Spider-Man", category = "POPULAR", position = 2),
            createMovie(id = 4, title = "Spider-Man: Into the Spider-Verse", category = "TOP_RATED", position = 0)
        )
        movieDao.upsertAll(movies)

        val pagingSource = movieDao.searchPagingSource(category = "POPULAR", query = "Spider")
        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 10, placeholdersEnabled = false)
        ) as PagingSource.LoadResult.Page<Int, com.adauto.marvel.core.database.model.MovieWithUserData>

        assertThat(result.data).hasSize(2)
        assertThat(result.data.map { it.movie.id }).containsExactly(1, 3).inOrder()
        assertThat(result.nextKey).isNull()
    }
}
