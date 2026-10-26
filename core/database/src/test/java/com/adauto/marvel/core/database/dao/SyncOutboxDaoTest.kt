package com.adauto.marvel.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.adauto.marvel.core.database.MarvelDatabase
import com.adauto.marvel.core.database.entity.SyncOutboxEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncOutboxDaoTest {
    private lateinit var db: MarvelDatabase
    private lateinit var syncOutboxDao: SyncOutboxDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MarvelDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        syncOutboxDao = db.syncOutboxDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun upsertAndFind_storesAndRetrievesEntity() = runTest {
        val entity = SyncOutboxEntity(
            movieId = 10,
            target = SyncOutboxEntity.TARGET_FAVORITE,
            desired = true,
            attempts = 0,
            lastError = null,
            version = 1
        )
        syncOutboxDao.upsert(entity)

        val retrieved = syncOutboxDao.find(10, SyncOutboxEntity.TARGET_FAVORITE)
        assertThat(retrieved).isEqualTo(entity)
    }

    @Test
    fun deleteVersion_deletesOnlyWhenVersionMatches() = runTest {
        val entity = SyncOutboxEntity(
            movieId = 20,
            target = SyncOutboxEntity.TARGET_WATCHLIST,
            desired = true,
            attempts = 1,
            version = 2
        )
        syncOutboxDao.upsert(entity)

        // Wrong version: should return 0 deleted rows
        val deletedWrongVersion = syncOutboxDao.deleteVersion(20, SyncOutboxEntity.TARGET_WATCHLIST, 1)
        assertThat(deletedWrongVersion).isEqualTo(0)
        assertThat(syncOutboxDao.find(20, SyncOutboxEntity.TARGET_WATCHLIST)).isNotNull()

        // Correct version: should return 1 deleted row
        val deletedCorrectVersion = syncOutboxDao.deleteVersion(20, SyncOutboxEntity.TARGET_WATCHLIST, 2)
        assertThat(deletedCorrectVersion).isEqualTo(1)
        assertThat(syncOutboxDao.find(20, SyncOutboxEntity.TARGET_WATCHLIST)).isNull()
    }

    @Test
    fun updateFailure_incrementsAttemptsAndSetsErrorWhenVersionMatches() = runTest {
        val entity = SyncOutboxEntity(
            movieId = 30,
            target = SyncOutboxEntity.TARGET_FAVORITE,
            desired = false,
            attempts = 0,
            lastError = null,
            version = 1
        )
        syncOutboxDao.upsert(entity)

        val updated = syncOutboxDao.updateFailure(30, SyncOutboxEntity.TARGET_FAVORITE, 1, "Network timeout")
        assertThat(updated).isEqualTo(1)

        val modified = syncOutboxDao.find(30, SyncOutboxEntity.TARGET_FAVORITE)
        assertThat(modified?.attempts).isEqualTo(1)
        assertThat(modified?.lastError).isEqualTo("Network timeout")
        assertThat(modified?.version).isEqualTo(1)
    }

    @Test
    fun getAllPending_ordersByCreatedAtAscendingRegardlessOfVersion() = runTest {
        val first = SyncOutboxEntity(
            movieId = 1,
            target = SyncOutboxEntity.TARGET_FAVORITE,
            desired = true,
            version = 5,
            createdAt = 1000L
        )
        val second = SyncOutboxEntity(
            movieId = 2,
            target = SyncOutboxEntity.TARGET_WATCHLIST,
            desired = true,
            version = 1,
            createdAt = 2000L
        )
        val third = SyncOutboxEntity(
            movieId = 3,
            target = SyncOutboxEntity.TARGET_FAVORITE,
            desired = false,
            version = 2,
            createdAt = 3000L
        )

        syncOutboxDao.upsert(second)
        syncOutboxDao.upsert(third)
        syncOutboxDao.upsert(first)

        val all = syncOutboxDao.getAllPending()
        assertThat(all.map { it.movieId }).containsExactly(1, 2, 3).inOrder()
    }

    @Test
    fun getPendingMovieIds_filtersByTarget() = runTest {
        syncOutboxDao.upsert(
            SyncOutboxEntity(movieId = 1, target = SyncOutboxEntity.TARGET_FAVORITE, desired = true)
        )
        syncOutboxDao.upsert(
            SyncOutboxEntity(movieId = 2, target = SyncOutboxEntity.TARGET_WATCHLIST, desired = true)
        )
        syncOutboxDao.upsert(
            SyncOutboxEntity(movieId = 3, target = SyncOutboxEntity.TARGET_FAVORITE, desired = false)
        )

        val favIds = syncOutboxDao.getPendingMovieIds(SyncOutboxEntity.TARGET_FAVORITE)
        assertThat(favIds).containsExactly(1, 3)

        val watchedIds = syncOutboxDao.getPendingMovieIds(SyncOutboxEntity.TARGET_WATCHLIST)
        assertThat(watchedIds).containsExactly(2)
    }

    @Test
    fun clearAll_removesAllEntries() = runTest {
        syncOutboxDao.upsert(
            SyncOutboxEntity(movieId = 1, target = SyncOutboxEntity.TARGET_FAVORITE, desired = true)
        )
        syncOutboxDao.upsert(
            SyncOutboxEntity(movieId = 2, target = SyncOutboxEntity.TARGET_WATCHLIST, desired = true)
        )

        assertThat(syncOutboxDao.getAllPending()).hasSize(2)
        syncOutboxDao.clearAll()
        assertThat(syncOutboxDao.getAllPending()).isEmpty()
    }
}
