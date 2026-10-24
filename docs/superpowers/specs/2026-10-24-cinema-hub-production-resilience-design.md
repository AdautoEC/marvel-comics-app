# Cinema Hub: Production Resilience, Quality Gates & Sync Architecture

**Date:** 2026-10-24  
**Author:** Antigravity & Engineering Team  
**Status:** PROPOSED (Incorporating Final Concurrency & Identity Guards)

---

## 1. Intent & The "Why"

Following the architectural review of HEAD `220d412`, this document defines the production-grade implementation of remaining critical systems in Cinema Hub. The goal is to eliminate cosmetic gates, prevent silent authentication failures, ensure reliable offline-first bidirectional synchronization without resurrecting deleted data, harden TMDB credential distribution against network stalls and 401s, establish verifiable CI/CD quality gates, and maintain complete documentation integrity.

---

## 2. Goals, Non-Goals & Anti-Goals

### 2.1 Goals
1. **Immediate Repository Hygiene & Docs Un-ignore (Phase 1):**
   - Un-ignore documentation: remove `docs/` from `.gitignore` (tracking `docs/superpowers/specs/`, `docs/adr/`).
   - Sanitize `README.md`: replace all absolute `file:///Users/...` links with relative repository paths, remove static "Detekt 0 Code Smells" badge, and remove unverified coverage/test claims until measured by Kover.
   - Strict Firebase CI Validation: validate `package_name == com.adauto.cinemahub` AND `mobilesdk_app_id == secrets.FIREBASE_APP_ID` in `app/google-services.json`. Remove ad-hoc Python monkeypatching and eliminate `continue-on-error: true` on Firebase App Distribution.
   - Test Lab Gate: remove the exit 0 quota workaround; support audited `workflow_dispatch` input `override_test_lab: boolean` with clear warning logs when quota is constrained.
   - Release APK & AAB Leak Gate: add CI step unzipping release APK/AAB and scanning dex and resources to prove TMDB secret values are not baked into release binaries.
2. **Resilient TMDB Credential Management (Phase 2):**
   - In-memory process cache with stale-while-revalidate 24h TTL.
   - Prewarm credentials in `MarvelApp.onCreate` using an injected `@ApplicationScope`.
   - Remote Config: explicit `FirebaseRemoteConfigSettings`; forced refetch calls `fetch(0)` followed by `activate()` via `suspendCancellableCoroutine`, catching `FirebaseRemoteConfigFetchThrottledException`.
   - Single-flight `TmdbAuthenticator`: parses response body via `kotlinx.serialization` strictly for TMDB `status_code == 7` (Invalid API key), deduplicating concurrent 401s.
   - Header & Query Param: support `Authorization: Bearer <token>` when `tmdb_read_access_token` is present; fallback to `HttpUrl.Builder.setQueryParameter("api_key", key)`.
   - Immediate Interceptor Rejection: if credentials unavailable, `TmdbAuthInterceptor` throws `KeysUnavailableException : IOException` (mapped to `DomainError.KeysUnavailable` in `ErrorMapper`) rather than proceeding without credentials.
   - Zero plaintext fallback in release builds: `buildConfigField` for local fallback enabled strictly on `buildTypes.debug`.
3. **Robust Quality Gates & Canary Verification (Phase 3):**
   - Full Detekt ruleset (`buildUponDefaultConfig = true`) with `ignoreAnnotated: ['Composable']` strictly on `MagicNumber`, per-module baseline (`baseline = file("$projectDir/detekt-baseline.xml")`), and `build.maxIssues: 0`.
   - Kover coverage plugin across modules, measuring line and branch coverage while excluding generated code (`*_Factory`, `Hilt_*`, `*Database_Impl`, `BuildConfig`, Previews).
   - Functional `:benchmark` module configured as `com.android.test` targeting `:app`'s `benchmark` build type (explicit `signingConfig = signingConfigs.getByName("debug")` and `matchingFallbacks = ["release"]`) with `androidx.baselineprofile` plugin. Root UI hierarchy configured with `Modifier.semantics { testTagsAsResourceId = true }`.
   - Dynamic release notes generated from actual test XML reports and artifact file sizes.
   - R8 `mapping.txt` kept strictly private as an Actions artifact.
   - Canary Validation: prove gates fail by verifying a canary PR with deliberate Detekt, Kover, and Lint violations is blocked by branch protection.
