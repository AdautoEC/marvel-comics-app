# Phase 3 (P2): Data Layer Monotonicity, RemoteMediator Position Indexing & Outbox FIFO Ordering Specification

## 1. Context & Motivation

Following the completion and verification of Phase 1 (P0: Data & Session Guarantees) and Phase 2 (P1: Concurrency, Lifecycle & Quality Gates), this specification targets the Phase 3 (P2) architectural findings identified in the technical audit:

1. **`MoviesRemoteMediator` Position Collision:**
   - In `MoviesRemoteMediator.load()`, start position was computed as:
     `val startPosition = (page - 1) * state.config.pageSize`
   - TMDB API paginated movie endpoints (`/3/movie/popular`, `/3/movie/top_rated`) always return exactly 20 items per page regardless of client request parameters.
   - `MovieEntity` enforces a unique SQLite index on `(category, position)`.
   - If `state.config.pageSize` is configured to a value lower than 20 (e.g. 10 or 15), items from subsequent pages overlap in position with previous pages, triggering SQLite constraint violations or index overwrites.
   - **Resolution:** Decouple database position calculation from client-side `PagingConfig.pageSize` by using a canonical constant `TMDB_PAGE_SIZE = 20`.

2. **Guest Staging Version Monotonicity:**
   - In `AuthRepositoryImpl.stageGuestDataIntoOutbox()`, local guest favorites and watchlist entries are converted into `SyncOutboxEntity` records with a hardcoded `version = 1`.
   - If an outbox entry already existed for that `(movieId, target)` (for example, with `version = 3`), rewriting `version = 1` regresses the monotonic version counter, violating optimistic concurrency invariants used by `deleteVersion` and `updateFailure`.
   - **Resolution:** Query existing records before staging, setting `version = (existing?.version ?: 0) + 1`.

3. **Outbox FIFO Dispatch Ordering (`getAllPending`):**
   - In `SyncOutboxDao.getAllPending()`, rows were ordered by `ORDER BY version ASC`.
   - `version` represents per-row mutation revisions for a single `(movieId, target)`, not chronological creation order across different movies. A movie toggled 3 times earlier has `version = 3`, while a movie toggled once later has `version = 1`. Sorting by `version ASC` dispatches the later mutation before the earlier one, corrupting the intended mutation sequence.
   - **Resolution:**
     - Extend `SyncOutboxEntity` with `@ColumnInfo(defaultValue = "0") val createdAt: Long = 0L`.
     - Bump Room database version from 4 to 5.
     - Implement schema migration `MIGRATION_4_5` (`ALTER TABLE sync_outbox ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0`).
     - Update `getAllPending()` to order by `ORDER BY createdAt ASC, version ASC`.
     - Populate `createdAt` when creating or updating outbox rows in `MoviesRepositoryImpl` and `AuthRepositoryImpl` (reusing `fav.addedAt` and `watch.watchedAt` for guest staging).

---

## 2. Detailed Technical Design

### 2.1 RemoteMediator Position Indexing

**File:** `core/data/src/main/java/com/adauto/marvel/core/data/mediator/MoviesRemoteMediator.kt`

```kotlin
class MoviesRemoteMediator(...) : RemoteMediator<Int, MovieWithUserData>() {

    companion object {
        const val TMDB_PAGE_SIZE = 20
    }

    override suspend fun load(loadType: LoadType, state: PagingState<Int, MovieWithUserData>): MediatorResult {
        ...
        val startPosition = (page - 1) * TMDB_PAGE_SIZE
        val entities = data.results.mapIndexed { index, dto ->
            dto.toMovieEntity(category = category, position = startPosition + index, fetchedAt = now)
        }
        ...
    }
}
```

- When `page = 1`: positions `0..19`.
- When `page = 2`: positions `20..39`.
- Guarantees `position` is contiguous, distinct, and monotonic across all pages regardless of `state.config.pageSize`.

---

### 2.2 Room Database v5 & Schema Migration `MIGRATION_4_5`

**Files:**
- `core/database/src/main/java/com/adauto/marvel/core/database/entity/SyncOutboxEntity.kt`
- `core/database/src/main/java/com/adauto/marvel/core/database/MarvelDatabase.kt`
- `core/database/src/main/java/com/adauto/marvel/core/database/migrations/DatabaseMigrations.kt`
- `core/database/src/main/java/com/adauto/marvel/core/database/di/DatabaseModule.kt`

