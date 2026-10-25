# Phase 2 (P1) Concurrency, Lifecycle, and Quality Gates Design

## 1. Intent and Why
Phase 1 (P0) established authoritative mutex isolation for pull/reconciliation, persistent sync failures, encrypted OAuth tokens, and server-side session revocation.
Phase 2 (P1) eliminates severe concurrency races, unhandled edge cases in authentication backoff, and quality gate misrepresentations identified in the technical audit:
- Deadlock vulnerability in `SyncCoordinator` when buffer saturates under mutex.
- Dropped background execution when toggles occur while worker is active (`ExistingWorkPolicy.KEEP`).
- Race condition where `SessionManagerImpl` defaults to `Guest` on cold start, causing background sync or early actions to treat authenticated users as visitors.
- 24-hour credential TTL perpetually resetting to current time upon app cold restart.
- Uncoordinated cold-start requests performing redundant disk reads and Remote Config network queries.
- `TmdbAuthenticator` resending requests with the exact same rejected API key during cooldown.
- HTTP 429 `Retry-After` headers logged but disregarded in backoff scheduling.
- GitHub Actions workflows vulnerable to mutable tag tampering (21 unpinned action references).
- README metrics masking exclusions (Kover composable/DI omissions, Detekt 98 baselined rules).
- Missing baseline profile (`baseline-prof.txt`), invalidating startup performance claims.

---

## 2. Goals, Non-Goals, and Anti-Goals

### Goals
- Make `SyncCoordinator.requestPull()` strictly non-suspending and deadlock-proof.
- Guarantee that any outbox mutation wakes WorkManager without discarding pending runs (`ExistingWorkPolicy.APPEND_OR_REPLACE`).
- Prevent session initialization races by providing an asynchronous barrier (`awaitInitialSession()`) for background workers and data mutations.
- Persist credential `fetchedAt` timestamp in encrypted storage to respect TMDB 24h key rotation schedule.
- Guarantee single-flight execution for cold-start credential loading.
- Terminate 401 retry loops in `TmdbAuthenticator` when refreshed credentials match the failing request.
- Respect HTTP 429 `Retry-After` delay in `SyncOutboxWorker` (short cooperative delay $\le 10$s in-pass; native `Result.retry()` backoff if $> 10$s or second attempt).
- Pin all 21 GitHub Actions `uses:` to full 40-character commit SHAs.
- Add legitimate `baseline-prof.txt` for `com.adauto.marvel.**` and harden Macrobenchmark UI selectors against locale variations.
- Transparently document Kover exclusions and Detekt baseline in README.

### Non-Goals
- Phase 3 (P2) items: `RemoteMediator` position collision formula, guest version override, `getAllPending` version sorting.
- Overhauling UI architecture or modifying `SessionState` sealed hierarchy into a tri-state enum.

### Anti-Goals
- Introducing blocking waits (`runBlocking` on Android Main thread).
- Breaking existing Detekt zero-issue policy (`maxIssues = 0`).
- Adding new Detekt baseline suppressions.

---

## 3. Constraints
- **Zero Detekt Issues**: All code must pass `./gradlew detekt` with `maxIssues = 0`.
- **Unit Test Coverage**: All bug fixes and changes must be accompanied by unit tests following strict TDD (Red -> Green).
- **Process & Cooperative Cancellation**: Respect Kotlin coroutine cancellation; avoid swallowing `CancellationException`.
- **Documentation Integrity**: Retain existing docstrings and comments.

---

## 4. Architectural Approaches

### Considered Approaches
1. **Approach 1 (Selected): Deferred Initialization Barrier & Cooperative Worker Backoff**
   - Keeps `SessionState` sealed hierarchy (`Guest`, `Authenticated`).
   - Introduces `awaitInitialSession(): SessionState` via `CompletableDeferred` in `SessionManager` that ensures storage read is completed and then returns `sessionState.value`.
   - `SyncOutboxWorker` and repository toggle methods await this barrier before checking session state.
   - For 429 `Retry-After`: short delays ($\le 10$s) use coroutine `delay()` in-pass; longer delays or subsequent attempts return `Result.retry()`.
   - Single-flight credential loading using `Mutex`.
   - Pin SHAs, add `baseline-prof.txt` targeting `com.adauto.marvel.**`, update README.
   - *Rationale:* Eliminates races without ripple effects across Compose UI screens and ViewModels.

2. **Approach 2 (Discarded): Explicit `SessionState.Loading` & WorkManager Global Rescheduling**
   - Changes `SessionState` sealed interface to include `SessionState.Loading`.
   - Requires every UI screen, ViewModel, and flow collector to handle `Loading` state, causing potential UI flickers on startup.
   - Reschedules worker on any 429 with custom work names, creating work-chain stalling.
   - *Rationale:* Excessive churn across UI modules for a problem that is inherently a data/lifecycle coordination barrier.

---

## 5. Detailed Component Specifications

