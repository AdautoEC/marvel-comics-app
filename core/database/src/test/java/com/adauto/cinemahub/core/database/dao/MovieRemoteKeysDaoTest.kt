package com.adauto.cinemahub.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.adauto.cinemahub.core.database.CinemaHubDatabase
import com.adauto.cinemahub.core.database.entity.MovieRemoteKeysEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MovieRemoteKeysDaoTest {
    private lateinit var db: CinemaHubDatabase
    private lateinit var movieRemoteKeysDao: MovieRemoteKeysDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, CinemaHubDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        movieRemoteKeysDao = db.movieRemoteKeysDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndGetKey_storesAndRetrievesKey() = runTest {
        val key = MovieRemoteKeysEntity(
            label = "POPULAR",
            nextPage = 2,
            totalPages = 10,
            updatedAt = 1000L
        )
        movieRemoteKeysDao.insertOrReplace(key)

        val retrieved = movieRemoteKeysDao.getKey("POPULAR")
        assertThat(retrieved).isEqualTo(key)
    }

    @Test
    fun insertOrReplace_updatesExistingKey() = runTest {
        val key1 = MovieRemoteKeysEntity(
            label = "POPULAR",
            nextPage = 2,
            totalPages = 10,
            updatedAt = 1000L
        )
        val key2 = MovieRemoteKeysEntity(
            label = "POPULAR",
            nextPage = 3,
            totalPages = 10,
            updatedAt = 2000L
        )
        movieRemoteKeysDao.insertOrReplace(key1)
        movieRemoteKeysDao.insertOrReplace(key2)

        val retrieved = movieRemoteKeysDao.getKey("POPULAR")
        assertThat(retrieved).isEqualTo(key2)
    }

    @Test
    fun deleteKey_removesSpecificLabel() = runTest {
        val popular = MovieRemoteKeysEntity(label = "POPULAR", nextPage = 2, totalPages = 10, updatedAt = 1000L)
        val topRated = MovieRemoteKeysEntity(label = "TOP_RATED", nextPage = 3, totalPages = 8, updatedAt = 1000L)

        movieRemoteKeysDao.insertOrReplace(popular)
        movieRemoteKeysDao.insertOrReplace(topRated)

        movieRemoteKeysDao.deleteKey("POPULAR")

        assertThat(movieRemoteKeysDao.getKey("POPULAR")).isNull()
        assertThat(movieRemoteKeysDao.getKey("TOP_RATED")).isNotNull()
    }

    @Test
    fun clearAll_removesAllKeys() = runTest {
        val popular = MovieRemoteKeysEntity(label = "POPULAR", nextPage = 2, totalPages = 10, updatedAt = 1000L)
        val topRated = MovieRemoteKeysEntity(label = "TOP_RATED", nextPage = 3, totalPages = 8, updatedAt = 1000L)

        movieRemoteKeysDao.insertOrReplace(popular)
        movieRemoteKeysDao.insertOrReplace(topRated)

        movieRemoteKeysDao.clearAll()

        assertThat(movieRemoteKeysDao.getKey("POPULAR")).isNull()
        assertThat(movieRemoteKeysDao.getKey("TOP_RATED")).isNull()
    }
}
