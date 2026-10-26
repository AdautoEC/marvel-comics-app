package com.adauto.cinemahub.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.adauto.cinemahub.core.database.CinemaHubDatabase
import com.adauto.cinemahub.core.database.entity.MovieEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MovieDaoCompositeKeyTest {
    private lateinit var db: CinemaHubDatabase
    private lateinit var movieDao: MovieDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, CinemaHubDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        movieDao = db.movieDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun movieCanCoexistInMultipleCategoriesWithoutOverwriting() = runTest {
        val popularMovie = MovieEntity(
            id = 550,
            category = "POPULAR",
            position = 1,
            title = "Fight Club",
            overview = "Overview",
            posterPath = "/poster.jpg",
            backdropPath = null,
            voteAverage = 8.4,
            releaseDate = "1999-10-15",
            fetchedAt = 1000L
        )
        val topRatedMovie = popularMovie.copy(
            category = "TOP_RATED",
            position = 5
        )

        movieDao.upsertAll(listOf(popularMovie))
        movieDao.upsertAll(listOf(topRatedMovie))

        val popularLastPos = movieDao.getLastPosition("POPULAR")
        val topRatedLastPos = movieDao.getLastPosition("TOP_RATED")

        assertThat(popularLastPos).isEqualTo(1)
        assertThat(topRatedLastPos).isEqualTo(5)
    }
}