4. **Offline-First Sync Outbox & Schema Migration 4 (Phase 4):**
   - Single `MIGRATION_3_4` establishing `movie_remote_keys` and `sync_outbox`.
   - Outbox schema: `(movieId INTEGER, target TEXT, desired INTEGER, attempts INTEGER, lastError TEXT, version INTEGER, PRIMARY KEY (movieId, target))`. Default values match entity annotations exactly (`@ColumnInfo(defaultValue = "0")`, `@ColumnInfo(defaultValue = "1")`).
   - Compare-and-delete: `DELETE FROM sync_outbox WHERE movieId = :id AND target = :target AND version = :capturedVersion`.
   - Concurrency-safe failure updates: `UPDATE sync_outbox SET attempts = attempts + 1, lastError = :error WHERE movieId = :id AND target = :target AND version = :capturedVersion`.
   - Outbox write gating: outbox is written ONLY when authenticated. In guest mode, mutations are written strictly to local user tables.
   - Deterministic Failure Handling:
     1. In a Room transaction, run `DELETE FROM sync_outbox WHERE movieId = :id AND target = :target AND version = :capturedVersion`.
     2. If `affectedRows == 1`:
        - If `desired == true` (failed addition): revert local state by deleting the local row in `favorite_movies` / `watched_movies`.
        - If `desired == false` (failed removal): local row was already deleted; trigger background pull to authoritatively restore the item from TMDB.
     3. If `affectedRows == 0`: a newer mutation occurred during push; do nothing locally.
     4. After transaction commits, emit a one-time failure event to the UI channel.
   - Cross-account identity guard: track `lastAccountId` in an unencrypted DataStore preference separate from the session blob.
     - Survives session 401 (`clearSession()`), keeping outbox entries intact for re-login into the same account.
     - Upon login, if `lastAccountId != null && lastAccountId != newAccountId`, purge outbox and local user tables before staging guest items. Cleared only on explicit logout.
   - Toggles execute as single atomic Room transactions mutating local user tables and `sync_outbox` simultaneously.
   - Sync Concurrency: `SyncCoordinator` singleton with Coroutine `Mutex` shared by `SyncOutboxWorker` and `moviesRepository.syncCloudWithLocal()`.
   - `WorkManager` integration with `hilt-work`, `Configuration.Provider` in `MarvelApp`, and native exponential backoff with `MAX_ATTEMPTS = 5` ceiling.
   - RemoteKeys Recovery: `MoviesRemoteMediator.initialize()` triggers `LAUNCH_INITIAL_REFRESH` if remote key is missing for cached category; `REFRESH` clears entities and keys in the same transaction.
   - Guest Mode Migration on Login: `completeAuthentication()` enqueues existing local favorites/watchlist into `sync_outbox` (`desired = true`, `version = 1`) before remote pull.
   - Logout: cancels `SyncOutboxWorker` unique work (`cancelUniqueWork`), attempts a 3-second outbox flush via `withTimeoutOrNull(3_000L)`, revokes session on TMDB server (`DELETE /3/authentication/session`), and clears local data, outbox, and `lastAccountId`.
   - OAuth CSRF hardening: consume `pendingRequestToken` in `SessionStorage` only on match or expiration. Results dispatched to UI via ViewModel Channel.

### 2.2 Non-Goals
- Remote search via TMDB API endpoint (deferred to a dedicated follow-up PR).
- Major library upgrades (AGP, Kotlin 2.1, Compose BOM, Coil 3) (deferred to a dedicated upgrade phase after quality gates and safety nets are active).
- Real-time WebSocket sync.

### 2.3 Anti-Goals
- **No plaintext credentials in release binaries:** Never embed fallback API keys in release BuildConfig or assets.
- **No silent failures:** Deep link and CSRF errors must be surfaced to the user with actionable retry states; permanent failures revert local state, alert user, and log to Crashlytics.
- **No cross-account contamination:** Switching TMDB accounts must never upload another user's local favorites.
- **No resurrecting deleted items:** Offline deletions must not be overwritten by remote pulls during sync.
- **No lost user mutations:** Push completion or failure updates must never overwrite rows mutated after push initiation (guaranteed by version check).
- **No infinite retry loops:** OkHttp Authenticator must enforce single-flight execution and limit retries.

