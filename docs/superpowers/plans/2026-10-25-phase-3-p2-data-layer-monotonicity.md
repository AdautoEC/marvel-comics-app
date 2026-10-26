# Phase 3 (P2): Data Layer Monotonicity, RemoteMediator Position Indexing & Outbox FIFO Ordering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix RemoteMediator position indexing collision on custom page sizes, implement Room schema migration 4->5 to order outbox mutations by creation timestamp, and enforce monotonic versioning during guest staging.

**Architecture:** Replace dynamic `PagingConfig.pageSize` position calculation in `MoviesRemoteMediator` with a constant `TMDB_PAGE_SIZE = 20`. Upgrade Room database to version 5 with an explicit `createdAt` column on `sync_outbox` table, adjusting `getAllPending()` to sort by creation order (`ORDER BY createdAt ASC, version ASC`). Update `AuthRepositoryImpl` to query existing outbox entries and stage with `(existing?.version ?: 0) + 1` and corresponding timestamps.

**Tech Stack:** Android Jetpack Room (v2.6+), AndroidX Paging 3 (`RemoteMediator`), Kotlin Coroutines & Flow, SQLite, Robolectric, MockK, Google Truth, Detekt.

**Spec:** [`docs/superpowers/specs/2026-10-25-phase-3-p2-data-layer-monotonicity-design.md`](file:///Users/adautosouza/repos/estudos/docs/superpowers/specs/2026-10-25-phase-3-p2-data-layer-monotonicity-design.md)

## Global Constraints

- Android API desugaring enabled, Room 2.6 schema export enabled.
- Zero Detekt violations (`maxIssues = 0`).
- No regressions in Kover coverage or existing database migrations (1->2, 2->3, 3->4).
- Maintain documentation integrity and preserve existing docstrings.

## Review Focus

- Page size decoupling: `MoviesRemoteMediator` calculates position strictly using TMDB page size 20 regardless of `state.config.pageSize`.
- Room migration safety: `MIGRATION_4_5` correctly alters `sync_outbox` without data loss and assigns default `createdAt = 0` for existing records.
- FIFO outbox dispatch: `SyncOutboxDao.getAllPending()` returns items in ascending creation order (`createdAt ASC`), preventing out-of-order execution when version numbers vary.
- Monotonic versioning: `AuthRepositoryImpl.stageGuestDataIntoOutbox` fetches existing versions per `(movieId, target)` to ensure version strictly increments (`(existing?.version ?: 0) + 1`).
- Preserved timestamps: Guest favorites and watchlist stage into outbox with `createdAt` corresponding to `addedAt` / `watchedAt`.

---

### Task 1: Decouple `MoviesRemoteMediator` Position from Paging Config (`:core:data`)

**Files:**
- Modify: `core/data/src/main/java/com/adauto/marvel/core/data/mediator/MoviesRemoteMediator.kt:60-70`
- Test: `core/data/src/test/java/com/adauto/marvel/core/data/mediator/MoviesRemoteMediatorTest.kt`

**Interfaces:**
- Consumes: TMDB page responses (`page: Int`)
- Produces: `MovieEntity` with position `(page - 1) * TMDB_PAGE_SIZE + index`

- [ ] **Step 1: Write failing test in `MoviesRemoteMediatorTest`**

Add unit test `load_whenPageSizeIsSmallerThanTmdbPageSize_computesPositionBasedOnTmdbConstant`:
```kotlin
@Test
fun load_whenPageSizeIsSmallerThanTmdbPageSize_computesPositionBasedOnTmdbConstant() = runTest {
    coEvery { remoteKeysDao.getKey("POPULAR") } returns MovieRemoteKeysEntity(
        label = "POPULAR",
        nextPage = 2,
        totalPages = 5,
        updatedAt = fixedClock.millis()
    )
    val movieDto = TmdbMovieDto(id = 100, title = "M100", overview = "O", voteAverage = 8.0, releaseDate = "2024")
    coEvery { api.getPopularMovies(page = 2) } returns ApiResponse.Success(
        TmdbPageResponseDto(page = 2, results = listOf(movieDto), totalPages = 5, totalResults = 100)
    )

    // Paging config has pageSize = 10 (smaller than TMDB 20)
    val stateWithSmallPage = PagingState<Int, MovieWithUserData>(
        pages = emptyList(),
        anchorPosition = null,
        config = PagingConfig(pageSize = 10),
        leadingPlaceholderCount = 0
    )

    mediator.load(LoadType.APPEND, stateWithSmallPage)

    val insertedSlot = slot<List<MovieEntity>>()
    coVerify(exactly = 1) { movieDao.upsertAll(capture(insertedSlot)) }
    // Page 2 start position must be (2 - 1) * 20 = 20, NOT (2 - 1) * 10 = 10
    assertThat(insertedSlot.captured.first().position).isEqualTo(20)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:data:testDebugUnitTest --tests "com.adauto.marvel.core.data.mediator.MoviesRemoteMediatorTest.load_whenPageSizeIsSmallerThanTmdbPageSize_computesPositionBasedOnTmdbConstant"`
Expected: FAIL with `expected:<20> but was:<10>`

- [ ] **Step 3: Implement constant TMDB page size in `MoviesRemoteMediator.kt`**

Define `private const val TMDB_PAGE_SIZE = 20` (or in companion object) and update:
```kotlin
val startPosition = (page - 1) * TMDB_PAGE_SIZE
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:data:testDebugUnitTest --tests "com.adauto.marvel.core.data.mediator.MoviesRemoteMediatorTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/data/src/main/java/com/adauto/marvel/core/data/mediator/MoviesRemoteMediator.kt core/data/src/test/java/com/adauto/marvel/core/data/mediator/MoviesRemoteMediatorTest.kt
git commit -m "fix(data): decouple MoviesRemoteMediator position calculation from Paging pageSize"
```

---

### Task 2: Room Schema Migration 4->5 with `SyncOutboxEntity.createdAt` & FIFO Ordering (`:core:database`)

**Files:**
- Modify: `core/database/src/main/java/com/adauto/marvel/core/database/entity/SyncOutboxEntity.kt`
- Modify: `core/database/src/main/java/com/adauto/marvel/core/database/MarvelDatabase.kt`
- Modify: `core/database/src/main/java/com/adauto/marvel/core/database/migrations/DatabaseMigrations.kt`
- Modify: `core/database/src/main/java/com/adauto/marvel/core/database/di/DatabaseModule.kt`
- Modify: `core/database/src/main/java/com/adauto/marvel/core/database/dao/SyncOutboxDao.kt`
- Test: `core/database/src/test/java/com/adauto/marvel/core/database/dao/SyncOutboxDaoTest.kt`
- Create: `core/database/src/test/java/com/adauto/marvel/core/database/migration/Migration4To5Test.kt`

**Interfaces:**
- Consumes: Room database version 4
- Produces: Room database version 5, `SyncOutboxEntity.createdAt: Long`, `MIGRATION_4_5`, and `getAllPending(): List<SyncOutboxEntity>` ordered by `createdAt ASC, version ASC`.

- [ ] **Step 1: Write failing test in `SyncOutboxDaoTest` and migration test `Migration4To5Test`**

In `SyncOutboxDaoTest`:
```kotlin
@Test
fun getAllPending_ordersByCreatedAtAscendingRegardlessOfVersion() = runTest {
    val earlierCreatedHigherVersion = SyncOutboxEntity(
        movieId = 1,
        target = SyncOutboxEntity.TARGET_FAVORITE,
        desired = true,
        version = 5,
        createdAt = 1000L
    )
    val laterCreatedLowerVersion = SyncOutboxEntity(
        movieId = 2,
        target = SyncOutboxEntity.TARGET_WATCHLIST,
        desired = true,
        version = 1,
        createdAt = 2000L
    )

    syncOutboxDao.upsert(laterCreatedLowerVersion)
    syncOutboxDao.upsert(earlierCreatedHigherVersion)

    val all = syncOutboxDao.getAllPending()
    assertThat(all.map { it.movieId }).containsExactly(1, 2).inOrder()
}
```

In `Migration4To5Test`:
Validate creating version 4 database, inserting sample outbox entry without `createdAt`, running `MIGRATION_4_5` to version 5, and asserting preserved data with default `createdAt = 0L`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:database:testDebugUnitTest --tests "com.adauto.marvel.core.database.migration.Migration4To5Test"`
Expected: Compilation failure or missing `MIGRATION_4_5`.

- [ ] **Step 3: Implement Room Schema Migration 4->5 & DAO Query Update**

1. Update `SyncOutboxEntity`:
```kotlin
@ColumnInfo(defaultValue = "0")
val createdAt: Long = 0L
```
2. In `MarvelDatabase.kt`: change `version = 5`.
3. In `DatabaseMigrations.kt`:
```kotlin
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `sync_outbox` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0")
    }
}
```
4. In `DatabaseModule.kt`: `.addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)`.
5. In `SyncOutboxDao.kt`:
```kotlin
@Query("SELECT * FROM sync_outbox ORDER BY createdAt ASC, version ASC")
suspend fun getAllPending(): List<SyncOutboxEntity>
```

- [ ] **Step 4: Run database tests and schema export**

Run: `./gradlew :core:database:testDebugUnitTest :core:database:kspDebugKotlin`
Verify tests pass and `5.json` is generated under `core/database/schemas/com.adauto.marvel.core.database.MarvelDatabase/5.json`.

- [ ] **Step 5: Commit**

```bash
git add core/database/
git commit -m "feat(database): migrate Room schema to v5 with sync_outbox createdAt and FIFO ordering"
```

---

### Task 3: Enforce Monotonic Versioning and Creation Timestamps in Repositories (`:core:data`)

**Files:**
- Modify: `core/data/src/main/java/com/adauto/marvel/core/data/repository/AuthRepositoryImpl.kt:152-182`
- Modify: `core/data/src/main/java/com/adauto/marvel/core/data/repository/MoviesRepositoryImpl.kt:158-171,204-217`
- Test: `core/data/src/test/java/com/adauto/marvel/core/data/repository/AuthRepositoryTest.kt`
- Test: `core/data/src/test/java/com/adauto/marvel/core/data/repository/MoviesRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `SyncOutboxDao.find(movieId, target)`
- Produces: Monotonic `version = (existing?.version ?: 0) + 1` and `createdAt` persistence.

