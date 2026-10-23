# TMDB Movies App (Cinema Hub: Movies, Favorites & Watched) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Evolve the app from Marvel Comics into a modern, production-grade Cinema Hub Movies App powered by TMDB, featuring Popular/Top-Rated movies, offline-first Favorites and Watched status stored in Room 2.6, and Firebase integration (Remote Config, Crashlytics, App Check / Play Integrity, Performance Monitoring).

**Architecture:** Clean Architecture + MVI/UDF with Offline-First Reactive Storage. Unidirectional Data Flow in Jetpack Compose, Room 2.6 DAOs for caching and user interaction (Favorites & Watched), AndroidX Paging 3 `RemoteMediator` for page-based infinite scroll, Skydoves Sandwich for type-safe Retrofit calls, and Android KeyStore AES-256-GCM for encrypted credential delivery via Firebase Remote Config.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose (BOM 2024.10.01), Material 3, AndroidX Paging 3, Room 2.6, Hilt 2.52, Skydoves Sandwich 2.0.10, Kotlinx Serialization, Coil 2.7.0, Firebase (Remote Config, Crashlytics, Performance, App Check), Detekt 1.23.7.

## Global Constraints

- **Zero Hardcoded Secrets in Git:** TMDB API Key and Read Access Token must come exclusively from Firebase Remote Config / Android Keystore encrypted storage (or `local.properties` in development).
- **Architecture Governance:** Coil `AsyncImage` strictly forbidden outside `:core:ui` (enforced via Detekt rule).
- **Deterministic Pagination:** Room cache maintains deterministic `position` ordering to prevent list jumping across page boundaries.
- **Offline-First:** All movie listings, details, favorite toggles, and watched states must function 100% offline without network connectivity once cached.
- **Aspect Ratio:** Movie poster artwork retains standard 2:3 aspect ratio (`ComicCard`/`MovieCard`) with gradient scrim overlays.
- **Quality Gates:** 100% pass on `./gradlew detekt lintDebug testDebugUnitTest assembleDebug assembleRelease`.

---

## Tasks Overview

- [x] **Task 1: Core Model Evolution** — Adapt domain models (`MovieSummary`, `MovieDetail`, `Genre`, `CastMember`, `MovieFilter`) and update serialization contracts.
- [x] **Task 2: Core Database Layer** — Room 2.6 entities & DAOs for `movies`, `favorite_movies`, `watched_movies`, and `remote_keys`.
- [x] **Task 3: Core Network & TMDB Service** — TMDB API service (`/3/movie/popular`, `/3/movie/top_rated`, `/3/movie/{id}`), `TmdbAuthInterceptor` with `api_key` injection, and DTO mappings.
- [x] **Task 4: Core Data Layer** — Paging 3 `MoviesRemoteMediator` with page-based pagination, and `MoviesRepository` exposing reactive Flows for catalog, favorites, and watched lists.
- [x] **Task 5: Core UI & Design System Updates** — Update `MovieCard`, `MovieCover`, `FavoriteButton`, and `WatchedBadge` with accessible semantics.
- [x] **Task 6: Feature Movies List (Home Screen)** — Category filter tabs (Popular, Top Rated, Favoritos, Assistidos), 2-column grid, pull-to-refresh, shimmer skeletons.
- [x] **Task 7: Feature Movie Detail Screen** — Parallax backdrop hero poster, stats row (Rating, Runtime, Release Date), Synopsis, Cast chips, Favorite & Watched toggle actions.
- [x] **Task 8: Firebase Integration & Secrets in CI** — Firebase Remote Config (`tmdb_api_key`), Crashlytics, App Check / Play Integrity, Performance, and CI pipeline secrets wiring.
- [ ] **Task 9: End-to-End Verification & Whole-Branch Review** — Full build verification (`detekt`, `lintDebug`, `testDebugUnitTest`, `assembleRelease`) and PR update.

---

## Task Details

### Task 1: Core Model Evolution

**Files to modify/create:**
- `core/model/src/main/java/com/adauto/marvel/core/model/MovieSummary.kt`
- `core/model/src/main/java/com/adauto/marvel/core/model/MovieDetail.kt`
- `core/model/src/main/java/com/adauto/marvel/core/model/MovieFilter.kt`
- `core/model/src/test/java/com/adauto/marvel/core/model/MovieModelTest.kt`

- [x] Write unit test in `MovieModelTest.kt` verifying model construction, formatted release dates, and ratings.
- [x] Create `@Immutable data class MovieSummary(val id: Int, val title: String, val posterUrl: String?, val backdropUrl: String?, val voteAverage: Double, val releaseDate: String?, val isFavorite: Boolean = false, val isWatched: Boolean = false)`.
- [x] Create `@Immutable data class MovieDetail(val id: Int, val title: String, val overview: String, val posterUrl: String?, val backdropUrl: String?, val voteAverage: Double, val runtime: Int?, val releaseDate: String?, val genres: List<String>, val cast: List<String>, val isFavorite: Boolean = false, val isWatched: Boolean = false)`.
- [x] Create `enum class MovieCategory { POPULAR, TOP_RATED, FAVORITES, WATCHED }`.
- [x] Run `./gradlew :core:model:testDebugUnitTest` and verify.
- [x] Commit: `feat(core:model): define MovieSummary, MovieDetail and MovieCategory domain models`.