---

## 3. Constraints
- **Android Support:** `minSdk = 26`, `targetSdk = 35`, Kotlin 2.0.21, AGP 8.7.2.
- **Application Class:** `MarvelApp` (implements `Configuration.Provider` for Hilt WorkManager; default WorkManager initializer disabled in `AndroidManifest.xml`).
- **Room SQLite Integrity:** Schema export (`4.json`) enabled; `sourceSets.getByName("test").assets.srcDir("$projectDir/schemas")` configured; migrations verified with `MigrationTestHelper`. Default column annotations match SQLite DDL (`defaultValue = "0"` and `defaultValue = "1"`).
- **CI/CD Determinism:** GitHub Actions runs must execute unit tests, Detekt baseline check, Android Lint, release signing, and Firebase upload without manual intervention or fake passes.

---

## 4. Architectural Approaches & Detailed Design

### 4.1 TMDB Key Management & 401 Handling
* **Selected: OkHttp `Authenticator` + Process In-Memory Cache (Stale-While-Revalidate)**
  - `TmdbApiKeyProvider` stores credentials in an in-memory `AtomicReference` with timestamp.
  - Read path: If valid within 24h TTL, returns in-memory value without touching Keystore or DataStore.
  - Prewarm: `MarvelApp.onCreate` launches an unblocking prewarm via `@ApplicationScope`.
  - Stale-while-revalidate: When 24h expires, the existing key is retained while a background coroutine attempts Remote Config fetch with `withTimeout(5_000L)`.
  - Forced refetch: calls `fetch(0)` and `activate()` via `suspendCancellableCoroutine`, catching `FirebaseRemoteConfigFetchThrottledException`.
  - Single-flight `TmdbAuthenticator`:
    1. Reads response body via `response.peekBody(1024L).string()`.
    2. Parses with `kotlinx.serialization` using a lightweight DTO:
       ```kotlin
       @Serializable
       private data class TmdbStatusResponse(val status_code: Int? = null)
       ```
    3. Acts strictly if `status_code == 7` (Invalid API key). Other 401s (e.g. invalid session) pass through to repository.
    4. Concurrency single-flight: inspects original request credential (from `Authorization` header or query param). If current provider key already differs from failed request key, immediately retries with current key.
    5. If keys match, triggers one forced refetch with cooldown throttling (minimum 15 min between forced refetches).
    6. Limits retry count to 1 via `response.priorResponse`.
  - Missing Credential Interception: If `keyProvider.getApiKey()` returns null / empty, `TmdbAuthInterceptor` throws `KeysUnavailableException : IOException`, mapped to `DomainError.KeysUnavailable` in `ErrorMapper`.
  - Release Binary Protection: Fallback credentials from `local.properties` declared strictly in `buildTypes.debug`. CI unzips release APK/AAB and validates that secret values do not appear in decompressed dex or resources.

