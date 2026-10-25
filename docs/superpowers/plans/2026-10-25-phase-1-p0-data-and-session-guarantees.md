# Phase 1 (P0): Data & Session Guarantees Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve all 4 P0 architectural violations: enclose remote pull downloads inside the sync mutex, persist permanent sync failure events with foreground UI delivery, persist and validate OAuth request tokens against CSRF/process death, and revoke remote TMDB sessions during logout with an outbox flush attempt.

**Architecture:** 
1. **Extended Pull Mutex:** In `MoviesRepositoryImpl.syncCloudWithLocal()`, wrap the full download cycle (`fetchAllRemoteFavorites` and `fetchAllRemoteWatchlist`) and Room reconciliation inside `syncCoordinator.mutex.withLock`, preventing concurrent outbox worker deletion from discarding newly created local items.
2. **Persistent Sync Failures:** Persist permanent failure events in DataStore (`SyncFailureStorage`) and expose unconsumed events via `SyncEventBus` so the UI alerts the user when in or returning to the foreground ("No silent failures").
3. **Hardened OAuth Token Lifecycle:** Persist `pendingRequestToken` with timestamp in `AuthPreferencesStorage`. In `completeAuthentication`, validate token existence, match, and 10-minute TTL before consuming it. Handle and surface authentication errors in `MainActivity`.
4. **Authoritative Logout:** Add Retrofit endpoint `DELETE /3/authentication/session`, attempt an outbox flush under mutex before clearing, call TMDB to revoke the session, and purge local data and preferences.

**Tech Stack:** Kotlin 2.0.21, Coroutines & Flow, Room 2.6.1, Retrofit 2.11.0, Sandwich 2.0.9, AndroidX DataStore Preferences 1.1.1, MockK, Truth, JUnit 4.

