# Design Document: Cinema Hub Hardening & Architecture Evolution

**Date:** 2026-10-23  
**Status:** In Review  
**Target:** Cinema Hub Android App  

---

## 1. Intent & Context

Following the migration from Marvel Comics to Cinema Hub (TMDB), a comprehensive architectural audit revealed critical data integrity bugs, security gaps, and performance inefficiencies in the database and UI layers:
1. **P0 Data Integrity:** `MovieEntity` uses `id` as single primary key, causing movies that appear in multiple categories (e.g. *Populares* and *Mais Votados*) to overwrite each other's category and position.
2. **P0 Data Loss Risk:** The database shares cache and user favorites in the same database with `.fallbackToDestructiveMigration()`, wiping user data on schema change.
3. **P0 Security (Fail-Open CI):** Missing release secrets quietly fall back to debug signing keys and fake configurations, publishing unsigned/debug artifacts to public releases.
4. **P0 Security (CSRF in OAuth):** Deep link authentication callbacks (`marvelapp://tmdb-callback`) accept any `request_token` without validating that it was initiated by the local app session.
5. **P1 Performance & UX:** Paging query executes 2 separate queries per item (N+1 query problem) on every emission and does not react to favorite toggle events without category switching.
6. **P2 Hygiene & Branding:** Residual Marvel packages, hardcoded UI strings, dead connectivity state, and Compose recomposition allocations.

This design document establishes the architectural roadmap to address these findings systematically across three phased branches.

---

## 2. Goals, Non-Goals & Anti-Goals

### Goals
- **Multi-Category Coexistence:** A movie (e.g., ID 550) must independently exist in `POPULAR` and `TOP_RATED` without overwriting row state or position.
- **Zero Data Loss on Schema Migrations:** User data (`favorite_movies` and `watched_movies`) must be protected from destructive cache wipes via explicit Room migrations and schema export.
- **Fail-Closed Release Engineering:** CI and Gradle builds must fail explicitly if release keystore or production credentials are unset.
- **CSRF-Protected OAuth Flow:** Callback tokens must match an active, unexpired `pendingRequestToken` generated during the authorization handshake.
- **0ms Reactive Heart Updates:** Eliminating the N+1 Room queries via SQL `LEFT JOIN` so that toggling a favorite updates the list immediately in real time.
- **Modern Cinema Hub Branding:** Transitioning package IDs and deep link schemes to Cinema Hub (`cinemahub://`).

### Non-Goals
- Full rewrite of network engine from Sandwich/Retrofit to Ktor.
- Distributed multi-device conflict resolution (CRDTs). A deterministic client-side outbox with additive cloud sync satisfies TMDB requirements.
- Adding empty "pass-through" UseCase boilerplate that only calls a single repository method.

### Anti-Goals
- Dropping Room cache without migrations in production.
- Breaking existing unit tests or test coverage.

---

## 3. Approaches Considered & Trade-offs

### Approach 1: Three Phased Branches by Severity Wave (Selected)
- **Wave 1 (`fix/data-integrity-and-security`):** P0 fixes (Composite PK, Room migrations without destructive fallback, CI fail-closed signing, OAuth state verification).
- **Wave 2 (`perf/room-reactive-join-and-sync`):** P1 fixes (Room `LEFT JOIN` for reactive favorites and 0 N+1 queries, `RemoteKeys` in Paging, `PullToRefresh` state alignment, CI workflow consolidation).
- **Wave 3 (`refactor/branding-compose-and-i18n`):** P2 fixes (`applicationId` & scheme update, Compose allocation optimizations in `MovieCard`, `strings.xml` extraction, domain model decoupling).
- **Trade-off:** High isolation of risk, testable deliverables at each milestone, fast integration into `main`.

### Approach 2: Granular Micro-Branches (7+ Branches)
- A separate branch per finding (`fix/movie-pk`, `fix/room-migrations`, `security/ci-fail-closed`, etc.).
- **Trade-off:** Heavy rebase overhead, frequent merge conflicts, and multiple CI pipeline runs.

### Approach 3: Monolithic Branch (`refactor/architectural-hardening`)
- All changes delivered in one single PR.
- **Trade-off:** Massive diff (>2000 lines), high risk of regressions, difficult to review.

**Decision:** Approach 1 is selected for maximum stability and incremental verification.