### 5.1 Concurrency & Mutex Deadlock Elimination (`SyncCoordinator`)
- **Module**: `:core:data`
- **File**: `com/adauto/marvel/core/data/sync/SyncCoordinator.kt`
- **Specification**:
  - Replace `MutableSharedFlow<Unit>(extraBufferCapacity = 1)` with `MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)`.
  - Change `requestPull()` from `suspend fun requestPull() { _pullRequests.emit(Unit) }` to:
    ```kotlin
    fun requestPull() {
        _pullRequests.tryEmit(Unit)
    }
    ```
  - Eliminates suspension. Can be safely invoked while holding locks without risking deadlock or thread exhaustion.

### 5.2 WorkManager Wake-up Retention (`ExistingWorkPolicy`)
- **Module**: `:core:data`
- **Files**:
  - `com/adauto/marvel/core/data/repository/MoviesRepositoryImpl.kt`
  - `com/adauto/marvel/core/data/repository/AuthRepositoryImpl.kt`
- **Specification**:
  - In `enqueueSyncWork()`, change `workManager.enqueueUniqueWork(SyncOutboxWorker.WORK_NAME, ExistingWorkPolicy.KEEP, request)` to:
    ```kotlin
    workManager.enqueueUniqueWork(
        SyncOutboxWorker.WORK_NAME,
        ExistingWorkPolicy.APPEND_OR_REPLACE,
        request
    )
    ```
  - When an outbox toggle occurs while a worker is already running, WorkManager queues a consecutive execution upon current completion, ensuring the latest mutations are processed.

### 5.3 Cold Start Session Race Barrier (`SessionManager`)
- **Module**: `:core:network`
- **Files**:
  - `com/adauto/marvel/core/network/security/session/SessionManager.kt`
  - `com/adauto/marvel/core/network/security/session/SessionManagerImpl.kt`
- **Specification**:
  - Add to `SessionManager` interface:
    ```kotlin
    suspend fun awaitInitialSession(): SessionState
    ```
  - In `SessionManagerImpl`:
    ```kotlin
    private val initDeferred = CompletableDeferred<Unit>()

    init {
        scope.launch {
            mutex.withLock {
                loadStoredSession()
            }
            initDeferred.complete(Unit)
        }
    }

    override suspend fun awaitInitialSession(): SessionState {
        initDeferred.await()
        return sessionState.value
    }
    ```
  - Note: `initDeferred.await()` acts as an initialization latch. Subsequent mutations via `saveSession()` or `clearSession()` update `sessionState.value`. Callers awaiting `awaitInitialSession()` receive the current, fully-initialized state without locking into a stale snapshot.
  - In `SyncOutboxWorker.doWork()`:
    ```kotlin
    val sessionState = sessionManager.awaitInitialSession()
    if (sessionState !is SessionState.Authenticated) {
        return Result.success()
    }
    ```
  - In `MoviesRepositoryImpl` mutating functions (`toggleFavorite`, `toggleWatchlist`), call `sessionManager.awaitInitialSession()` before branching on visitor vs authenticated flow.

### 5.4 Credential TTL Persistence & Single-Flight Cold Start (`TmdbApiKeyProviderImpl`)
- **Module**: `:core:network`
- **File**: `com/adauto/marvel/core/network/security/TmdbApiKeyProviderImpl.kt`
- **Specification**:
  - Define a storage entity:
    ```kotlin
    @Serializable
    data class StoredCredentials(
        val apiKey: String?,
        val readAccessToken: String?,
        val fetchedAt: Long
    )
    ```
  - In `EncryptedKeyStorage`: Serialize and decrypt `StoredCredentials` containing the original `fetchedAt` epoch timestamp. Backwards compatibility: if JSON contains legacy schema without `fetchedAt`, fallback gracefully to `timeProvider()`.
  - On cold start:
    - Restore cache using `creds to stored.fetchedAt` rather than `creds to timeProvider()`.
    - If `now - stored.fetchedAt >= TTL_24H`, mark for background revalidation or fetch fresh credentials.
  - **Single-Flight Execution**:
    - Add `private val coldStartMutex = Mutex()`.
    - In `getCredentials()`:
      ```kotlin
      val fastCached = cachedCredentials.get()
      if (fastCached != null) { ... }
      return coldStartMutex.withLock {
          val recheck = cachedCredentials.get()
          if (recheck != null) return@withLock ...
          loadFromStorageOrRemoteConfig()
      }
      ```
    - Guarantees N parallel cold-start requests perform exactly 1 disk read and 1 network fetch.

### 5.5 Authenticator Cooldown Loop Guard (`TmdbAuthenticator`)
- **Module**: `:core:network`
- **File**: `com/adauto/marvel/core/network/authenticator/TmdbAuthenticator.kt`
- **Specification**:
  - When `statusCode == TMDB_INVALID_KEY_STATUS_CODE`:
  - Fetch `currentCredentials`. If not rotated, invoke `keyProvider.refreshKeyForced()`.
  - Check whether `newCredentials` differs from the failed request credentials:
    ```kotlin
    val isSameKey = when {
        requestBearer != null -> newCredentials.readAccessToken == requestBearer
        requestKey != null -> newCredentials.apiKey == requestKey
        else -> false
    }
    if (isSameKey) {
        return null // Stop resending the identical rejected key during cooldown
    }
    ```
  - Returns `null` when rotation cannot produce a fresh credential, letting OkHttp terminate gracefully.

