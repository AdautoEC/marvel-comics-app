# Wave 2: Performance, Reactive Queries & CI Consolidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate N+1 Room queries (reducing 40 SQLite queries per page down to 0) to achieve instant, reactive list heart toggles via SQL `LEFT JOIN`, bind PullToRefresh directly to real Paging load state without artificial delays, and consolidate GitHub Actions CI workflows.

**Architecture:**
1. In `:core:database`, define a projection `MovieWithUserData` combining `MovieEntity` with favorite and watchlist boolean indicators. Update `MovieDao.pagingSource` to a SQL `LEFT JOIN` on `favorite_movies` and `watched_movies`. Because Room's invalidation tracker observes all joined tables, toggling a favorite or watchlist item automatically invalidates the paging source, causing the UI heart icon to update immediately without changing tabs or refetching from network.
2. Update `MoviesRemoteMediator` to operate on `RemoteMediator<Int, MovieWithUserData>` and remove the 40 `.first()` queries per page emission in `MoviesRepositoryImpl`.
3. In `:feature:movies`, remove artificial `delay(400)` in `MovieListViewModel.RefreshRequested`. Directly bind `PullToRefreshBox` `isRefreshing` to `movies.loadState.refresh is LoadState.Loading` combined with sync jobs.
4. Delete obsolete `.github/workflows/release.yml` and streamline `.github/workflows/main-cd.yml`.

**Tech Stack:** Kotlin 2.0.21, AndroidX Room 2.6.1, AndroidX Paging 3.3.2, Jetpack Compose Material 3 (PullToRefreshBox), GitHub Actions.

