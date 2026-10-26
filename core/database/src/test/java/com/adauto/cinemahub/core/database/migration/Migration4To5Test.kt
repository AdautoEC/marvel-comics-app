package com.adauto.cinemahub.core.database.migration

import android.content.ContentValues
import androidx.room.testing.MigrationTestHelper
import androidx.room.util.TableInfo
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.adauto.cinemahub.core.database.CinemaHubDatabase
import com.adauto.cinemahub.core.database.migrations.MIGRATION_4_5
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Migration4To5Test {

    private val dbName = "migration_4_5_test.db"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CinemaHubDatabase::class.java
    )

    @Test
    fun migrate4To5_addsCreatedAtColumnToSyncOutbox_andPreservesData() {
        val initialDb = helper.createDatabase(dbName, 4)
        insertVersion4Data(initialDb)
        initialDb.close()

        val migratedDb = helper.runMigrationsAndValidate(dbName, 5, true, MIGRATION_4_5)

        verifyPreservedData(migratedDb)
        verifySyncOutboxSchema(migratedDb)
        verifyTableWriteOperations(migratedDb)
    }

    private fun insertVersion4Data(db: SupportSQLiteDatabase) {
        val outboxValues = ContentValues().apply {
            put("movieId", 42)
            put("target", "FAVORITE")
            put("desired", 1)
            put("attempts", 2)
            put("lastError", "Network timeout")
            put("version", 3)
        }
        db.insert("sync_outbox", android.database.sqlite.SQLiteDatabase.CONFLICT_NONE, outboxValues)
    }

    private fun verifyPreservedData(db: SupportSQLiteDatabase) {
        db.query("SELECT * FROM sync_outbox WHERE movieId = 42 AND target = 'FAVORITE'").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("desired"))).isEqualTo(1)
            assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("attempts"))).isEqualTo(2)
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("lastError"))).isEqualTo("Network timeout")
            assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("version"))).isEqualTo(3)
            assertThat(cursor.getLong(cursor.getColumnIndexOrThrow("createdAt"))).isEqualTo(0L)
        }
    }

    private fun verifySyncOutboxSchema(db: SupportSQLiteDatabase) {
        val outboxTable = TableInfo.read(db, "sync_outbox")
        val createdAtColumn = outboxTable.columns["createdAt"]
        assertThat(createdAtColumn).isNotNull()
        assertThat(createdAtColumn?.type).isEqualTo("INTEGER")
        assertThat(createdAtColumn?.notNull).isTrue()
        assertThat(createdAtColumn?.defaultValue).isEqualTo("0")
    }

    private fun verifyTableWriteOperations(db: SupportSQLiteDatabase) {
        val newValues = ContentValues().apply {
            put("movieId", 99)
            put("target", "WATCHLIST")
            put("desired", 0)
            put("attempts", 0)
            put("version", 1)
            put("createdAt", 1700000000000L)
        }
        val rowId = db.insert("sync_outbox", android.database.sqlite.SQLiteDatabase.CONFLICT_NONE, newValues)
        assertThat(rowId).isGreaterThan(0L)

        db.query("SELECT * FROM sync_outbox WHERE movieId = 99 AND target = 'WATCHLIST'").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getLong(cursor.getColumnIndexOrThrow("createdAt"))).isEqualTo(1700000000000L)
        }
    }
}