### 4.2 Synchronization: Room Outbox & Remote Keys
* **Selected: `sync_outbox` Table + `movie_remote_keys` Table + `SyncCoordinator` Mutex + `WorkManager` CoroutineWorker + `MIGRATION_3_4`**
  - `movie_remote_keys` Schema & Entity:
    ```sql
    CREATE TABLE IF NOT EXISTS `movie_remote_keys` (
        `label` TEXT NOT NULL PRIMARY KEY,
        `nextPage` INTEGER,
        `totalPages` INTEGER,
        `updatedAt` INTEGER NOT NULL
    );
    ```
    ```kotlin
    @Entity(tableName = "movie_remote_keys")
    data class MovieRemoteKeysEntity(
        @PrimaryKey val label: String,
        val nextPage: Int?,
        val totalPages: Int?,
        val updatedAt: Long
    )
    ```
  - `sync_outbox` Schema & Entity:
    ```sql
    CREATE TABLE IF NOT EXISTS `sync_outbox` (
        `movieId` INTEGER NOT NULL,
        `target` TEXT NOT NULL, /* 'FAVORITE' | 'WATCHLIST' */
        `desired` INTEGER NOT NULL, /* 1 = true, 0 = false */
        `attempts` INTEGER NOT NULL DEFAULT 0,
        `lastError` TEXT,
        `version` INTEGER NOT NULL DEFAULT 1,
        PRIMARY KEY (`movieId`, `target`)
    );
    ```
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
  - Write Gating & Atomic Toggles:
    - In guest mode: mutations write solely to `favorite_movies` or `watched_movies`. `sync_outbox` is NOT written.
    - When authenticated: read-modify-write inside a single Room transaction:
      ```kotlin
      database.withTransaction {
          // 1. Update local favorite_movies or watched_movies
          // 2. Read existing row in sync_outbox:
          val existing = syncOutboxDao.find(movieId, target)
          val nextVersion = (existing?.version ?: 0) + 1
          syncOutboxDao.upsert(
              SyncOutboxEntity(
                  movieId = movieId,
                  target = target,
                  desired = newDesiredState,
                  attempts = 0,
                  version = nextVersion
              )
          )
      }
      ```
  - Compare-and-Delete:
    ```sql
    DELETE FROM sync_outbox 
    WHERE movieId = :movieId AND target = :target AND version = :capturedVersion
    ```
  - Concurrency-safe Failure Updates:
    ```sql
    UPDATE sync_outbox 
    SET attempts = attempts + 1, lastError = :error 
    WHERE movieId = :movieId AND target = :target AND version = :capturedVersion
    ```
  - Worker Retry & Failure Policy (`MAX_ATTEMPTS = 5`):
    - On transient errors (`IOException`, 5xx, or 429):
      - If HTTP 429, parses `Retry-After` header to calculate delay.
      - If `attempts + 1 < MAX_ATTEMPTS`: executes concurrency-safe failure update and returns `Result.retry()`.
    - On permanent errors (permanent 4xx non-404, or `attempts + 1 >= MAX_ATTEMPTS`):
      - Executes failure transaction:
        ```kotlin
        val rowsAffected = database.withTransaction {
            val deleted = syncOutboxDao.deleteVersion(movieId, target, capturedVersion)
            if (deleted == 1) {
                if (desired) {
                    when (target) {
                        "FAVORITE" -> database.favoriteDao().delete(movieId)
                        "WATCHLIST" -> database.watchedDao().delete(movieId)
                    }
                }
                // If desired == false: local row already deleted; background pull will restore from TMDB
            }
            deleted
        }
        if (rowsAffected == 1) {
            uiEventChannel.send(SyncFailureEvent(movieId, target, errorMsg))
            if (desired.not()) {
                syncCoordinator.requestPull()
            }
        }
        ```
    - 404 (Movie not found): safely delete row (`deleteVersion`), no retry needed.
    - 401 (Session expired): calls `sessionManager.clearSession()`, cancels unique work, retains outbox rows for re-authentication into the same account.
  - Cross-Account Guard on Login:
    - Dedicated DataStore preference `last_account_id` (outside encrypted session blob).
    - In `completeAuthentication(newAccountId)`:
      - If `lastAccountId != null && lastAccountId != newAccountId`:
        - Purge `sync_outbox`, `favorite_movies`, and `watched_movies`.
      - Stage guest local favorites/watchlist into `sync_outbox` (`desired = true, version = 1`).
      - Save `last_account_id = newAccountId`.
  - Sync Coordinator:
    ```kotlin
    @Singleton
    class SyncCoordinator @Inject constructor() {
        val mutex = Mutex()
    }
    ```
    Guarantees mutual exclusion between `SyncOutboxWorker` (push) and `moviesRepository.syncCloudWithLocal()` (pull).
  - Authoritative Pull Reconciliation:
    1. Downloads all remote pages first. If any page fails, aborts without touching local database.
    2. Atomic Room transaction:
       - Query pending `movieId`s from `sync_outbox` for that target.
       - Reconcile only items NOT present in the pending outbox set (remote deletions deleted locally; remote additions upserted).
  - Cold Migration Recovery: `MoviesRemoteMediator.initialize()` verifies remote key presence in `movie_remote_keys`. If missing, forces `LAUNCH_INITIAL_REFRESH`. `REFRESH` clears category items and key in the same Room transaction.
  - Logout Lifecycle:
    1. Cancel `SyncOutboxWorker` unique work (`WorkManager.cancelUniqueWork`).
    2. Attempt a 3-second outbox flush using `withTimeoutOrNull(3_000L)` on `syncCoordinator.mutex`.
    3. Call TMDB API `DELETE /3/authentication/session`.
    4. Clear session storage, clear `sync_outbox`, clear local user tables, and clear `last_account_id`.

