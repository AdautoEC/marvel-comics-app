package com.adauto.marvel.core.database.migration

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.util.TableInfo
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.adauto.marvel.core.database.migrations.MIGRATION_2_3
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Migration2To3Test {

    private val dbName = "migration_2_3_test.db"
    private lateinit var context: Context
    private lateinit var openHelper: SupportSQLiteOpenHelper

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        if (::openHelper.isInitialized) {
            openHelper.close()
        }
        context.deleteDatabase(dbName)
    }

    private fun createVersion2Database(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `movies` (
                            `id` INTEGER NOT NULL,
                            `category` TEXT NOT NULL,
                            `position` INTEGER NOT NULL,
                            `title` TEXT NOT NULL,
                            `overview` TEXT NOT NULL,
                            `posterPath` TEXT,
                            `backdropPath` TEXT,
                            `voteAverage` REAL NOT NULL,
                            `releaseDate` TEXT,
                            `runtime` INTEGER,
                            `genresJson` TEXT,
                            `castJson` TEXT,
                            `fetchedAt` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())
                    db.execSQL("""
                        CREATE UNIQUE INDEX IF NOT EXISTS `index_movies_category_position` 
                        ON `movies` (`category`, `position`)
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `favorite_movies` (
                            `movieId` INTEGER NOT NULL,
                            `title` TEXT NOT NULL,
                            `posterPath` TEXT,
                            `voteAverage` REAL NOT NULL,
                            `releaseDate` TEXT,
                            `addedAt` INTEGER NOT NULL,
                            PRIMARY KEY(`movieId`)
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `watched_movies` (
                            `movieId` INTEGER NOT NULL,
                            `title` TEXT NOT NULL,
                            `posterPath` TEXT,
                            `voteAverage` REAL NOT NULL,
                            `releaseDate` TEXT,
                            `watchedAt` INTEGER NOT NULL,
                            PRIMARY KEY(`movieId`)
                        )
                    """.trimIndent())
                }

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) = Unit
            })
            .build()

        openHelper = FrameworkSQLiteOpenHelperFactory().create(config)
        return openHelper.writableDatabase
    }

    @Test
    fun migration2To3_createsCompositePrimaryKeyAndRetainsData() {
        val db = createVersion2Database()

        // Insert version 2 data
        val movieValues = ContentValues().apply {
            put("id", 101)
            put("category", "POPULAR")
            put("position", 1)
            put("title", "Iron Man")
            put("overview", "Tony Stark builds an armored suit.")
            put("posterPath", "/ironman.jpg")
            put("backdropPath", "/ironman_bg.jpg")
            put("voteAverage", 7.9)
            put("releaseDate", "2008-05-02")
            put("runtime", 126)
            put("genresJson", "[\"Action\",\"Sci-Fi\"]")
            put("castJson", "[\"Robert Downey Jr.\"]")
            put("fetchedAt", 1000L)
        }
        db.insert("movies", SQLiteDatabaseConflictAlgorithm.NONE, movieValues)

        val favValues = ContentValues().apply {
            put("movieId", 101)
            put("title", "Iron Man")
            put("posterPath", "/ironman.jpg")
            put("voteAverage", 7.9)
            put("releaseDate", "2008-05-02")
            put("addedAt", 2000L)
        }
        db.insert("favorite_movies", SQLiteDatabaseConflictAlgorithm.NONE, favValues)

        val watchedValues = ContentValues().apply {
            put("movieId", 101)
            put("title", "Iron Man")
            put("posterPath", "/ironman.jpg")
            put("voteAverage", 7.9)
            put("releaseDate", "2008-05-02")
            put("watchedAt", 3000L)
        }
        db.insert("watched_movies", SQLiteDatabaseConflictAlgorithm.NONE, watchedValues)

        // Run Migration 2 -> 3
        MIGRATION_2_3.migrate(db)

        // 1. Verify schema on `movies`
        val moviesTableInfo = TableInfo.read(db, "movies")
        val primaryKeyColumns = moviesTableInfo.columns.values
            .filter { it.isPrimaryKey }
            .map { it.name }
            .toSet()
        assertThat(primaryKeyColumns).containsExactly("id", "category")

        // Verify unique index on (category, position)
        val index = moviesTableInfo.indices?.find { it.name == "index_movies_category_position" }
        assertThat(index).isNotNull()
        assertThat(index?.columns).containsExactly("category", "position").inOrder()
        assertThat(index?.unique).isTrue()

        // 2. Verify existing movie row is retained
        db.query("SELECT * FROM movies WHERE id = 101").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("category"))).isEqualTo("POPULAR")
            assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("position"))).isEqualTo(1)
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("title"))).isEqualTo("Iron Man")
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("overview"))).isEqualTo("Tony Stark builds an armored suit.")
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("posterPath"))).isEqualTo("/ironman.jpg")
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("backdropPath"))).isEqualTo("/ironman_bg.jpg")
            assertThat(cursor.getDouble(cursor.getColumnIndexOrThrow("voteAverage"))).isEqualTo(7.9)
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("releaseDate"))).isEqualTo("2008-05-02")
            assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("runtime"))).isEqualTo(126)
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("genresJson"))).isEqualTo("[\"Action\",\"Sci-Fi\"]")
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("castJson"))).isEqualTo("[\"Robert Downey Jr.\"]")
            assertThat(cursor.getLong(cursor.getColumnIndexOrThrow("fetchedAt"))).isEqualTo(1000L)
        }

        // 3. Verify coexistence: same movie ID can now coexist in a different category
        val topRatedMovie = ContentValues().apply {
            put("id", 101)
            put("category", "TOP_RATED")
            put("position", 1)
            put("title", "Iron Man")
            put("overview", "Tony Stark builds an armored suit.")
            put("posterPath", "/ironman.jpg")
            put("backdropPath", "/ironman_bg.jpg")
            put("voteAverage", 7.9)
            put("releaseDate", "2008-05-02")
            put("runtime", 126)
            put("genresJson", "[\"Action\",\"Sci-Fi\"]")
            put("castJson", "[\"Robert Downey Jr.\"]")
            put("fetchedAt", 1000L)
        }
        val insertedRowId = db.insert("movies", SQLiteDatabaseConflictAlgorithm.NONE, topRatedMovie)
        assertThat(insertedRowId).isNotEqualTo(-1L)

        db.query("SELECT COUNT(*) FROM movies WHERE id = 101").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getInt(0)).isEqualTo(2)
        }

        // 4. Verify unique constraint: duplicate (category, position) throws SQLiteConstraintException
        val duplicatePositionMovie = ContentValues().apply {
            put("id", 999)
            put("category", "POPULAR")
            put("position", 1) // already used by id=101
            put("title", "Duplicate Position Movie")
            put("overview", "Should fail due to unique index")
            put("voteAverage", 5.0)
            put("fetchedAt", 2000L)
        }
        assertThrows(SQLiteConstraintException::class.java) {
            db.insert("movies", SQLiteDatabaseConflictAlgorithm.FAIL, duplicatePositionMovie)
        }

        // 5. Verify favorite_movies remains intact
        val favTableInfo = TableInfo.read(db, "favorite_movies")
        val favPrimaryKeys = favTableInfo.columns.values
            .filter { it.isPrimaryKey }
            .map { it.name }
            .toSet()
        assertThat(favPrimaryKeys).containsExactly("movieId")

        db.query("SELECT * FROM favorite_movies WHERE movieId = 101").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("title"))).isEqualTo("Iron Man")
            assertThat(cursor.getLong(cursor.getColumnIndexOrThrow("addedAt"))).isEqualTo(2000L)
        }

        // 6. Verify watched_movies remains intact
        val watchedTableInfo = TableInfo.read(db, "watched_movies")
        val watchedPrimaryKeys = watchedTableInfo.columns.values
            .filter { it.isPrimaryKey }
            .map { it.name }
            .toSet()
        assertThat(watchedPrimaryKeys).containsExactly("movieId")

        db.query("SELECT * FROM watched_movies WHERE movieId = 101").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("title"))).isEqualTo("Iron Man")
            assertThat(cursor.getLong(cursor.getColumnIndexOrThrow("watchedAt"))).isEqualTo(3000L)
        }
    }

    private object SQLiteDatabaseConflictAlgorithm {
        const val NONE = android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
        const val FAIL = android.database.sqlite.SQLiteDatabase.CONFLICT_FAIL
    }
}