---

### Task 2: Core Database Layer (Room 2.6 with Favorites & Watched)

**Files to modify/create:**
- `core/database/src/main/java/com/adauto/marvel/core/database/entity/MovieEntity.kt`
- `core/database/src/main/java/com/adauto/marvel/core/database/entity/FavoriteMovieEntity.kt`
- `core/database/src/main/java/com/adauto/marvel/core/database/entity/WatchedMovieEntity.kt`
- `core/database/src/main/java/com/adauto/marvel/core/database/entity/RemoteKeyEntity.kt`
- `core/database/src/main/java/com/adauto/marvel/core/database/dao/MovieDao.kt`
- `core/database/src/main/java/com/adauto/marvel/core/database/dao/FavoriteDao.kt`
- `core/database/src/main/java/com/adauto/marvel/core/database/dao/WatchedDao.kt`
- `core/database/src/main/java/com/adauto/marvel/core/database/MarvelDatabase.kt`
- `core/database/src/test/java/com/adauto/marvel/core/database/MovieDaoTest.kt`

- [x] Write unit tests for DAOs with in-memory Room database.
- [x] Define `MovieEntity` with `position` ordering, `title`, `posterPath`, `backdropPath`, `voteAverage`, `releaseDate`, `category`.
- [x] Define `FavoriteMovieEntity(val movieId: Int, val addedAt: Long)` and `FavoriteDao` with `@Upsert`, `deleteFavorite(id)`, `isFavorite(id): Flow<Boolean>`, `getFavorites(): Flow<List<MovieEntity>>`.
- [x] Define `WatchedMovieEntity(val movieId: Int, val watchedAt: Long)` and `WatchedDao` with `@Upsert`, `deleteWatched(id)`, `isWatched(id): Flow<Boolean>`, `getWatched(): Flow<List<MovieEntity>>`.
- [x] Update `MarvelDatabase` (or `AppDatabase`) entities and version migration.
- [x] Run `./gradlew :core:database:testDebugUnitTest` and verify.
- [x] Commit: `feat(core:database): add MovieEntity, FavoriteDao and WatchedDao for offline persistence`.

---

### Task 3: Core Network & TMDB API Service with Sandwich

**Files to modify/create:**
- `core/network/src/main/java/com/adauto/marvel/core/network/api/TmdbApi.kt`
- `core/network/src/main/java/com/adauto/marvel/core/network/model/TmdbMovieDto.kt`
- `core/network/src/main/java/com/adauto/marvel/core/network/model/TmdbPageResponseDto.kt`
- `core/network/src/main/java/com/adauto/marvel/core/network/model/TmdbMovieDetailDto.kt`
- `core/network/src/main/java/com/adauto/marvel/core/network/interceptor/TmdbAuthInterceptor.kt`
- `core/network/src/main/java/com/adauto/marvel/core/network/di/NetworkModule.kt`
- `core/network/src/test/java/com/adauto/marvel/core/network/TmdbApiTest.kt`

- [x] Add MockWebServer unit test with TMDB sample JSON fixture.
- [x] Implement `TmdbAuthInterceptor` injecting `api_key` query parameter transparently.
- [x] Define `TmdbApi` with:
  - `getPopularMovies(@Query("page") page: Int): ApiResponse<TmdbPageResponseDto<TmdbMovieDto>>`
  - `getTopRatedMovies(@Query("page") page: Int): ApiResponse<TmdbPageResponseDto<TmdbMovieDto>>`
  - `getMovieDetail(@Path("movie_id") id: Int, @Query("append_to_response") append: String = "credits"): ApiResponse<TmdbMovieDetailDto>`
- [x] Update `NetworkModule` with `BASE_URL = "https://api.themoviedb.org/"` and isolated `@ImageOkHttpClient`.
- [x] Run `./gradlew :core:network:testDebugUnitTest` and verify.
- [x] Commit: `feat(core:network): integrate TMDB API with Sandwich and TmdbAuthInterceptor`.

---

### Task 4: Core Data Layer (Paging 3 RemoteMediator & MoviesRepository)

**Files to modify/create:**
- `core/data/src/main/java/com/adauto/marvel/core/data/mediator/MoviesRemoteMediator.kt`
- `core/data/src/main/java/com/adauto/marvel/core/data/mapper/MovieMappers.kt`
- `core/data/src/main/java/com/adauto/marvel/core/data/repository/MoviesRepository.kt`
- `core/data/src/main/java/com/adauto/marvel/core/data/repository/MoviesRepositoryImpl.kt`
- `core/data/src/test/java/com/adauto/marvel/core/data/MoviesRepositoryTest.kt`