**Spec:** [`docs/superpowers/specs/2026-10-24-cinema-hub-production-resilience-design.md`](file:///Users/adautosouza/repos/estudos/docs/superpowers/specs/2026-10-24-cinema-hub-production-resilience-design.md)

## Global Constraints

- Android Application ID: `com.adauto.cinemahub`
- Detekt & Lint: Zero code smells (`maxIssues = 0`), 100% test pass rate across all modules.
- Mutex Integrity: The `SyncCoordinator.mutex` must be held continuously from the start of the remote pull download until the reconciliation Room transaction commits.
- No Silent Failures: Permanent worker failures must be persisted and surfaced to the user.
- CSRF & Process Death Resistance: OAuth tokens must survive process death and must not be invalidated by mismatched callback deep links.
- Server-Side Session Revocation: Logout must invoke TMDB `DELETE /3/authentication/session`.

## Review Focus

1. **Pull Mutex Race**: Verify that a worker attempting to push an outbox item during `syncCloudWithLocal()` cannot run concurrently between the snapshot download and the reconciliation commit.
2. **Permanent Failure Persistence**: Verify that when `SyncOutboxWorker` exhausts retries in the background, the failure is written to persistent storage and emitted as an unconsumed event when observed in foreground.
3. **Deep Link Rejection Without Forfeiture**: Verify that an invalid or forged request token callback fails authentication without erasing the stored legitimate pending token.
4. **Process Death Token Recovery**: Verify that if the application process restarts while Custom Tab is open, the pending request token is successfully read from DataStore and completed.
5. **Session Revocation on Logout**: Verify that calling `logout()` issues `DELETE /3/authentication/session` with the active session ID to TMDB and clears all local tables.

---

### Task 1: Enclose Remote Pull Downloads Inside `SyncCoordinator.mutex`

**Files:**
- Modify: `core/data/src/main/java/com/adauto/marvel/core/data/repository/MoviesRepositoryImpl.kt`
- Modify: `core/data/src/test/java/com/adauto/marvel/core/data/repository/MoviesRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `SyncCoordinator.mutex`, `fetchAllRemoteFavorites`, `fetchAllRemoteWatchlist`, `database.withTransaction`.
- Produces: Concurrency-guarded `syncCloudWithLocal()`.

- [ ] **Step 1: Write failing test in `MoviesRepositoryImplTest` proving downloads run under mutex**
  In `MoviesRepositoryImplTest.kt`, add test `syncCloudWithLocal_holdsMutexAcrossRemoteDownloadAndDatabaseCommit`:
  - Assert that `syncCoordinator.mutex.isLocked` is true during the execution of `api.getFavoriteMovies` and `api.getWatchlistMovies`.
  - Verify that if another coroutine tries to acquire `syncCoordinator.mutex` while `api.getFavoriteMovies` is suspended, it is blocked until `syncCloudWithLocal()` completes.
  - Run `./gradlew :core:data:testReleaseUnitTest --tests "*.syncCloudWithLocal_holdsMutexAcrossRemoteDownloadAndDatabaseCommit"` and verify it fails.

- [ ] **Step 2: Move download calls inside `syncCoordinator.mutex.withLock` in `MoviesRepositoryImpl`**
  In `MoviesRepositoryImpl.kt`:
  ```kotlin
  override suspend fun syncCloudWithLocal(): Outcome<Unit> {
      val state = sessionManager.sessionState.value
      if (state !is SessionState.Authenticated) {
          return Outcome.Success(Unit)
      }

      val accountId = state.accountId
      val sessionId = state.sessionId

      return syncCoordinator.mutex.withLock {
          val remoteFavs = when (val favsResult = fetchAllRemoteFavorites(accountId, sessionId)) {
              is Outcome.Success -> favsResult.value
              is Outcome.Failure -> return@withLock favsResult
          }

          val remoteWatchlist = when (val watchResult = fetchAllRemoteWatchlist(accountId, sessionId)) {
              is Outcome.Success -> watchResult.value
              is Outcome.Failure -> return@withLock watchResult
          }

          database.withTransaction {
              reconcileFavorites(remoteFavs)
              reconcileWatchlist(remoteWatchlist)
          }

          Outcome.Success(Unit)
      }
  }
  ```

- [ ] **Step 3: Run tests and verify they pass**
  Run `./gradlew :core:data:testReleaseUnitTest --tests "com.adauto.marvel.core.data.repository.MoviesRepositoryImplTest"`.
  Verify all unit tests pass.

- [ ] **Step 4: Commit Task 1**
  `git commit -m "fix(data): hold sync mutex continuously across remote pull and reconciliation"`

---

### Task 2: Persistent Sync Failure Events ("No Silent Failures")

**Files:**
- Create: `core/data/src/main/java/com/adauto/marvel/core/data/storage/SyncFailureStorage.kt`
- Create: `core/data/src/test/java/com/adauto/marvel/core/data/storage/SyncFailureStorageTest.kt`
- Modify: `core/data/src/main/java/com/adauto/marvel/core/data/sync/SyncEventBus.kt`
- Modify: `core/data/src/main/java/com/adauto/marvel/core/data/worker/SyncOutboxWorker.kt`
- Modify: `core/data/src/test/java/com/adauto/marvel/core/data/worker/SyncOutboxWorkerTest.kt`
- Modify: `core/data/src/main/java/com/adauto/marvel/core/data/di/DataModule.kt`

**Interfaces:**
- Consumes: DataStore Preferences, `SyncFailureEvent`.
- Produces: `SyncFailureStorage`, persistent `SyncEventBus.unreadFailures: Flow<List<SyncFailureEvent>>`, `SyncEventBus.consumeFailures(events: List<SyncFailureEvent>)`.

- [ ] **Step 1: Write unit tests for `SyncFailureStorage`**
  In `core/data/src/test/java/com/adauto/marvel/core/data/storage/SyncFailureStorageTest.kt`:
  - Test saving multiple `SyncFailureEvent`s.
  - Test retrieving saved unconsumed failures.
  - Test clearing or acknowledging failures.
  - Test recovering gracefully from DataStore IOExceptions.

- [ ] **Step 2: Implement `SyncFailureStorage` and wire into `DataModule`**
  In `core/data/src/main/java/com/adauto/marvel/core/data/storage/SyncFailureStorage.kt`:
  - Use `DataStore<Preferences>` storing a JSON string list of failures:
    ```kotlin
    @Serializable
    data class PersistedSyncFailure(
        val movieId: Int,
        val target: String,
        val error: String?,
        val timestamp: Long
    )
    ```
  - Expose `suspend fun recordFailure(event: SyncFailureEvent, timestamp: Long)`
  - Expose `val unconsumedFailures: Flow<List<SyncFailureEvent>>`
  - Expose `suspend fun clearFailures()`

- [ ] **Step 3: Update `SyncEventBus` to persist failures and expose unconsumed events**
  In `SyncEventBus.kt`:
  - Inject `SyncFailureStorage`.
  - When `emitFailure(event)` is called: write to `SyncFailureStorage.recordFailure(event, ...)` and emit to in-memory flow.
  - Expose `val unconsumedFailures: Flow<List<SyncFailureEvent>> = storage.unconsumedFailures`.
  - Add `suspend fun consumeFailures()` to acknowledge and clear.

- [ ] **Step 4: Update `SyncOutboxWorkerTest` and verify failure handling**
  - Verify that when worker handles a permanent failure (`rowsAffected == 1`), `syncEventBus.emitFailure` is invoked and persisted.
  - Run `./gradlew :core:data:testReleaseUnitTest --tests "*.SyncOutboxWorkerTest"` and verify success.

- [ ] **Step 5: Commit Task 2**
  `git commit -m "feat(data): persist sync failure events to DataStore for foreground delivery"`

---

### Task 3: Hardened OAuth Request Token Lifecycle & Error Surfacing

**Files:**
- Modify: `core/data/src/main/java/com/adauto/marvel/core/data/storage/AuthPreferencesStorage.kt`
- Modify: `core/data/src/main/java/com/adauto/marvel/core/data/repository/AuthRepositoryImpl.kt`
- Modify: `core/data/src/test/java/com/adauto/marvel/core/data/storage/AuthPreferencesStorageTest.kt`
- Modify: `core/data/src/test/java/com/adauto/marvel/core/data/repository/AuthRepositoryTest.kt`
- Modify: `app/src/main/java/com/adauto/marvel/MainActivity.kt`

**Interfaces:**
- Consumes: `AuthPreferencesStorage`, `AuthRepository.completeAuthentication`.
- Produces: Process-death resilient OAuth with non-destructive validation and UI error surfacing.

- [ ] **Step 1: Write failing tests for `AuthPreferencesStorage` request token persistence**
  In `AuthPreferencesStorageTest.kt`:
  - Test saving `pendingRequestToken` with timestamp.
  - Test retrieving `pendingRequestToken`.
  - Test clearing `pendingRequestToken`.

- [ ] **Step 2: Implement request token methods in `AuthPreferencesStorage`**
  In `AuthPreferencesStorage.kt`:
  ```kotlin
  suspend fun savePendingRequestToken(token: String, createdAtMs: Long)
  suspend fun getPendingRequestToken(): Pair<String, Long>?
  suspend fun clearPendingRequestToken()
  ```

- [ ] **Step 3: Write failing tests in `AuthRepositoryTest` for hardened validation**
  In `AuthRepositoryTest.kt`:
  - `completeAuthentication_whenTokenDoesNotMatch_doesNotClearPendingToken`: verify that calling with a mismatched token returns `Outcome.Failure(DomainError.InvalidKey)` and leaves the stored token intact.
  - `completeAuthentication_whenTokenMatches_clearsPendingTokenAndCompletes`: verify successful exchange clears the token.
  - `completeAuthentication_survivesProcessDeath_whenTokenReadFromStorage`: verify that if `AuthRepositoryImpl` is recreated, it reads `getPendingRequestToken()` and succeeds.

- [ ] **Step 4: Implement hardened token validation in `AuthRepositoryImpl`**
  In `AuthRepositoryImpl.kt`:
  - In `createRequestToken()`: call `authPreferences.savePendingRequestToken(token, now)`.
  - In `completeAuthentication(requestToken: String)`:
    1. Read `val pending = authPreferences.getPendingRequestToken()`.
    2. Check `if (pending == null) return Outcome.Failure(DomainError.InvalidKey)`.
    3. Check `if (pending.first != requestToken) return Outcome.Failure(DomainError.InvalidKey)` without clearing!
    4. Check `if (now - pending.second > TOKEN_TTL_MS)` -> call `authPreferences.clearPendingRequestToken()` and return failure.
    5. Token is valid -> call `authPreferences.clearPendingRequestToken()`.
    6. Continue with `createSession(...)`.

- [ ] **Step 5: Surface `completeAuthentication` failures in `MainActivity`**
  In `MainActivity.kt`:
  - When `authRepository.completeAuthentication(requestToken)` returns `Outcome.Failure`, surface error via `Toast` or snackbar event so the user is informed rather than experiencing silent failure.

- [ ] **Step 6: Run tests and verify**
  Run `./gradlew :core:data:testReleaseUnitTest :app:testDebugUnitTest`.
  Verify all pass.

- [ ] **Step 7: Commit Task 3**
  `git commit -m "security(auth): persist OAuth token against process death, prevent CSRF token wipe, and surface errors"`

---

### Task 4: Server-Side Session Revocation on Logout & Outbox Flush

**Files:**
- Create: `core/network/src/main/java/com/adauto/marvel/core/network/model/TmdbDeleteSessionRequestDto.kt`
- Modify: `core/network/src/main/java/com/adauto/marvel/core/network/api/TmdbApi.kt`
- Modify: `core/data/src/main/java/com/adauto/marvel/core/data/repository/AuthRepositoryImpl.kt`
- Modify: `core/data/src/test/java/com/adauto/marvel/core/data/repository/AuthRepositoryTest.kt`

**Interfaces:**
- Consumes: `@HTTP(method = "DELETE", path = "3/authentication/session", hasBody = true)`.
- Produces: Remote session revocation and outbox flush during logout.

- [ ] **Step 1: Define `TmdbDeleteSessionRequestDto` and endpoint in `TmdbApi`**
  In `core/network/src/main/java/com/adauto/marvel/core/network/model/TmdbDeleteSessionRequestDto.kt`:
  ```kotlin
  @Serializable
  data class TmdbDeleteSessionRequestDto(
      @SerialName("session_id") val sessionId: String
  )
  ```
  In `TmdbApi.kt`:
  ```kotlin
  @HTTP(method = "DELETE", path = "3/authentication/session", hasBody = true)
  suspend fun deleteSession(
      @Body body: TmdbDeleteSessionRequestDto
  ): ApiResponse<TmdbActionResponseDto>
  ```

- [ ] **Step 2: Write failing test in `AuthRepositoryTest` for session revocation on logout**
  In `AuthRepositoryTest.kt`:
  - Verify that when `logout()` is called on an authenticated user, `api.deleteSession(TmdbDeleteSessionRequestDto("sess_iron"))` is called.
  - Verify that if there are pending outbox items, flush is attempted before clearing.
  - Verify that `authPreferences.clearPendingRequestToken()` is called.
  - Verify that `database.syncOutboxDao().clearAll()`, `favoriteDao.clearAll()`, and `watchedDao.clearAll()` are executed.

- [ ] **Step 3: Implement remote revocation and flush in `AuthRepositoryImpl.logout`**
  In `AuthRepositoryImpl.kt`:
  ```kotlin
  override suspend fun logout() {
      val state = sessionManager.sessionState.value
      val currentSessionId = (state as? SessionState.Authenticated)?.sessionId

      // Cancel periodic worker and attempt immediate flush under mutex
      workManager.cancelUniqueWork(SyncOutboxWorker.WORK_NAME)
      withTimeoutOrNull(LOGOUT_FLUSH_TIMEOUT_MS) {
          syncCoordinator.mutex.withLock {
              // Drain any pending items if authenticated
              if (currentSessionId != null) {
                  // Direct push of pending items or drain loop
              }
          }
      }

      // Revoke remote session on TMDB server
      if (currentSessionId != null) {
          api.deleteSession(TmdbDeleteSessionRequestDto(currentSessionId))
      }

      // Clear local session state and cached credentials
      sessionManager.clearSession()
      database.withTransaction {
          database.syncOutboxDao().clearAll()
          database.favoriteDao().clearAll()
          database.watchedDao().clearAll()
      }
      authPreferences.clearLastAccountId()
      authPreferences.clearPendingRequestToken()
  }
  ```

- [ ] **Step 4: Run tests and verify**
  Run `./gradlew :core:data:testReleaseUnitTest :core:network:testReleaseUnitTest`.
  Verify all tests pass.

- [ ] **Step 5: Commit Task 4**
  `git commit -m "feat(auth): revoke session on TMDB server during logout and clear pending tokens"`

---

### Task 5: Full Suite Verification & PR Canary Check

**Files:**
- Verify: Full codebase

- [ ] **Step 1: Run complete CI test suite locally**
  Run `./gradlew detekt lintDebug testDebugUnitTest testReleaseUnitTest koverXmlReport koverHtmlReport assembleDebug assembleRelease`.
  Confirm 0 Detekt issues, 0 lint errors, and 100% test pass rate.

- [ ] **Step 2: Verify git status and commit cleanliness**
  Ensure working directory is clean.
