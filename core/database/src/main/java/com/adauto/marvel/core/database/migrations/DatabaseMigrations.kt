package com.adauto.marvel.core.database.migrations

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
