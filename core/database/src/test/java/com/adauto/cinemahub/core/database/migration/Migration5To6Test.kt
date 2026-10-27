package com.adauto.cinemahub.core.database.migration

import android.content.ContentValues
import androidx.room.testing.MigrationTestHelper
import androidx.room.util.TableInfo
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.adauto.cinemahub.core.database.CinemaHubDatabase
import com.adauto.cinemahub.core.database.migrations.MIGRATION_5_6
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Migration5To6Test {

    private val dbName = "migration_5_6_test.db"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CinemaHubDatabase::class.java
    )

    @Test
    fun migrate5To6_renamesWatchedMoviesTableAndWatchedAtColumn_andPreservesData() {
        val initialDb = helper.createDatabase(dbName, 5)
        insertVersion5Data(initialDb)
        initialDb.close()

        val migratedDb = helper.runMigrationsAndValidate(dbName, 6, true, MIGRATION_5_6)

        verifyPreservedData(migratedDb)
        verifyWatchlistMoviesSchema(migratedDb)
        verifyTableWriteOperations(migratedDb)
    }

    private fun insertVersion5Data(db: SupportSQLiteDatabase) {
        val watchedValues = ContentValues().apply {
            put("movieId", 101)
            put("title", "Interstellar")
            put("posterPath", "/interstellar.jpg")
            put("voteAverage", 8.7)
            put("releaseDate", "2014-11-05")
            put("watchedAt", 1_700_000_000_000L)
        }
        db.insert("watched_movies", android.database.sqlite.SQLiteDatabase.CONFLICT_NONE, watchedValues)
    }

    private fun verifyPreservedData(db: SupportSQLiteDatabase) {
        db.query("SELECT * FROM watchlist_movies WHERE movieId = 101").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("title"))).isEqualTo("Interstellar")
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("posterPath"))).isEqualTo("/interstellar.jpg")
            assertThat(cursor.getDouble(cursor.getColumnIndexOrThrow("voteAverage"))).isEqualTo(8.7)
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("releaseDate"))).isEqualTo("2014-11-05")
            assertThat(cursor.getLong(cursor.getColumnIndexOrThrow("addedAt"))).isEqualTo(1_700_000_000_000L)
        }
    }

    private fun verifyWatchlistMoviesSchema(db: SupportSQLiteDatabase) {
        val watchlistTable = TableInfo.read(db, "watchlist_movies")
        assertThat(watchlistTable.columns).containsKey("addedAt")
        assertThat(watchlistTable.columns).doesNotContainKey("watchedAt")

        val oldWatchedTable = TableInfo.read(db, "watched_movies")
        assertThat(oldWatchedTable.columns).isEmpty()
    }

    private fun verifyTableWriteOperations(db: SupportSQLiteDatabase) {
        val newValues = ContentValues().apply {
            put("movieId", 202)
            put("title", "Dune")
            put("posterPath", "/dune.jpg")
            put("voteAverage", 8.0)
            put("releaseDate", "2021-10-22")
            put("addedAt", 1_710_000_000_000L)
        }
        val rowId = db.insert("watchlist_movies", android.database.sqlite.SQLiteDatabase.CONFLICT_NONE, newValues)
        assertThat(rowId).isGreaterThan(0L)

        db.query("SELECT * FROM watchlist_movies WHERE movieId = 202").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getLong(cursor.getColumnIndexOrThrow("addedAt"))).isEqualTo(1_710_000_000_000L)
        }
    }
}