- [ ] **Step 1: Write failing tests in `AuthRepositoryTest` and `MoviesRepositoryImplTest`**

In `AuthRepositoryTest`:
```kotlin
@Test
fun completeAuthentication_stagesGuestDataWithMonotonicVersionIncrement() = runTest {
    coEvery { authPreferences.getLastAccountId() } returns null
    every { favoriteDao.getAllFavorites() } returns flowOf(listOf(sampleFavEntity(101).copy(addedAt = 12345L)))
    every { watchedDao.getAllWatched() } returns flowOf(listOf(sampleWatchEntity(202).copy(watchedAt = 67890L)))
    
    // Existing outbox entry with version 3
    coEvery { syncOutboxDao.find(101, SyncOutboxWorker.TARGET_FAVORITE) } returns SyncOutboxEntity(
        movieId = 101,
        target = SyncOutboxWorker.TARGET_FAVORITE,
        desired = false,
        version = 3,
        createdAt = 1000L
    )
    coEvery { syncOutboxDao.find(202, SyncOutboxWorker.TARGET_WATCHLIST) } returns null
    ...
    authRepository.completeAuthentication("tok_guest")

    // Must increment to version 4 and keep original createdAt
    coVerify(exactly = 1) {
        syncOutboxDao.upsert(
            SyncOutboxEntity(movieId = 101, target = "FAVORITE", desired = true, attempts = 0, version = 4, createdAt = 1000L)
        )
    }
    // New entry must start with version 1 and use watchedAt as createdAt
    coVerify(exactly = 1) {
        syncOutboxDao.upsert(
            SyncOutboxEntity(movieId = 202, target = "WATCHLIST", desired = true, attempts = 0, version = 1, createdAt = 67890L)
        )
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:data:testDebugUnitTest --tests "com.adauto.marvel.core.data.repository.AuthRepositoryTest.completeAuthentication_stagesGuestDataWithMonotonicVersionIncrement"`
Expected: FAIL with `expected version = 4 but was: 1`.