**Spec:** [`docs/superpowers/specs/2026-10-23-cinema-hub-hardening-design.md`](file:///Users/adautosouza/repos/estudos/docs/superpowers/specs/2026-10-23-cinema-hub-hardening-design.md)

## Global Constraints

- **0ms Reactive Heart Updates:** Toggling a favorite or watchlist item must update the list UI state immediately through Room table observation without requiring tab switching or page reload.
- **Zero N+1 Queries per Page:** `MoviesRepositoryImpl.movies(category)` must not call `favoriteDao.isFavorite().first()` or `watchedDao.isWatched().first()` per item inside `pagingData.map`. All user state must be supplied in the initial SQL query.
- **Natural LoadState-driven Refreshing:** Pull-to-refresh spinner must accurately reflect asynchronous data fetching / mediator refresh without artificial `delay()` calls.
- **Detekt & Lint Cleanliness:** Zero new Detekt code smells, zero lint errors, 100% test pass rate across all modules.
- **Documentation Preservation:** All plans and temporary superpower documents remain local in `docs/` and `.superpowers/` (both in `.gitignore`).

## Review Focus

1. **Reactive Heart Update on Favorite Toggle:** Toggling a movie as favorite updates `favorite_movies`. The `MovieDao.pagingSource` query joining `favorite_movies` must immediately emit the updated `isFavorite = true` through Room's InvalidationTracker without requiring a manual refresh.
2. **Paging RemoteMediator with Joined Projection:** When `MoviesRemoteMediator` loads page 2 or refreshes page 1, `state.lastItemOrNull()` accesses `item.movie.position` to derive the next page correctly without ClassCastException or null pointer errors.
3. **Detail Screen Sync with List:** Favoriting a movie from the Detail screen (`MovieDetailScreen`) updates `favorite_movies` in Room. Navigating back to `MovieListScreen` must immediately show the filled heart icon on that movie card without delay.
4. **Pull-to-Refresh Spinner Dismissal:** Initiating pull-to-refresh triggers mediator refresh and cloud sync. When both complete, the refresh spinner disappears promptly without getting stuck or depending on a hardcoded 400ms timer.
5. **CI Workflow Hygiene:** Removing `release.yml` leaves `main-cd.yml` as the sole authority for releases, preventing duplicate deployment attempts or failures from deprecated CLI tokens.

---

### Task 1: Room `LEFT JOIN` Projection & Elimination of N+1 Queries (:core:database & :core:data)

**Files:**
- Create: `core/database/src/main/java/com/adauto/marvel/core/database/model/MovieWithUserData.kt`
- Modify: `core/database/src/main/java/com/adauto/marvel/core/database/dao/MovieDao.kt`
- Modify: `core/data/src/main/java/com/adauto/marvel/core/data/mediator/MoviesRemoteMediator.kt`
- Modify: `core/data/src/main/java/com/adauto/marvel/core/data/repository/MoviesRepositoryImpl.kt`
- Test: `core/database/src/test/java/com/adauto/marvel/core/database/dao/MovieDaoReactiveJoinTest.kt`
- Test: `core/data/src/test/java/com/adauto/marvel/core/data/repository/MoviesRepositoryReactiveTest.kt`

**Interfaces:**
- Consumes: `MovieEntity`, `FavoriteMovieEntity`, `WatchedMovieEntity`, `MovieDao`
- Produces: `MovieWithUserData`, `MovieDao.pagingSource(category): PagingSource<Int, MovieWithUserData>`, `MoviesRepositoryImpl.getMoviesByCategory` without N+1 queries

- [ ] **Step 1: Create `MovieWithUserData` in `:core:database`**

In `core/database/src/main/java/com/adauto/marvel/core/database/model/MovieWithUserData.kt`:
```kotlin
package com.adauto.marvel.core.database.model

import androidx.room.Embedded
import com.adauto.marvel.core.database.entity.MovieEntity

data class MovieWithUserData(
    @Embedded val movie: MovieEntity,
    val isFavorite: Boolean,
    val isWatched: Boolean
)
```

- [ ] **Step 2: Update `MovieDao.pagingSource` to `LEFT JOIN` query**

In `core/database/src/main/java/com/adauto/marvel/core/database/dao/MovieDao.kt`:
```kotlin
@Query("""
    SELECT m.*, 
           (f.movieId IS NOT NULL) AS isFavorite, 
           (w.movieId IS NOT NULL) AS isWatched
    FROM movies m
    LEFT JOIN favorite_movies f ON m.id = f.movieId
    LEFT JOIN watched_movies w ON m.id = w.movieId
    WHERE m.category = :category
    ORDER BY m.position ASC
""")
fun pagingSource(category: String): PagingSource<Int, MovieWithUserData>
```

- [ ] **Step 3: Write failing in-memory test for reactive table invalidation**

In `core/database/src/test/java/com/adauto/marvel/core/database/dao/MovieDaoReactiveJoinTest.kt`:
Test that:
1. Inserting a movie into `movies` with empty `favorite_movies` returns `isFavorite = false`.
2. Inserting an entry into `favorite_movies` for that movie updates `isFavorite = true` in the query result.
3. Deleting from `favorite_movies` reverts `isFavorite = false`.

- [ ] **Step 4: Update `MoviesRemoteMediator` to use `MovieWithUserData`**

In `core/data/src/main/java/com/adauto/marvel/core/data/mediator/MoviesRemoteMediator.kt`:
Update generic type parameter:
```kotlin
class MoviesRemoteMediator(
    private val api: TmdbApi,
    private val database: MarvelDatabase,
    private val category: String,
    private val clock: Clock = Clock.systemUTC(),
    private val cacheTtlHours: Long = 6L
) : RemoteMediator<Int, MovieWithUserData>()
```
In `load`:
```kotlin
LoadType.APPEND -> {
    val lastItem = state.lastItemOrNull()
        ?: return MediatorResult.Success(endOfPaginationReached = true)
    (lastItem.movie.position / state.config.pageSize) + 2
}
```

- [ ] **Step 5: Eliminate N+1 queries in `MoviesRepositoryImpl`**

In `core/data/src/main/java/com/adauto/marvel/core/data/repository/MoviesRepositoryImpl.kt`:
Replace lines 62-68 with:
```kotlin
return pager.flow.map { pagingData ->
    pagingData.map { item ->
        item.movie.toMovieSummary(isFavorite = item.isFavorite, isWatched = item.isWatched)
    }
}
```

- [ ] **Step 6: Run tests to verify all pass**

Run: `./gradlew :core:database:testDebugUnitTest :core:data:testDebugUnitTest :core:database:detekt :core:data:detekt`
Expected: PASS with 0 errors.

- [ ] **Step 7: Commit Task 1**

```bash
git add core/database/ core/data/
git commit -m "perf(database,data): eliminate N+1 queries via Room LEFT JOIN for instant reactive heart updates"
```

---

### Task 2: Reactive Pull-to-Refresh & In-Tab Search Optimization (:feature:movies)

**Files:**
- Modify: `feature/movies/src/main/java/com/adauto/marvel/feature/movies/MovieListViewModel.kt`
- Modify: `feature/movies/src/main/java/com/adauto/marvel/feature/movies/MovieListScreen.kt`
- Test: `feature/movies/src/test/java/com/adauto/marvel/feature/movies/MovieListViewModelTest.kt`

**Interfaces:**
- Consumes: `MovieListAction.RefreshRequested`, `movies.loadState.refresh`
- Produces: True loading indicator bound to real network / sync state without fake delays

- [ ] **Step 1: Remove artificial `delay(400)` in `MovieListViewModel`**

In `feature/movies/src/main/java/com/adauto/marvel/feature/movies/MovieListViewModel.kt`:
In `MovieListAction.RefreshRequested`:
Remove `delay(400)`. Run `syncCloudWithLocal()` if authenticated, and clear `isRefreshing` immediately upon completion of the coroutine.

- [ ] **Step 2: Bind `PullToRefreshBox` in `MovieListScreen`**

In `feature/movies/src/main/java/com/adauto/marvel/feature/movies/MovieListScreen.kt`:
Ensure `isRefreshing` evaluates true when either `uiState.isRefreshing` is true or `movies.loadState.refresh is LoadState.Loading` (and items exist).
Ensure pull-to-refresh spinner dismisses naturally when loading completes.

- [ ] **Step 3: Run unit tests and UI tests in `:feature:movies`**

Run: `./gradlew :feature:movies:testDebugUnitTest :feature:movies:detekt`
Expected: PASS with 0 errors.

- [ ] **Step 4: Commit Task 2**

```bash
git add feature/movies/
git commit -m "fix(ui): remove artificial delay from pull-to-refresh and drive spinner from real load state"
```

---

### Task 3: CI Workflow Consolidation (.github/workflows)

**Files:**
- Delete: `.github/workflows/release.yml`
- Modify: `.github/workflows/main-cd.yml`

**Interfaces:**
- Consumes: `github.run_number`
- Produces: Unified CD pipeline with dynamic `versionCode`

- [ ] **Step 1: Delete obsolete `release.yml`**

Delete `.github/workflows/release.yml`.

- [ ] **Step 2: Set dynamic `versionCode` in `main-cd.yml`**

In `.github/workflows/main-cd.yml`:
Under `Build Minified Release APK & Bundle`, pass dynamic `VERSION_CODE`:
```yaml
- name: Build Minified Release APK & Bundle
  env:
    CI_RELEASE_BUILD: "true"
    VERSION_CODE: ${{ github.run_number }}
  run: ./gradlew assembleRelease bundleRelease -Pandroid.injected.version.code=${{ github.run_number }}
```

- [ ] **Step 3: Validate workflow syntax and local build**

Run: `./gradlew help`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit Task 3**

```bash
git add .github/workflows/
git commit -m "ci: consolidate CD pipelines by removing legacy release.yml and configuring dynamic versionCode"
```

---

### Task 4: Full Quality Verification Suite for Wave 2

**Files:**
- All changed files in Wave 2.

**Verification Commands:**
- [ ] **Step 1: Run static analysis and lint**
  `./gradlew detekt lintDebug`
  Expected: BUILD SUCCESSFUL, 0 code smells, 0 lint errors.

- [ ] **Step 2: Run all unit tests**
  `./gradlew testDebugUnitTest`
  Expected: All unit tests pass across all modules.

- [ ] **Step 3: Assemble debug and test APKs**
  `./gradlew assembleDebug assembleRelease :app:assembleDebugAndroidTest`
  Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Verify working tree clean**
  `git status`
  Expected: Clean working tree.