---

## 4. Technical Specifications by Wave

### Wave 1: Data Integrity & Security (Branch: `fix/data-integrity-and-security`)

#### 1. Composite Primary Key in `MovieEntity`
- Update [`MovieEntity`](file:///Users/adautosouza/repos/estudos/core/database/src/main/java/com/adauto/marvel/core/database/entity/MovieEntity.kt):
  ```kotlin
  @Entity(
      tableName = "movies",
      primaryKeys = ["id", "category"],
      indices = [Index(value = ["category", "position"], unique = true)]
  )
  data class MovieEntity(
      val id: Int,
      val category: String,
      val position: Int,
      ...
  )
  ```
- Increment `MarvelDatabase` version to `3` and provide `MIGRATION_2_3`:
  Recreates `movies` table with composite primary key `(id, category)` preserving existing rows.
- Enable `exportSchema = true` and configure Room schema location in `core:database/build.gradle.kts`.
- Remove `.fallbackToDestructiveMigration()` in [`DatabaseModule.kt`](file:///Users/adautosouza/repos/estudos/core/database/src/main/java/com/adauto/marvel/core/database/di/DatabaseModule.kt).
- Add DAO integration tests with in-memory Room database validating multi-category coexistence.

#### 2. Fail-Closed Release Signing & CI Hardening
- In [`app/build.gradle.kts`](file:///Users/adautosouza/repos/estudos/app/build.gradle.kts):
  Enforce strict fail-closed validation for production releases while preserving PR and local compilation (`assembleRelease` for R8 testing):
  ```kotlin
  signingConfigs {
      create("release") {
          val keystorePath = System.getenv("KEYSTORE_FILE") ?: project.findProperty("KEYSTORE_FILE") as? String
          val storePasswordEnv = System.getenv("KEYSTORE_PASSWORD") ?: project.findProperty("KEYSTORE_PASSWORD") as? String
          val keyAliasEnv = System.getenv("KEY_ALIAS") ?: project.findProperty("KEY_ALIAS") as? String
          val keyPasswordEnv = System.getenv("KEY_PASSWORD") ?: project.findProperty("KEY_PASSWORD") as? String
          val isPublishingOrCD = System.getenv("CI_RELEASE_BUILD") == "true" || 
              gradle.startParameter.taskNames.any { it.contains("bundleRelease", ignoreCase = true) }

          if (!keystorePath.isNullOrBlank() && file(keystorePath).exists() && !storePasswordEnv.isNullOrBlank()) {
              storeFile = file(keystorePath)
              storePassword = storePasswordEnv
              keyAlias = keyAliasEnv
              keyPassword = keyPasswordEnv
          } else if (isPublishingOrCD) {
              throw GradleException("Production release signing configuration (KEYSTORE_FILE/PASSWORD/ALIAS) is missing or incomplete.")
          } else {
              // Non-production release verification (PR verification / local assembleRelease) falls back to debug signing to test R8
              initWith(getByName("debug"))
          }
      }
  }
  ```
- In [`.github/workflows/main-cd.yml`](file:///Users/adautosouza/repos/estudos/.github/workflows/main-cd.yml):
  Set `CI_RELEASE_BUILD: "true"` in the build environment and remove silent fallbacks for `GOOGLE_SERVICES_JSON_B64` and `RELEASE_KEYSTORE_BASE64` during production CD runs.

#### 3. CSRF Protection for OAuth Deep Link Callbacks
- In [`AuthRepository`](file:///Users/adautosouza/repos/estudos/core/data/src/main/java/com/adauto/marvel/core/data/repository/AuthRepository.kt) & [`AuthRepositoryImpl`](file:///Users/adautosouza/repos/estudos/core/data/src/main/java/com/adauto/marvel/core/data/repository/AuthRepositoryImpl.kt):
  - Store `pendingRequestToken` with timestamp (TTL = 10 minutes).
  - In `completeAuthentication(requestToken: String)`:
    Verify `requestToken == pendingRequestToken && !isExpired(pendingTokenTimestamp)`.
    If mismatched or expired, reject with `DomainError.InvalidKey` and abort token exchange.
    Clear pending token upon completion.
- Add unit tests verifying callback token spoofing rejection.

---

### Wave 2: Performance, Reactive Queries & Sync (Branch: `perf/room-reactive-join-and-sync`)

#### 1. Eliminate N+1 Room Queries via `LEFT JOIN`
- In `core:database`, create a projection entity / data class:
  ```kotlin
  data class MovieWithUserData(
      @Embedded val movie: MovieEntity,
      val isFavorite: Boolean,
      val isWatched: Boolean
  )
  ```
- In [`MovieDao`](file:///Users/adautosouza/repos/estudos/core/database/src/main/java/com/adauto/marvel/core/database/dao/MovieDao.kt):
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
- In [`MoviesRepositoryImpl`](file:///Users/adautosouza/repos/estudos/core/data/src/main/java/com/adauto/marvel/core/data/repository/MoviesRepositoryImpl.kt):
  Directly map `MovieWithUserData` to `MovieSummary`.
  **Outcome:** 0 additional SQLite queries per page (from 40 down to 0). Toggling favorite in Room triggers Room's table invalidation tracker on `favorite_movies`, automatically refreshing the UI heart in real-time.

#### 2. Reactive In-Tab Search with Canonical `flatMapLatest`
- In [`MovieListViewModel.kt`](file:///Users/adautosouza/repos/estudos/feature/movies/src/main/java/com/adauto/marvel/feature/movies/MovieListViewModel.kt):
  Structure the flow as:
  ```kotlin
  val moviesFlow: Flow<PagingData<MovieSummary>> = combine(_selectedCategory, _searchQuery) { cat, q -> cat to q }
      .flatMapLatest { (category, query) ->
          getCategoryPagingFlow(category).map { pagingData ->
              if (query.isBlank()) pagingData
              else pagingData.filter { it.title.contains(query.trim(), ignoreCase = true) }
          }
      }
      .cachedIn(viewModelScope)
  ```

#### 3. True `PullToRefresh` State Binding
- Remove `delay(400)` in `MovieListViewModel.kt`.
- In `MovieListScreen.kt`, bind `PullToRefreshBox(isRefreshing = movies.loadState.refresh is LoadState.Loading, ...)` so the loading spinner reflects true network/mediator activity.

#### 4. Workflow Consolidation
- Delete obsolete [`.github/workflows/release.yml`](file:///Users/adautosouza/repos/estudos/.github/workflows/release.yml) (which had legacy `FIREBASE_CLI_TOKEN`).
- Ensure [`.github/workflows/main-cd.yml`](file:///Users/adautosouza/repos/estudos/.github/workflows/main-cd.yml) generates dynamic `versionCode = ${{ github.run_number }}` and handles both Release tagging and App Distribution.

---

### Wave 3: Branding, Compose Optimizations & i18n (Branch: `refactor/branding-compose-and-i18n`)

#### 1. Cinema Hub Branding & Deep Link Migration
- Update `applicationId` to `com.adauto.cinemahub` in `app/build.gradle.kts`.
- Update manifest scheme from `marvelapp` to `cinemahub`:
  `<data android:scheme="cinemahub" android:host="tmdb-callback" />`.
- Update TMDB callback redirect URL.

#### 2. Compose Recomposition Performance
- In [`MovieCard.kt`](file:///Users/adautosouza/repos/estudos/core/ui/src/main/java/com/adauto/marvel/core/ui/component/MovieCard.kt):
  Extract static `Brush.verticalGradient` and color lists into top-level constants to avoid object allocations in every card recomposition.
- In `LazyVerticalGrid` items, add `contentType = { "MovieCard" }` for optimal view recycling.

#### 3. String Resources & Decoupling
- Extract hardcoded strings to `strings.xml`.
- Move `MovieCategory.displayName` out of `:core:model` or provide string resource IDs (`@StringRes displayNameRes: Int`) for proper localization.
- Connect `ConnectivityManager` flow to update `isOffline` in `MovieListViewModel` so `OfflineBanner` is active when offline.
- Decouple `TmdbAccountDto` from `AuthRepository` interface by returning domain model `AccountProfile`.

---

## 5. Verification & Testing Strategy
- Unit tests for all repository, viewmodel, and DAO changes.
- Integration tests with in-memory Room database for composite keys and migrations.
- Local command: `./gradlew detekt lintDebug testDebugUnitTest assembleDebug assembleRelease :app:assembleDebugAndroidTest`.
- Full CI/CD pass on GitHub Actions for each branch.