### 4.3 Quality Gates & Static Analysis
* **Selected: Detekt Baseline per Module + Kover + Functional Benchmark + Canary Gate**
  - Detekt configured in root `build.gradle.kts`:
    - `buildUponDefaultConfig = true`
    - In `subprojects`: `baseline = file("$projectDir/detekt-baseline.xml")`
    - In `config/detekt/detekt.yml`: `build: { maxIssues: 0 }`, `ignoreAnnotated: ['Composable']` strictly on `MagicNumber`.
  - Kover configured at root, measuring line and branch coverage across modules, generating XML and HTML reports in CI. Excludes generated classes (`*_Factory`, `Hilt_*`, `*Database_Impl`, `BuildConfig`, Previews).
  - Benchmark module:
    - Configured with `com.android.test` and `androidx.baselineprofile`.
    - Configures `:app` with `benchmark` build type:
      ```kotlin
      buildTypes {
          create("benchmark") {
              initWith(buildTypes.getByName("release"))
              signingConfig = signingConfigs.getByName("debug")
              matchingFallbacks = listOf("release")
          }
      }
      ```
    - Root Compose hierarchy sets `Modifier.semantics { testTagsAsResourceId = true }`.
    - CI runs `assembleBenchmark` to verify compilation.
  - Canary Acceptance Criterion: Phase 3 includes a disposable canary PR with intentional Detekt, Kover, and Lint errors proving it is blocked by GitHub branch protection.

---

## 5. Detailed Test Plan & Acceptance Criteria by Phase

### Phase 1: Repository Hygiene, Documentation & CI Firebase Hardening
- **Implementation:**
  - Remove `docs/` from `.gitignore`.
  - Convert all absolute `file:///Users/...` links in `README.md` to relative repository paths; remove static "Detekt 0 Code Smells" badge and unverified coverage/test claims.
  - Remove Python injection script in `.github/workflows/main-cd.yml`.
  - Add bash validation step in CI checking `package_name == com.adauto.cinemahub` AND `mobilesdk_app_id == secrets.FIREBASE_APP_ID`.
  - Support `workflow_dispatch` input `override_test_lab` (boolean) to prevent blocked releases when Firebase quota is exhausted.
  - Remove `continue-on-error: true` from Firebase App Distribution.
  - Remove `release-mapping/mapping.txt` from GitHub Release assets (retain as Actions artifact).
  - Add CI step unzipping release APK and AAB, searching decompressed dex and resources for TMDB secrets.
- **Test Plan & Acceptance Criteria:**
  - `git check-ignore docs/superpowers/specs/...` returns 1 (tracked).
  - CI passes with official `app/google-services.json`.
  - Deliberately tampering with package name OR `mobilesdk_app_id` triggers step failure (exit 1).
  - APK/AAB secret scan passes cleanly.

### Phase 2: TMDB Key Provider, OkHttp Authenticator & Prewarm
- **Implementation:**
  - Update `TmdbApiKeyProviderImpl` with 24h TTL, in-memory cache, 5s timeout, and `FirebaseRemoteConfigFetchThrottledException` handling via `fetch(0)` and `activate()`.
  - Add credential prewarm in `MarvelApp.onCreate` using `@ApplicationScope`.
  - Implement `TmdbAuthenticator` with `kotlinx.serialization` parsing of TMDB `status_code: 7`, single-flight deduplication, and retry limits.
  - Update `TmdbAuthInterceptor` to throw `KeysUnavailableException : IOException` when key is missing, mapped to `DomainError.KeysUnavailable` in `ErrorMapper`.
  - Configure `buildConfigField` for fallback key strictly in `buildTypes.debug`.