**Entity Change:**
```kotlin
@Entity(
    tableName = "sync_outbox",
    primaryKeys = ["movieId", "target"]
)
data class SyncOutboxEntity(
    val movieId: Int,
    val target: String,
    val desired: Boolean,
    @ColumnInfo(defaultValue = "0")
    val attempts: Int = 0,
    val lastError: String? = null,
    @ColumnInfo(defaultValue = "1")
    val version: Int = 1,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = 0L
)
```

**Database Migration:**
```kotlin
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `sync_outbox` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0")
    }
}
```

**Database Module:**
```kotlin
.addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
```

**DAO Query:**
```kotlin
@Query("SELECT * FROM sync_outbox ORDER BY createdAt ASC, version ASC")
suspend fun getAllPending(): List<SyncOutboxEntity>
```

---

### 2.3 Monotonic Version & Timestamp Handling in Repositories

#### 2.3.1 `MoviesRepositoryImpl`
```kotlin
val existing = database.syncOutboxDao().find(movie.id, target)
val nextVersion = (existing?.version ?: 0) + 1
val creationTime = existing?.createdAt ?: clock.millis()

database.syncOutboxDao().upsert(
    SyncOutboxEntity(
        movieId = movie.id,
        target = target,
        desired = newDesiredState,
        attempts = 0,
        version = nextVersion,
        createdAt = creationTime
    )
)
```

#### 2.3.2 `AuthRepositoryImpl.stageGuestDataIntoOutbox()`
```kotlin
private suspend fun stageGuestDataIntoOutbox() {
    val localFavorites = database.favoriteDao().getAllFavorites().first()
    val localWatched = database.watchedDao().getAllWatched().first()

    if (localFavorites.isNotEmpty() || localWatched.isNotEmpty()) {
        database.withTransaction {
            for (fav in localFavorites) {
                val existing = database.syncOutboxDao().find(fav.movieId, SyncOutboxWorker.TARGET_FAVORITE)
                val nextVersion = (existing?.version ?: 0) + 1
                val creationTime = existing?.createdAt ?: fav.addedAt
                database.syncOutboxDao().upsert(
                    SyncOutboxEntity(
                        movieId = fav.movieId,
                        target = SyncOutboxWorker.TARGET_FAVORITE,
                        desired = true,
                        attempts = 0,
                        version = nextVersion,
                        createdAt = creationTime
                    )
                )
            }
            for (watch in localWatched) {
                val existing = database.syncOutboxDao().find(watch.movieId, SyncOutboxWorker.TARGET_WATCHLIST)
                val nextVersion = (existing?.version ?: 0) + 1
                val creationTime = existing?.createdAt ?: watch.watchedAt
                database.syncOutboxDao().upsert(
                    SyncOutboxEntity(
                        movieId = watch.movieId,
                        target = SyncOutboxWorker.TARGET_WATCHLIST,
                        desired = true,
                        attempts = 0,
                        version = nextVersion,
                        createdAt = creationTime
                    )
                )
            }
        }
    }
}
```

---

## 3. Verification Plan

1. **Unit & Migration Tests:**
   - `MoviesRemoteMediatorTest`: Test position calculation with `pageSize < 20` (e.g. `pageSize = 10`), asserting that page 2 entities have position $\ge 20$.
   - `Migration4To5Test`: Robolectric Room migration test validating schema upgrade from version 4 to 5, data preservation, and `createdAt` default values.
   - `SyncOutboxDaoTest`: Verify that `getAllPending()` returns entities sorted by `createdAt ASC` even when `version` is higher for earlier created entities.
   - `AuthRepositoryTest`: Verify guest data staging computes `(existing?.version ?: 0) + 1` and preserves/assigns creation timestamp.
   - `MoviesRepositoryImplTest`: Verify toggles compute `(existing?.version ?: 0) + 1` and record creation timestamp.
2. **Quality Gates:**
   - Detekt (`maxIssues = 0`).
   - Android Lint (`lintDebug`).
   - Release unit tests (`testReleaseUnitTest`).
   - Gradle build (`assembleDebug`, `assembleRelease`).