### 5.6 HTTP 429 `Retry-After` Header Handling (`SyncOutboxWorker`)
- **Module**: `:core:data`
- **File**: `com/adauto/marvel/core/data/worker/SyncOutboxWorker.kt`
- **Specification**:
  - Parse `Retry-After` header from HTTP 429 response:
    - Parse delta-seconds integer (e.g. `"5"` -> 5000 ms). If missing or invalid date, default to 5000 ms.
  - If `retryDelayMs <= 10_000L` and item has not retried in-pass:
    - Perform cooperative coroutine `delay(retryDelayMs)`.
    - Retry the single remote mutation within the same worker pass.
  - If `retryDelayMs > 10_000L` or in-pass retry still returns transient error:
    - Record failure with `Retry-After` detail and return `Result.retry()`.
    - Relies on WorkManager's native retry backoff; avoids creating duplicate unique work requests under `SyncOutboxWorker.WORK_NAME`.

### 5.7 CI Security Hardening (GitHub Actions SHA Pinning)
- **Module**: `.github/workflows`
- **Files**:
  - `.github/workflows/main-cd.yml`
  - `.github/workflows/pr.yml`
- **Specification**:
  - Replace all 21 mutable tag references with full 40-character commit SHAs:
    - `actions/checkout@v4` -> `actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683 # v4.2.2`
    - `actions/setup-java@v4` -> `actions/setup-java@3a4f6e1af504cf6a31855fa899c6aa5355ba6c12 # v4.7.0`
    - `gradle/actions/setup-gradle@v4` -> `gradle/actions/setup-gradle@94baf254fe0c57716d4314e984d0de0fa911c631 # v4.3.0`
    - `actions/upload-artifact@v4` -> `actions/upload-artifact@4cec3d8aa04e39d1a68397de0c4cd6fb99baeddf # v4.6.1`
    - `actions/download-artifact@v4` -> `actions/download-artifact@cc203385981b70ca67e1cc392babf9cc229d5806 # v4.1.9`
    - `google-github-actions/auth@v2` -> `google-github-actions/auth@71fee02ec636e254215838aab5d3665727178e58 # v2.1.8`
    - `google-github-actions/setup-gcloud@v2` -> `google-github-actions/setup-gcloud@6188d52230a3f751609d3005da7e184758878788 # v2.1.4`
    - `wzieba/Firebase-Distribution-Github-Action@v1` -> `wzieba/Firebase-Distribution-Github-Action@v1.7.0` (commit SHA)
    - `softprops/action-gh-release@v2` -> `softprops/action-gh-release@c95c1deef8dd4c51880d387c53c317454c84d7df # v2.2.1`
  - **Firebase App Distribution Gate**:
    - In `main-cd.yml`: if `secrets.FIREBASE_SERVICE_ACCOUNT` is empty, log an explicit error or fail the distribution step instead of generating deceptive release notes claiming distribution succeeded.

### 5.8 Baseline Profile & Macrobenchmark Selector Fix
- **Module**: `:app`, `:benchmark`
- **Files**:
  - `app/src/main/baseline-prof.txt`
  - `benchmark/src/main/java/com/adauto/cinemahub/benchmark/BaselineProfileGenerator.kt`
- **Specification**:
  - Add curated `app/src/main/baseline-prof.txt` targeting `com.adauto.marvel.**` startup critical classes, Room entities/daos, and Compose runtime rules.
  - In `BaselineProfileGenerator.kt`: Replace `By.descContains("Ver detalhes")` with locale-agnostic `By.res("movie_card_item")` or semantic `testTag` so benchmarking runs reliably in any device language.

### 5.9 Documentation & Metrics Transparency
- **File**: `README.md`
- **Specification**:
  - Clarify coverage metrics: Explicitly note that Kover metrics exclude UI `@Composable` functions and DI modules (`*.di`).
  - Clarify Detekt badge: Document that "0 active issues" is enforced with a baseline containing 98 baselined legacy rules, ensuring full transparency.

---

## 6. Builder Decisions Settled
- `awaitInitialSession()` completes a `CompletableDeferred<Unit>` latch and returns the live `sessionState.value` to avoid freezing user state on post-startup login/logout.
- HTTP 429 applies a cooperative `delay()` in-pass only for short delays ($\le 10$s, capped at 1 retry), falling back to WorkManager native `Result.retry()` to avoid work-queue deadlocks with `APPEND_OR_REPLACE`.
- `baseline-prof.txt` rules strictly match `com.adauto.marvel.**` codebase symbols.
