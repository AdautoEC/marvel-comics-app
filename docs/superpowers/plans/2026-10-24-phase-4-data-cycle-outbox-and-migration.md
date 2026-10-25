# Phase 4: Data Cycle - Outbox, OAuth Persistence & Single Migration 3->4 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement offline-resilient local-first synchronization: Room schema bump to v4 (`sync_outbox` and `movie_remote_keys` via `MIGRATION_3_4`), WorkManager `SyncOutboxWorker` with compare-and-delete, cross-account login guard with DataStore persistence, gated atomic toggles, and RemoteMediator cold migration recovery.

**Architecture:** Encapsulates local-first mutations in an atomic read-modify-write Room transaction with versioned outbox rows. Pushes pending changes via an assisted `@HiltWorker` executing concurrency-safe compare-and-delete and retry backoff. Enforces authoritative cloud reconciliation guarding pending local edits via `SyncCoordinator` mutex. Protects cross-account switching by purging previous account state and staging guest items. Recovers cold-migrated categories via `movie_remote_keys` presence check in `MoviesRemoteMediator.initialize()`.

**Tech Stack:** Room 2.6.1, WorkManager 2.9.1, Hilt Work 1.2.0, AndroidX DataStore Preferences 1.1.1, Kotlinx Coroutines Mutex & Flow, TMDB Account API.

