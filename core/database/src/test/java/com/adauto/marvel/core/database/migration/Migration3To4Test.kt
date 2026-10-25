package com.adauto.marvel.core.database.migration

import android.content.ContentValues
import androidx.room.testing.MigrationTestHelper
import androidx.room.util.TableInfo
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.adauto.marvel.core.database.MarvelDatabase
import com.adauto.marvel.core.database.migrations.MIGRATION_3_4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Migration3To4Test {

    private val dbName = "migration_3_4_test.db"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MarvelDatabase::class.java
    )

    @Test
    fun migrate3To4_createsSyncOutboxAndMovieRemoteKeys_andPreservesData() {
        val initialDb = helper.createDatabase(dbName, 3)
        insertVersion3Data(initialDb)
        initialDb.close()

        val migratedDb = helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4)

        verifyPreservedData(migratedDb)
        verifySyncOutboxSchema(migratedDb)
        verifyMovieRemoteKeysSchema(migratedDb)
        verifyTableWriteOperations(migratedDb)
    }

    private fun insertVersion3Data(db: SupportSQLiteDatabase) {
        val movieValues = ContentValues().apply {
            put("id", 42)
            put("category", "NOW_PLAYING")
            put("position", 0)
            put("title", "Spider-Man")
            put("overview", "Peter Parker gets bitten by a spider.")
            put("posterPath", "/spiderman.jpg")
            put("backdropPath", "/spiderman_bg.jpg")
            put("voteAverage", 8.2)
            put("releaseDate", "2002-05-03")
            put("runtime", 121)
            put("genresJson", "[\"Action\",\"Adventure\"]")
            put("castJson", "[\"Tobey Maguire\"]")
            put("fetchedAt", 1700000000000L)
        }
        db.insert("movies", android.database.sqlite.SQLiteDatabase.CONFLICT_NONE, movieValues)

        val favValues = ContentValues().apply {
            put("movieId", 42)
            put("title", "Spider-Man")
            put("posterPath", "/spiderman.jpg")
            put("voteAverage", 8.2)
            put("releaseDate", "2002-05-03")
            put("addedAt", 1700000050000L)
        }
        db.insert("favorite_movies", android.database.sqlite.SQLiteDatabase.CONFLICT_NONE, favValues)
    }

    private fun verifyPreservedData(db: SupportSQLiteDatabase) {
        db.query("SELECT * FROM movies WHERE id = 42 AND category = 'NOW_PLAYING'").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("title"))).isEqualTo("Spider-Man")
            assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("position"))).isEqualTo(0)
            assertThat(cursor.getDouble(cursor.getColumnIndexOrThrow("voteAverage"))).isEqualTo(8.2)
            assertThat(cursor.getLong(cursor.getColumnIndexOrThrow("fetchedAt"))).isEqualTo(1700000000000L)
        }

        db.query("SELECT * FROM favorite_movies WHERE movieId = 42").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("title"))).isEqualTo("Spider-Man")
            assertThat(cursor.getLong(cursor.getColumnIndexOrThrow("addedAt"))).isEqualTo(1700000050000L)
        }
    }

    private fun verifySyncOutboxSchema(db: SupportSQLiteDatabase) {
        val outboxTable = TableInfo.read(db, "sync_outbox")
        val outboxPrimaryKeys = outboxTable.columns.values
            .filter { it.isPrimaryKey }
            .map { it.name }
            .toSet()
        assertThat(outboxPrimaryKeys).containsExactly("movieId", "target")

        assertThat(outboxTable.columns["movieId"]?.type).isEqualTo("INTEGER")
        assertThat(outboxTable.columns["movieId"]?.notNull).isTrue()

        assertThat(outboxTable.columns["target"]?.type).isEqualTo("TEXT")
        assertThat(outboxTable.columns["target"]?.notNull).isTrue()

        assertThat(outboxTable.columns["desired"]?.type).isEqualTo("INTEGER")
        assertThat(outboxTable.columns["desired"]?.notNull).isTrue()

        assertThat(outboxTable.columns["attempts"]?.type).isEqualTo("INTEGER")
        assertThat(outboxTable.columns["attempts"]?.notNull).isTrue()
        assertThat(outboxTable.columns["attempts"]?.defaultValue).isEqualTo("0")

        assertThat(outboxTable.columns["lastError"]?.type).isEqualTo("TEXT")
        assertThat(outboxTable.columns["lastError"]?.notNull).isFalse()

        assertThat(outboxTable.columns["version"]?.type).isEqualTo("INTEGER")
        assertThat(outboxTable.columns["version"]?.notNull).isTrue()
        assertThat(outboxTable.columns["version"]?.defaultValue).isEqualTo("1")
    }

    private fun verifyMovieRemoteKeysSchema(db: SupportSQLiteDatabase) {
        val remoteKeysTable = TableInfo.read(db, "movie_remote_keys")
        val remoteKeysPrimaryKeys = remoteKeysTable.columns.values
            .filter { it.isPrimaryKey }
            .map { it.name }
            .toSet()
        assertThat(remoteKeysPrimaryKeys).containsExactly("label")

        assertThat(remoteKeysTable.columns["label"]?.type).isEqualTo("TEXT")
        assertThat(remoteKeysTable.columns["label"]?.notNull).isTrue()

        assertThat(remoteKeysTable.columns["nextPage"]?.type).isEqualTo("INTEGER")
        assertThat(remoteKeysTable.columns["nextPage"]?.notNull).isFalse()

        assertThat(remoteKeysTable.columns["totalPages"]?.type).isEqualTo("INTEGER")
        assertThat(remoteKeysTable.columns["totalPages"]?.notNull).isFalse()

        assertThat(remoteKeysTable.columns["updatedAt"]?.type).isEqualTo("INTEGER")
        assertThat(remoteKeysTable.columns["updatedAt"]?.notNull).isTrue()
    }

    private fun verifyTableWriteOperations(db: SupportSQLiteDatabase) {
        val outboxValues = ContentValues().apply {
            put("movieId", 42)
            put("target", "FAVORITE")
            put("desired", 1)
            put("attempts", 0)
            putNull("lastError")
            put("version", 1)
        }
        val outboxRowId = db.insert("sync_outbox", android.database.sqlite.SQLiteDatabase.CONFLICT_NONE, outboxValues)
        assertThat(outboxRowId).isNotEqualTo(-1L)

        val remoteKeyValues = ContentValues().apply {
            put("label", "POPULAR")
            put("nextPage", 2)
            put("totalPages", 10)
            put("updatedAt", 1700000090000L)
        }
        val remoteKeyRowId = db.insert(
            "movie_remote_keys",
            android.database.sqlite.SQLiteDatabase.CONFLICT_NONE,
            remoteKeyValues
        )
        assertThat(remoteKeyRowId).isNotEqualTo(-1L)
    }
}