- [x] Write unit tests for `MoviesRemoteMediator` testing `page` increment and cache TTL.
- [x] Write unit tests for `MoviesRepositoryImpl` testing `toggleFavorite(id)` and `toggleWatched(id)`.
- [x] Implement `MoviesRemoteMediator` handling `page` (1, 2, 3...) with Room transaction.
- [x] Implement `MoviesRepository`:
  - `fun movies(category: MovieCategory): Flow<PagingData<MovieSummary>>`
  - `fun getMovieDetail(id: Int): Outcome<MovieDetail>`
  - `suspend fun toggleFavorite(movie: MovieSummary)`
  - `suspend fun toggleWatched(movie: MovieSummary)`
  - `fun observeFavorites(): Flow<List<MovieSummary>>`
  - `fun observeWatched(): Flow<List<MovieSummary>>`
- [x] Run `./gradlew :core:data:testDebugUnitTest` and verify.
- [x] Commit: `feat(core:data): implement MoviesRemoteMediator and reactive MoviesRepository`.

---

### Task 5: Core UI Components

**Files to modify/create:**
- `core/ui/src/main/java/com/adauto/marvel/core/ui/component/MovieCard.kt`
- `core/ui/src/main/java/com/adauto/marvel/core/ui/component/FavoriteButton.kt`
- `core/ui/src/main/java/com/adauto/marvel/core/ui/component/WatchedBadge.kt`
- `core/ui/src/androidTest/java/com/adauto/marvel/core/ui/MovieCardTest.kt`

- [x] Update `MovieCard` with 2:3 aspect ratio, title, rating star badge, and favorite toggle icon.
- [x] Ensure `LocalInspectionMode.current` preview placeholder is present for Coil.
- [x] Run `./gradlew :core:ui:testDebugUnitTest` and verify.
- [x] Commit: `feat(core:ui): build MovieCard with rating chip, favorite icon, and 2:3 aspect ratio`.

---

### Task 6: Feature Movies List (Home Screen with Category Tabs)

**Files to modify/create:**
- `feature/comics/src/main/java/com/adauto/marvel/feature/comics/ComicListContract.kt` (evolve to `MovieListContract.kt`)
- `feature/comics/src/main/java/com/adauto/marvel/feature/comics/ComicListViewModel.kt`
- `feature/comics/src/main/java/com/adauto/marvel/feature/comics/ComicListScreen.kt`
- `feature/comics/src/test/java/com/adauto/marvel/feature/comics/MovieListViewModelTest.kt`

- [x] Implement Category Filter Tabs: "Populares", "Mais Votados", "Favoritos", "Assistidos".
- [x] Expose UDF actions: `CategorySelected(category)`, `MovieClicked(id)`, `FavoriteToggled(movie)`.
- [x] Display 2-column grid with pull-to-refresh and shimmer placeholder.
- [x] Run `./gradlew :feature:comics:testDebugUnitTest` and verify.
- [x] Commit: `feat(feature:movies): add category filter tabs and reactive favorites list in HomeScreen`.

---

### Task 7: Feature Movie Detail Screen

**Files to modify/create:**
- `feature/detail/src/main/java/com/adauto/marvel/feature/detail/ComicDetailContract.kt` (evolve to `MovieDetailContract.kt`)
- `feature/detail/src/main/java/com/adauto/marvel/feature/detail/ComicDetailViewModel.kt`
- `feature/detail/src/main/java/com/adauto/marvel/feature/detail/ComicDetailScreen.kt`
- `feature/detail/src/test/java/com/adauto/marvel/feature/detail/MovieDetailViewModelTest.kt`

- [x] Parallax hero backdrop artwork with vertical dark gradient scrim.
- [x] Action buttons: "Favorito" (❤️) and "Já assisti" (👁️ / ✅) with instant state feedback.
- [x] Stats pills row: Rating (⭐ 8.5), Runtime (124 min), Release Year (2026).
- [x] Genres row and Cast member chips.
- [x] Run `./gradlew :feature:detail:testDebugUnitTest` and verify.
- [x] Commit: `feat(feature:detail): add parallax backdrop, action pills, cast chips, and watch state`.

---

### Task 8: Firebase Integration (Project `marvel-comics-app-86f2a`)

**Files to modify/create:**
- `app/google-services.json`
- `app/build.gradle.kts`
- `.github/workflows/pr.yml`
- `.github/workflows/release.yml`

- [x] Place downloaded `google-services.json` from `marvel-comics-app-86f2a`.
- [x] Enable Firebase Performance plugin and dependencies.
- [x] Wire `secrets.TMDB_API_KEY` into CI workflows.
- [x] Verify `./gradlew assembleDebug assembleRelease`.
- [x] Commit: `feat(firebase): configure project marvel-comics-app-86f2a, performance and crashlytics`.

---

### Task 9: End-to-End Quality Verification

- [x] Run full project verification: `./gradlew detekt lintDebug testDebugUnitTest assembleDebug assembleRelease`.
- [x] Ensure 0 Detekt smells, 0 Lint errors, 100% test pass.
- [ ] Push to GitHub and update PR.
- [ ] Push to GitHub and update PR.