**Spec:** [`docs/superpowers/specs/2026-10-24-cinema-hub-production-resilience-design.md`](file:///Users/adautosouza/repos/estudos/docs/superpowers/specs/2026-10-24-cinema-hub-production-resilience-design.md)

## Global Constraints

- Android Application ID: `com.adauto.cinemahub`
- Database Version: Bumped from 3 to 4 in `MarvelDatabase` with exported `4.json`.
- Migration: Single atomic `MIGRATION_3_4` creating `sync_outbox` and `movie_remote_keys` without destroying user data.
- Table `sync_outbox`: Columns `movieId` (INT PK), `target` (TEXT PK), `desired` (INT), `attempts` (INT DEFAULT 0), `lastError` (TEXT), `version` (INT DEFAULT 1).
- Table `movie_remote_keys`: Columns `label` (TEXT PK), `nextPage` (INT), `totalPages` (INT), `updatedAt` (INT).
- Atomic Toggles: Read-modify-write inside `database.withTransaction` incrementing `version`. Guest writes exclusively to local tables; outbox written only when authenticated.
- Compare-and-Delete: `DELETE FROM sync_outbox WHERE movieId = :movieId AND target = :target AND version = :capturedVersion`.
- Failure Updates: `UPDATE sync_outbox SET attempts = attempts + 1, lastError = :error WHERE movieId = :movieId AND target = :target AND version = :capturedVersion`.
- Cross-Account Guard: DataStore preference `last_account_id`. If `newAccountId != lastAccountId`, purge outbox and local user data before staging guest items.
- Cold Migration Recovery: `MoviesRemoteMediator.initialize()` launches initial refresh if `movie_remote_keys` row is missing.
- Detekt & Lint: Zero code smells (`maxIssues = 0`), 100% test pass rate across all modules.

## Review Focus

1. **Compare-and-Delete Race Condition**: If a user toggles an item while `SyncOutboxWorker` is pushing to TMDB, the outbox row's version increments. Verify that the worker's delete query only deletes rows matching `version == capturedVersion`, preventing newer mutations from being discarded.
2. **Failure Update Race Condition**: When a push fails, verify that `UPDATE sync_outbox SET attempts = attempts + 1 ... WHERE version = :capturedVersion` does not update or mark as failed a newer pending version.
3. **Cross-Account Data Leakage**: Logging in with account B when account A was previously active must purge all outbox rows and local user tables before importing guest favorites.
4. **Guest Mode Outbox Hygiene**: Toggling favorites in guest mode must never write rows to `sync_outbox` to prevent negative desired states from wiping remote account items upon future login.
5. **Cold Migration Category Invalidation**: Categories cached before migration lack `movie_remote_keys`. `MoviesRemoteMediator.initialize()` must force `LAUNCH_INITIAL_REFRESH` and `REFRESH` must delete both category items and keys atomically.

---

### Task 1: Room Schema Bump v4, Single `MIGRATION_3_4`, `SyncOutboxDao` & `MovieRemoteKeysDao` (:core:database)

**Files:**
- Create: `core/database/src/main/java/com/adauto/marvel/core/database/entity/SyncOutboxEntity.kt`
- Create: `core/database/src/main/java/com/adauto/marvel/core/database/entity/MovieRemoteKeysEntity.kt`
- Create: `core/database/src/main/java/com/adauto/marvel/core/database/dao/SyncOutboxDao.kt`
- Create: `core/database/src/main/java/com/adauto/marvel/core/database/dao/MovieRemoteKeysDao.kt`
- Modify: `core/database/src/main/java/com/adauto/marvel/core/database/MarvelDatabase.kt`
- Modify: `core/database/src/main/java/com/adauto/marvel/core/database/migrations/DatabaseMigrations.kt`
- Modify: `core/database/src/main/java/com/adauto/marvel/core/database/di/DatabaseModule.kt`
- Modify: `core/database/build.gradle.kts`
- Create: `core/database/src/test/java/com/adauto/marvel/core/database/migration/Migration3To4Test.kt`

**Interfaces:**
- Consumes: Room 2.6.1, `MigrationTestHelper`.
- Produces: `SyncOutboxDao`, `MovieRemoteKeysDao`, `MIGRATION_3_4`, schema `4.json`.

- [ ] **Step 1: Define entities in `:core:database`**
  In `core/database/src/main/java/com/adauto/marvel/core/database/entity/SyncOutboxEntity.kt`:
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
      val version: Int = 1
  )
  ```
  In `core/database/src/main/java/com/adauto/marvel/core/database/entity/MovieRemoteKeysEntity.kt`:
  ```kotlin
  @Entity(tableName = "movie_remote_keys")
  data class MovieRemoteKeysEntity(
      @PrimaryKey val label: String,
      val nextPage: Int?,
      val totalPages: Int?,
      val updatedAt: Long
  )
  ```

- [ ] **Step 2: Define DAOs in `:core:database`**
  In `SyncOutboxDao.kt`:
  ```kotlin
  @Dao
  interface SyncOutboxDao {
      @Query("SELECT * FROM sync_outbox WHERE movieId = :movieId AND target = :target")
      suspend fun find(movieId: Int, target: String): SyncOutboxEntity?

      @Upsert
      suspend fun upsert(entity: SyncOutboxEntity)

      @Query("DELETE FROM sync_outbox WHERE movieId = :movieId AND target = :target AND version = :version")
      suspend fun deleteVersion(movieId: Int, target: String, version: Int): Int

      @Query("UPDATE sync_outbox SET attempts = attempts + 1, lastError = :error WHERE movieId = :movieId AND target = :target AND version = :version")
      suspend fun updateFailure(movieId: Int, target: String, version: Int, error: String?): Int

      @Query("SELECT * FROM sync_outbox ORDER BY version ASC")
      suspend fun getAllPending(): List<SyncOutboxEntity>

      @Query("SELECT movieId FROM sync_outbox WHERE target = :target")
      suspend fun getPendingMovieIds(target: String): List<Int>

      @Query("DELETE FROM sync_outbox")
      suspend fun clearAll()
  }
  ```
  In `MovieRemoteKeysDao.kt`:
  ```kotlin
  @Dao
  interface MovieRemoteKeysDao {
      @Query("SELECT * FROM movie_remote_keys WHERE label = :label")
      suspend fun getKey(label: String): MovieRemoteKeysEntity?

      @Upsert
      suspend fun insertOrReplace(key: MovieRemoteKeysEntity)

      @Query("DELETE FROM movie_remote_keys WHERE label = :label")
      suspend fun deleteKey(label: String)

      @Query("DELETE FROM movie_remote_keys")
      suspend fun clearAll()
  }
  ```

- [ ] **Step 3: Update `MarvelDatabase` and `DatabaseMigrations`**
  In `MarvelDatabase.kt`:
  Add `SyncOutboxEntity::class`, `MovieRemoteKeysEntity::class` to `entities`.
  Bump `version = 4`.
  Add `abstract fun syncOutboxDao(): SyncOutboxDao` and `abstract fun movieRemoteKeysDao(): MovieRemoteKeysDao`.
  In `DatabaseMigrations.kt`:
  ```kotlin
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
  ```
  In `DatabaseModule.kt`:
  `.addMigrations(MIGRATION_2_3, MIGRATION_3_4)`.

- [ ] **Step 4: Configure test assets and write `Migration3To4Test`**
  In `core/database/build.gradle.kts`:
  Ensure Room test schemas asset path is configured:
  ```kotlin
  sourceSets.getByName("test").assets.srcDir("$projectDir/schemas")
  ```
  In `core/database/src/test/java/com/adauto/marvel/core/database/migration/Migration3To4Test.kt`:
  Test migration from schema 3 to 4, verifying `sync_outbox` and `movie_remote_keys` tables are created and existing data in `movies`, `favorite_movies`, and `watched_movies` is retained.

- [ ] **Step 5: Verify build, tests, Detekt, and schema export**
  Run: `./gradlew :core:database:testDebugUnitTest :core:database:detekt`
  Verify that `4.json` is exported into `core/database/schemas/com.adauto.marvel.core.database.MarvelDatabase/4.json`.

- [ ] **Step 6: Commit**
  ```bash
  git add core/database/
  git commit -m "feat(database): bump Room to v4, add MIGRATION_3_4, sync_outbox, and movie_remote_keys"
  ```

---

### Task 2: WorkManager Hilt Setup, `SyncCoordinator` & `SyncOutboxWorker` (:core:data & :app)

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `core/data/build.gradle.kts`
- Modify: `app/src/main/java/com/adauto/marvel/MarvelApp.kt`
- Create: `core/data/src/main/java/com/adauto/marvel/core/data/sync/SyncCoordinator.kt`
- Create: `core/data/src/main/java/com/adauto/marvel/core/data/sync/SyncEventBus.kt`
- Create: `core/data/src/main/java/com/adauto/marvel/core/data/worker/SyncOutboxWorker.kt`
- Create: `core/data/src/test/java/com/adauto/marvel/core/data/worker/SyncOutboxWorkerTest.kt`

**Interfaces:**
- Consumes: WorkManager 2.9.1, `SyncOutboxDao`, `TmdbApi`, `SessionManager`.
- Produces: `SyncCoordinator`, `SyncEventBus`, and `@HiltWorker SyncOutboxWorker`.

- [ ] **Step 1: Add WorkManager & Hilt Work dependencies to `gradle/libs.versions.toml`**
  ```toml
  [versions]
  work = "2.9.1"
  hiltWork = "1.2.0"

  [libraries]
  androidx-work-runtime = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }
  androidx-work-testing = { group = "androidx.work", name = "work-testing", version.ref = "work" }
  androidx-hilt-work = { group = "androidx.hilt", name = "hilt-work", version.ref = "hiltWork" }
  androidx-hilt-compiler = { group = "androidx.hilt", name = "hilt-compiler", version.ref = "hiltWork" }
  ```
  Add to `app/build.gradle.kts` and `core/data/build.gradle.kts`:
  `implementation(libs.androidx.work.runtime)`
  `implementation(libs.androidx.hilt.work)`
  `ksp(libs.androidx.hilt.compiler)`
  In `core/data/build.gradle.kts`:
  `testImplementation(libs.androidx.work.testing)`

- [ ] **Step 2: Implement `Configuration.Provider` in `MarvelApp.kt`**
  In `MarvelApp.kt`:
  ```kotlin
  @HiltAndroidApp
  class MarvelApp : Application(), Configuration.Provider {
      @Inject lateinit var workerFactory: HiltWorkerFactory
      override val workManagerConfiguration: Configuration
          get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
  ```

- [ ] **Step 3: Implement `SyncCoordinator` and `SyncEventBus` in `:core:data`**
  In `core/data/src/main/java/com/adauto/marvel/core/data/sync/SyncCoordinator.kt`:
  ```kotlin
  @Singleton
  class SyncCoordinator @Inject constructor() {
      val mutex = Mutex()
      private val _pullRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
      val pullRequests: SharedFlow<Unit> = _pullRequests.asSharedFlow()

      suspend fun requestPull() {
          _pullRequests.emit(Unit)
      }
  }
  ```
  In `core/data/src/main/java/com/adauto/marvel/core/data/sync/SyncEventBus.kt`:
  ```kotlin
  data class SyncFailureEvent(val movieId: Int, val target: String, val error: String?)

  @Singleton
  class SyncEventBus @Inject constructor() {
      private val _events = MutableSharedFlow<SyncFailureEvent>(extraBufferCapacity = 10)
      val events: SharedFlow<SyncFailureEvent> = _events.asSharedFlow()

      suspend fun emitFailure(event: SyncFailureEvent) {
          _events.emit(event)
      }
  }
  ```

- [ ] **Step 4: Implement `@HiltWorker class SyncOutboxWorker`**
  In `core/data/src/main/java/com/adauto/marvel/core/data/worker/SyncOutboxWorker.kt`:
  - Handles pending outbox queue with `syncCoordinator.mutex.withLock`.
  - For each row:
    - Calls TMDB API (`api.markAsFavorite` / `api.addToWatchlist`).
    - On success: compare-and-delete: `database.syncOutboxDao().deleteVersion(movieId, target, capturedVersion)`.
    - On transient error (IOException, 5xx, or 429 with `Retry-After` parsing):
      - If `attempts + 1 < MAX_ATTEMPTS` (5): `database.syncOutboxDao().updateFailure(movieId, target, capturedVersion, error)`. Return `Result.retry()`.
    - On permanent error (permanent 4xx non-404, or `attempts + 1 >= MAX_ATTEMPTS`):
      - Executes rollback transaction:
        ```kotlin
        val rowsAffected = database.withTransaction {
            val deleted = database.syncOutboxDao().deleteVersion(movieId, target, capturedVersion)
            if (deleted == 1 && desired) {
                when (target) {
                    "FAVORITE" -> database.favoriteDao().delete(movieId)
                    "WATCHLIST" -> database.watchedDao().delete(movieId)
                }
            }
            deleted
        }
        if (rowsAffected == 1) {
            syncEventBus.emitFailure(SyncFailureEvent(movieId, target, errorMsg))
            if (!desired) {
                syncCoordinator.requestPull()
            }
        }
        ```
    - On 404: `database.syncOutboxDao().deleteVersion(movieId, target, capturedVersion)`.
    - On 401: calls `sessionManager.clearSession()`, cancels unique work, retains outbox rows, returns `Result.failure()`.

- [ ] **Step 5: Write unit tests in `SyncOutboxWorkerTest.kt`**
  Cover success compare-and-delete, retry on transient error with attempt increment, rollback on permanent error, 404 deletion, and 401 session cancellation.

- [ ] **Step 6: Verify build, tests, and Detekt**
  Run: `./gradlew :core:data:testDebugUnitTest :app:testDebugUnitTest :core:data:detekt :app:detekt`

- [ ] **Step 7: Commit**
  ```bash
  git add gradle/libs.versions.toml app/ core/data/
  git commit -m "feat(sync): add WorkManager SyncOutboxWorker, SyncCoordinator, and SyncEventBus"
  ```

---

### Task 3: Gated Atomic Toggles, Session Lifecycle & Cross-Account Guard (:core:data)

**Files:**
- Modify: `core/data/src/main/java/com/adauto/marvel/core/data/repository/AuthRepositoryImpl.kt`
- Modify: `core/data/src/main/java/com/adauto/marvel/core/data/repository/MoviesRepositoryImpl.kt`
- Modify: `core/data/src/test/java/com/adauto/marvel/core/data/repository/AuthRepositoryTest.kt`
- Modify: `core/data/src/test/java/com/adauto/marvel/core/data/repository/MoviesRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `SyncCoordinator`, `SyncOutboxDao`, `SessionManager`, WorkManager.
- Produces: Atomic toggles, cross-account guard, guest-to-auth sync, authoritative pull reconciliation.

- [ ] **Step 1: Implement Cross-Account Guard & OAuth Lifecycle in `AuthRepositoryImpl`**
  In `AuthRepositoryImpl.kt`:
  - DataStore key: `stringPreferencesKey("last_account_id")`.
  - In `completeAuthentication(requestToken)`:
    - After obtaining account profile and session:
      - Read `lastAccountId`.
      - If `lastAccountId != null && lastAccountId != newAccount.id.toString()`:
        - In Room transaction: `syncOutboxDao.clearAll()`, `favoriteDao.clearAll()`, `watchedDao.clearAll()`.
      - Stage guest local favorites and watchlist items into `sync_outbox`:
        - Query local favorites and watched movies:
        - For each: `syncOutboxDao.upsert(SyncOutboxEntity(movieId = it.id, target = "FAVORITE", desired = true, version = 1))`.
      - Store `last_account_id = newAccount.id.toString()`.
      - Enqueue `SyncOutboxWorker` unique work.
  - In `logout()`:
    - Cancel `SyncOutboxWorker` unique work via `WorkManager`.
    - 3-second outbox flush attempt:
      `withTimeoutOrNull(3_000L) { syncCoordinator.mutex.withLock { /* flush */ } }`.
    - Call TMDB API `DELETE /3/authentication/session`.
    - Clear session storage, `syncOutboxDao.clearAll()`, `favoriteDao.clearAll()`, `watchedDao.clearAll()`, and remove `last_account_id`.

- [ ] **Step 2: Implement Gated Atomic Toggles in `MoviesRepositoryImpl`**
  In `MoviesRepositoryImpl.kt`:
  - In `toggleFavorite(movieId: Int)` and `toggleWatched(movieId: Int)`:
    - Check `val isAuthenticated = sessionManager.getSession() != null`.
    - If `!isAuthenticated`:
      - Toggle only local database (`favoriteDao` or `watchedDao`). Do NOT write to `sync_outbox`.
    - If `isAuthenticated`:
      - Inside `database.withTransaction`:
        - Toggle local table.
        - Read existing row: `val existing = syncOutboxDao.find(movieId, target)`.
        - `val nextVersion = (existing?.version ?: 0) + 1`.
        - `syncOutboxDao.upsert(SyncOutboxEntity(movieId = movieId, target = target, desired = newDesired, attempts = 0, version = nextVersion))`.
      - Enqueue `SyncOutboxWorker` unique work.

- [ ] **Step 3: Implement Authoritative Pull Reconciliation in `MoviesRepositoryImpl`**
  In `MoviesRepositoryImpl.kt` (`syncCloudWithLocal`):
  - Download all remote pages first. If any page fails, abort without touching local DB.
  - Inside `syncCoordinator.mutex.withLock`:
    - `database.withTransaction`:
      - Query pending movie IDs: `val pending = syncOutboxDao.getPendingMovieIds(target).toSet()`.
      - Reconcile only items where `movieId !in pending`:
        - Remote items missing locally are inserted.
        - Local items missing remotely are deleted.

- [ ] **Step 4: Write unit tests in `AuthRepositoryTest` and `MoviesRepositoryImplTest`**
  - Verify guest toggle does not write outbox.
  - Verify authenticated toggle writes outbox and increments version atomically.
  - Verify cross-account login wipes previous account data.
  - Verify same-account login retains data and outbox.
  - Verify authoritative pull skips pending outbox movie IDs.
  - Verify logout cancels worker and clears local data.

- [ ] **Step 5: Verify build, tests, and Detekt**
  Run: `./gradlew :core:data:testDebugUnitTest :core:data:detekt`

- [ ] **Step 6: Commit**
  ```bash
  git add core/data/
  git commit -m "feat(data): implement gated atomic toggles, cross-account guard, and authoritative pull"
  ```

---

### Task 4: `MoviesRemoteMediator` Cold Migration Recovery & Category Invalidation (:core:data)

**Files:**
- Modify: `core/data/src/main/java/com/adauto/marvel/core/data/mediator/MoviesRemoteMediator.kt`
- Modify: `core/data/src/test/java/com/adauto/marvel/core/data/mediator/MoviesRemoteMediatorTest.kt`

**Interfaces:**
- Consumes: `MovieRemoteKeysDao`, `MarvelDatabase`, `TmdbApi`.
- Produces: Remote keys recovery, `LAUNCH_INITIAL_REFRESH` on cold migration, atomic category & key invalidation.

- [ ] **Step 1: Update `MoviesRemoteMediator.initialize()`**
  In `MoviesRemoteMediator.kt`:
  ```kotlin
  override suspend fun initialize(): InitializeAction {
      val remoteKey = database.movieRemoteKeysDao().getKey(category)
          ?: return InitializeAction.LAUNCH_INITIAL_REFRESH

      val cacheAge = clock.millis() - remoteKey.updatedAt
      return if (cacheAge < TimeUnit.HOURS.toMillis(cacheTtlHours)) {
          InitializeAction.SKIP_INITIAL_REFRESH
      } else {
          InitializeAction.LAUNCH_INITIAL_REFRESH
      }
  }
  ```

- [ ] **Step 2: Update `MoviesRemoteMediator.load()`**
  In `MoviesRemoteMediator.kt`:
  - On `LoadType.REFRESH`:
    - `page = 1`.
  - On `LoadType.APPEND`:
    - Query `val remoteKey = database.movieRemoteKeysDao().getKey(category)`.
    - If `remoteKey?.nextPage == null`: return `MediatorResult.Success(endOfPaginationReached = true)`.
    - `page = remoteKey.nextPage`.
  - On successful API response:
    - `val isEnd = page >= data.totalPages || data.results.isEmpty()`.
    - `val nextPage = if (isEnd) null else page + 1`.
    - Inside `database.withTransaction`:
      - If `loadType == LoadType.REFRESH`:
        - `database.movieDao().clearCategory(category)`.
        - `database.movieRemoteKeysDao().deleteKey(category)`.
      - `database.movieDao().upsertAll(entities)`.
      - `database.movieRemoteKeysDao().insertOrReplace(MovieRemoteKeysEntity(label = category, nextPage = nextPage, totalPages = data.totalPages, updatedAt = now))`.

- [ ] **Step 3: Update `MoviesRemoteMediatorTest` unit tests**
  - Verify missing remote key in `initialize()` returns `LAUNCH_INITIAL_REFRESH`.
  - Verify fresh remote key in `initialize()` returns `SKIP_INITIAL_REFRESH`.
  - Verify `REFRESH` clears category items and key in the same Room transaction.
  - Verify `APPEND` reads `nextPage` from `movie_remote_keys`.
  - Verify `APPEND` returns `endOfPaginationReached = true` when `nextPage` is null.

- [ ] **Step 4: Verify build, tests, Detekt, and coverage**
  Run: `./gradlew testDebugUnitTest detekt koverXmlReport`

- [ ] **Step 5: Commit**
  ```bash
  git add core/data/
  git commit -m "feat(data): implement RemoteMediator cold migration recovery and remote keys management"
  ```
