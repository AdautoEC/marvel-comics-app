package com.adauto.marvel.core.database

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.adauto.marvel.core.database.dao.ComicDao
import com.adauto.marvel.core.database.dao.RemoteKeyDao
import com.adauto.marvel.core.database.entity.ComicEntity
import com.adauto.marvel.core.database.entity.RemoteKeyEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ComicDaoTest {
    private lateinit var db: MarvelDatabase
    private lateinit var comicDao: ComicDao
    private lateinit var remoteKeyDao: RemoteKeyDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MarvelDatabase::class.java).build()
        comicDao = db.comicDao()
        remoteKeyDao = db.remoteKeyDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun upsertAndFetchById() = runTest {
        val comic = ComicEntity(
            id = 101,
            position = 0,
            title = "Spider-Man #1",
            issueNumber = 1.0,
            description = "Great power",
            thumbnailPath = "http://marvel.com/spiderman",
            thumbnailExt = "jpg",
            pageCount = 32,
            priceCents = 399,
            onsaleDate = "2026-01-01",
            creatorsJson = "Stan Lee",
            charactersJson = "Peter Parker",
            fetchedAt = 1000L
        )
        comicDao.upsert(listOf(comic))
        val fetched = comicDao.getComicById(101)
        assertEquals("Spider-Man #1", fetched?.title)
        assertEquals(1000L, comicDao.lastFetchedAt())
    }

    @Test
    fun pagingSourceReturnsComicsOrderedByPosition() = runTest {
        val comic2 = ComicEntity(
            id = 202,
            position = 1,
            title = "Spider-Man #2",
            issueNumber = 2.0,
            description = null,
            thumbnailPath = null,
            thumbnailExt = null,
            pageCount = 32,
            priceCents = 399,
            onsaleDate = null,
            creatorsJson = null,
            charactersJson = null,
            fetchedAt = 2000L
        )
        val comic1 = ComicEntity(
            id = 101,
            position = 0,
            title = "Spider-Man #1",
            issueNumber = 1.0,
            description = null,
            thumbnailPath = null,
            thumbnailExt = null,
            pageCount = 32,
            priceCents = 399,
            onsaleDate = null,
            creatorsJson = null,
            charactersJson = null,
            fetchedAt = 1000L
        )
        // Upsert in reverse order to verify order by position ASC
        comicDao.upsert(listOf(comic2, comic1))

        val pagingSource = comicDao.pagingSource()
        val loadResult = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 10,
                placeholdersEnabled = false
            )
        )
        val page = loadResult as PagingSource.LoadResult.Page
        assertEquals(2, page.data.size)
        assertEquals(101, page.data[0].id)
        assertEquals(0, page.data[0].position)
        assertEquals(202, page.data[1].id)
        assertEquals(1, page.data[1].position)
    }

    @Test
    fun clearAllRemovesAllComics() = runTest {
        val comic = ComicEntity(
            id = 101,
            position = 0,
            title = "Spider-Man #1",
            issueNumber = 1.0,
            description = null,
            thumbnailPath = null,
            thumbnailExt = null,
            pageCount = 32,
            priceCents = null,
            onsaleDate = null,
            creatorsJson = null,
            charactersJson = null,
            fetchedAt = 1000L
        )
        comicDao.upsert(listOf(comic))
        assertEquals("Spider-Man #1", comicDao.getComicById(101)?.title)

        comicDao.clearAll()
        assertNull(comicDao.getComicById(101))
        assertNull(comicDao.lastFetchedAt())
    }

    @Test
    fun remoteKeyDaoUpsertAndQuery() = runTest {
        val keys = listOf(
            RemoteKeyEntity(comicId = 101, nextOffset = 20),
            RemoteKeyEntity(comicId = 102, nextOffset = 40)
        )
        remoteKeyDao.upsert(keys)

        assertEquals(40, remoteKeyDao.lastNextOffset())

        remoteKeyDao.clearAll()
        assertNull(remoteKeyDao.lastNextOffset())
    }
}