- **Test Plan & Acceptance Criteria:**
  - `TmdbApiKeyProviderTest`: verifies in-memory cache hit (zero Remote Config calls within TTL), timeout triggering fallback to current key, and throttle handling with fake clock and fake Remote Config.
  - `TmdbAuthenticatorTest`: verifies N concurrent 401s with `status_code: 7` trigger exactly 1 refetch and retry with the new key.
  - `TmdbAuthInterceptorTest`: verifies missing key throws `KeysUnavailableException` without network execution.
  - `ErrorMapperTest`: verifies `KeysUnavailableException` maps to `DomainError.KeysUnavailable`.

### Phase 3: Real Quality Gates, Kover, Detekt & Benchmark Module
- **Implementation:**
  - Enable `buildUponDefaultConfig = true` and `build: { maxIssues: 0 }`.
  - Generate per-module Detekt baselines (`detekt-baseline.xml`).
  - Configure Kover across modules with exclusions for generated code.
  - Configure `:benchmark` module with `com.android.test`, `benchmark` build type, and `androidx.baselineprofile`.
  - Add `Modifier.semantics { testTagsAsResourceId = true }` to root Compose tree.
  - Generate dynamic release notes in CI from test XML and release APK size.
- **Test Plan & Acceptance Criteria:**
  - `./gradlew detekt` passes with 0 new issues.
  - `./gradlew koverHtmlReport koverXmlReport` generates coverage reports.
  - `./gradlew :benchmark:assembleBenchmark` compiles successfully.
  - Canary PR test: a disposable canary PR with intentional Detekt, Kover, and Lint errors is blocked by CI.

### Phase 4: Data Cycle - OAuth Persistence, Outbox & RemoteKeys (`MIGRATION_3_4`)
- **Implementation:**
  - Implement `MIGRATION_3_4` creating `sync_outbox` and `movie_remote_keys`. Update database version to 4 and export `4.json`.
  - Configure `sourceSets.getByName("test").assets.srcDir("$projectDir/schemas")` for Room tests.
  - Implement `SyncOutboxDao` with `version` increment, compare-and-delete, and versioned failure updates.
  - Integrate `WorkManager` with `hilt-work` and `MarvelApp`.
  - Implement `SyncOutboxWorker` with native backoff, `Retry-After` parsing, `MAX_ATTEMPTS = 5`, 404 disposal, session 401 handling, and Crashlytics non-fatal logging.
  - Make toggles atomic Room transactions (local table + `sync_outbox` with version counter).
  - Implement `SyncCoordinator` mutex for push/pull coordination.
  - Implement authoritative pull in `MoviesRepositoryImpl` respecting pending outbox items.
  - Update `MoviesRemoteMediator` to use `movie_remote_keys` and handle cold migration recovery in `initialize()`.
  - Persist OAuth `pendingRequestToken` in `SessionStorage`, consuming only on match or expiration, and dispatch results via ViewModel Channel.
  - Implement cross-account cleanup guard on login and graceful outbox flush + server revocation on logout.
- **Test Plan & Acceptance Criteria:**
  - `Migration3To4Test`: verifies database migration and data preservation using `MigrationTestHelper`.
  - `SyncOutboxDaoTest`: verifies version-based compare-and-delete and versioned failure updates prevent race condition data loss.
  - `SyncOutboxWorkerTest`: tested via `TestListenableWorkerBuilder` for retry, backoff, `Retry-After`, and 401/404 handling.
  - `AuthoritativeReconciliationTest`: verifies deleted items are not resurrected and pending outbox mutations are never deleted.
  - `AuthRepositoryTest`: simulates process death by recreating repository over the same underlying `SessionStorage`, verifying token and TTL persist across recreations; verifies forged callback tokens do not invalidate legitimate pending tokens; verifies guest data staging on login, cross-account purge when switching accounts, and TMDB session revocation on logout.

### Phase 5: Documentation & Coverage Verification
- **Implementation:**
  - Update `README.md` with actual Kover line/branch coverage percentages and exact test counts.
  - Document architecture, WorkManager outbox sync, and security models with accurate diagrams.
- **Test Plan & Acceptance Criteria:**
  - All repository links in `README.md` are relative and resolve properly.
  - Stated test numbers and coverage match Kover and test XML outputs exactly.

---

## 6. What Is Left to the Builder
- Exact WorkManager constraint builder parameters.
- Specific UI layout for the login failure / keys unavailable snackbar and retry button.
- Tuning of specific Detekt YAML thresholds for legacy composables.