- [ ] **Step 3: Implement monotonic versioning & timestamp preservation**

1. In `AuthRepositoryImpl.stageGuestDataIntoOutbox()`:
Query `syncOutboxDao.find(...)` for each item. Compute `val nextVersion = (existing?.version ?: 0) + 1` and `val creationTime = existing?.createdAt?.takeIf { it > 0 } ?: fav.addedAt`.
2. In `MoviesRepositoryImpl.toggleFavorite()` and `toggleWatchlist()`:
Record `val creationTime = existing?.createdAt?.takeIf { it > 0 } ?: clock.millis()`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:data:testDebugUnitTest --tests "com.adauto.marvel.core.data.repository.AuthRepositoryTest" :core:data:testDebugUnitTest --tests "com.adauto.marvel.core.data.repository.MoviesRepositoryImplTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/data/src/main/java/com/adauto/marvel/core/data/repository/AuthRepositoryImpl.kt core/data/src/main/java/com/adauto/marvel/core/data/repository/MoviesRepositoryImpl.kt core/data/src/test/java/
git commit -m "fix(data): ensure monotonic outbox versions and preserve creation timestamps"
```

---

### Task 4: Full Quality Gate Verification & Review

**Files:**
- Repository-wide quality verification

- [ ] **Step 1: Run comprehensive local test and verification suite**

Run:
```bash
./gradlew detekt lintDebug testDebugUnitTest testReleaseUnitTest koverXmlReport assembleDebug assembleRelease
```
Expected: 100% PASS, 0 Detekt issues, 0 lint errors, build SUCCESSFUL.

- [ ] **Step 2: Commit any cleanups or updated documentation**

```bash
git commit -m "docs: document Room v5 migration and outbox FIFO dispatch"
```
