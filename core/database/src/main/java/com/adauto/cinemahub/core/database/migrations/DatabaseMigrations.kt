package com.adauto.cinemahub.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `movies_new` (
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
                PRIMARY KEY(`id`, `category`)
            )
        """.trimIndent())

        db.execSQL("""
            INSERT OR REPLACE INTO `movies_new` (
                `id`, `category`, `position`, `title`, `overview`, 
                `posterPath`, `backdropPath`, `voteAverage`, `releaseDate`, 
                `runtime`, `genresJson`, `castJson`, `fetchedAt`
            )
            SELECT 
                `id`, `category`, `position`, `title`, `overview`, 
                `posterPath`, `backdropPath`, `voteAverage`, `releaseDate`, 
                `runtime`, `genresJson`, `castJson`, `fetchedAt`
            FROM `movies`
        """.trimIndent())

        db.execSQL("DROP TABLE `movies`")
        db.execSQL("ALTER TABLE `movies_new` RENAME TO `movies`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_movies_category_position` ON `movies` (`category`, `position`)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sync_outbox` (
                `movieId` INTEGER NOT NULL,
                `target` TEXT NOT NULL,
                `desired` INTEGER NOT NULL,
                `attempts` INTEGER NOT NULL DEFAULT 0,
                `lastError` TEXT,
                `version` INTEGER NOT NULL DEFAULT 1,
                PRIMARY KEY (`movieId`, `target`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `movie_remote_keys` (
                `label` TEXT NOT NULL PRIMARY KEY,
                `nextPage` INTEGER,
                `totalPages` INTEGER,
                `updatedAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `sync_outbox` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `watchlist_movies` (
                `movieId` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `posterPath` TEXT,
                `voteAverage` REAL NOT NULL,
                `releaseDate` TEXT,
                `addedAt` INTEGER NOT NULL,
                PRIMARY KEY(`movieId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR REPLACE INTO `watchlist_movies` (
                `movieId`, `title`, `posterPath`, `voteAverage`, `releaseDate`, `addedAt`
            )
            SELECT `movieId`, `title`, `posterPath`, `voteAverage`, `releaseDate`, `watchedAt`
            FROM `watched_movies`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `watched_movies`")
    }
}
