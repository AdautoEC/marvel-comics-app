# Phase 2 (P1) Concurrency, Lifecycle, and Quality Gates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate concurrency deadlocks, session initialization races, unhandled 401/429 rate limit loops, workflow security vulnerabilities, and quality gate misrepresentations across `:core:data`, `:core:network`, `:app`, and GitHub Actions.

**Architecture:** 
- Concurrency & Mutex: Non-suspending conflated SharedFlow in `SyncCoordinator` and `ExistingWorkPolicy.APPEND_OR_REPLACE` in WorkManager.
- Lifecycle: `CompletableDeferred` barrier (`awaitInitialSession()`) in `SessionManager` preventing visitor races, and single-flight cold start with persisted `fetchedAt` in `TmdbApiKeyProviderImpl`.
- Network & Resilience: `TmdbAuthenticator` terminates on unchanged credentials; `SyncOutboxWorker` executes cooperative `delay()` on HTTP 429 `Retry-After`.
- CI & Performance: Pin 21 GitHub Actions to exact commit SHAs, author `baseline-prof.txt` for `com.adauto.marvel.**`, and clarify Kover/Detekt metrics in `README.md`.

**Tech Stack:** Kotlin 2.0, Coroutines, WorkManager, OkHttp Authenticator, Room, Jetpack Compose, Kover, Detekt, GitHub Actions.

**Spec:** [docs/superpowers/specs/2026-10-25-phase-2-p1-concurrency-lifecycle-gates-design.md](file:///Users/adautosouza/repos/estudos/docs/superpowers/specs/2026-10-25-phase-2-p1-concurrency-lifecycle-gates-design.md)

## Global Constraints
- Zero Detekt Issues (`maxIssues = 0`, no new baseline suppressions).
- 100% test pass rate across all modified modules.
- Strict TDD (Red -> Green -> Refactor) for all functional code changes.
- Preserve Kotlin coroutine cooperative cancellation (rethrow `CancellationException`).
- Maintain existing comments and docstrings.

## Review Focus
- Concurrent emission into `SyncCoordinator.requestPull()` does not suspend calling coroutine even under mutex lock.
- `SessionManager.awaitInitialSession()` returns active session without stalling or freezing after post-startup OAuth login/logout.
- Concurrent cold-start calls to `TmdbApiKeyProvider.getCredentials()` trigger exactly 1 storage decryption and 1 Remote Config fetch.
- `TmdbAuthenticator` returns `null` when refreshed credentials match the failing request credentials to break infinite OkHttp retry loops.
- `SyncOutboxWorker` obeys HTTP 429 `Retry-After` header with in-pass cooperative delay for $\le 10$s without exceeding attempt caps.

---

### Task 1: Non-suspending Conflated Pull in `SyncCoordinator`

**Files:**
- Modify: `core/data/src/main/java/com/adauto/marvel/core/data/sync/SyncCoordinator.kt:13-18`
- Test: `core/data/src/test/java/com/adauto/marvel/core/data/sync/SyncCoordinatorTest.kt`

**Interfaces:**
- Consumes: Kotlin Coroutines Flow (`MutableSharedFlow`, `BufferOverflow.DROP_OLDEST`)
- Produces: `fun requestPull()` (non-suspending)

- [ ] **Step 1: Write the failing test**

Create `core/data/src/test/java/com/adauto/marvel/core/data/sync/SyncCoordinatorTest.kt`:
```kotlin
package com.adauto.marvel.core.data.sync

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncCoordinatorTest {

    @Test
    fun requestPull_doesNotSuspendAndDropsOldestWhenBufferFull() = runTest {
        val coordinator = SyncCoordinator()
        
        // Rapid sequential triggers should not suspend
        coordinator.requestPull()
        coordinator.requestPull()
        coordinator.requestPull()

        var received = 0
        val job = launch {
            coordinator.pullRequests.collect { received++ }
        }
        coordinator.requestPull()
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(1, received)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:data:testDebugUnitTest --tests "com.adauto.marvel.core.data.sync.SyncCoordinatorTest"`
Expected: FAIL (compilation error or failure due to suspend mismatch)

- [ ] **Step 3: Implement non-suspending `requestPull()` in `SyncCoordinator.kt`**

Update `core/data/src/main/java/com/adauto/marvel/core/data/sync/SyncCoordinator.kt`:
```kotlin
package com.adauto.marvel.core.data.sync

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncCoordinator @Inject constructor() {
    val mutex = Mutex()
    private val _pullRequests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val pullRequests: SharedFlow<Unit> = _pullRequests.asSharedFlow()

    fun requestPull() {
        _pullRequests.tryEmit(Unit)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:data:testDebugUnitTest --tests "com.adauto.marvel.core.data.sync.SyncCoordinatorTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/data/src/main/java/com/adauto/marvel/core/data/sync/SyncCoordinator.kt core/data/src/test/java/com/adauto/marvel/core/data/sync/SyncCoordinatorTest.kt
git commit -m "fix(data): make SyncCoordinator pull emission non-suspending with DROP_OLDEST"
```

---

### Task 2: WorkManager Wake-Up Retention (`ExistingWorkPolicy.APPEND_OR_REPLACE`)

**Files:**
- Modify: `core/data/src/main/java/com/adauto/marvel/core/data/repository/MoviesRepositoryImpl.kt:198`
- Modify: `core/data/src/main/java/com/adauto/marvel/core/data/repository/AuthRepositoryImpl.kt:143`
- Test: `core/data/src/test/java/com/adauto/marvel/core/data/repository/MoviesRepositoryImplTest.kt`
- Test: `core/data/src/test/java/com/adauto/marvel/core/data/repository/AuthRepositoryTest.kt`

**Interfaces:**
- Consumes: `androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE`
- Produces: Reliable WorkManager scheduling on consecutive mutations

- [ ] **Step 1: Write failing tests for APPEND_OR_REPLACE**

Update `MoviesRepositoryImplTest.kt` and `AuthRepositoryTest.kt` to assert that `workManager.enqueueUniqueWork` is called with `ExistingWorkPolicy.APPEND_OR_REPLACE` instead of `ExistingWorkPolicy.KEEP`.

In `MoviesRepositoryImplTest.kt`:
```kotlin
verify {
    workManager.enqueueUniqueWork(
        SyncOutboxWorker.WORK_NAME,
        ExistingWorkPolicy.APPEND_OR_REPLACE,
        any()
    )
}
```

In `AuthRepositoryTest.kt`:
```kotlin
verify {
    workManager.enqueueUniqueWork(
        SyncOutboxWorker.WORK_NAME,
        ExistingWorkPolicy.APPEND_OR_REPLACE,
        any()
    )
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:data:testDebugUnitTest --tests "com.adauto.marvel.core.data.repository.MoviesRepositoryImplTest" --tests "com.adauto.marvel.core.data.repository.AuthRepositoryTest"`
Expected: FAIL (assertion error: expected APPEND_OR_REPLACE but was KEEP)

- [ ] **Step 3: Update `MoviesRepositoryImpl.kt` and `AuthRepositoryImpl.kt`**

Replace `ExistingWorkPolicy.KEEP` with `ExistingWorkPolicy.APPEND_OR_REPLACE` in both files.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:data:testDebugUnitTest --tests "com.adauto.marvel.core.data.repository.MoviesRepositoryImplTest" --tests "com.adauto.marvel.core.data.repository.AuthRepositoryTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/data/src/main/java/com/adauto/marvel/core/data/repository/MoviesRepositoryImpl.kt core/data/src/main/java/com/adauto/marvel/core/data/repository/AuthRepositoryImpl.kt core/data/src/test/java/com/adauto/marvel/core/data/repository/MoviesRepositoryImplTest.kt core/data/src/test/java/com/adauto/marvel/core/data/repository/AuthRepositoryTest.kt
git commit -m "fix(data): retain WorkManager outbox wake-up using APPEND_OR_REPLACE"
```

---

### Task 3: Session Loading Barrier (`awaitInitialSession`)

**Files:**
- Modify: `core/network/src/main/java/com/adauto/marvel/core/network/security/session/SessionManager.kt`
- Modify: `core/network/src/main/java/com/adauto/marvel/core/network/security/session/SessionManagerImpl.kt`
- Modify: `core/data/src/main/java/com/adauto/marvel/core/data/worker/SyncOutboxWorker.kt:70`
- Modify: `core/data/src/main/java/com/adauto/marvel/core/data/repository/MoviesRepositoryImpl.kt`
- Test: `core/network/src/test/java/com/adauto/marvel/core/network/security/session/SessionManagerTest.kt`
- Test: `core/data/src/test/java/com/adauto/marvel/core/data/worker/SyncOutboxWorkerTest.kt`

**Interfaces:**
- Consumes: `CompletableDeferred<Unit>`
- Produces: `suspend fun awaitInitialSession(): SessionState`

- [ ] **Step 1: Write failing test in `SessionManagerTest.kt`**

Add test in `SessionManagerTest.kt`:
```kotlin
@Test
fun awaitInitialSession_waitsForStoredSessionBeforeReturning() = runTest {
    coEvery { storage.loadEncryptedSession() } coAnswers {
        delay(50)
        encryptedBytes
    }
    coEvery { cipher.decrypt(encryptedBytes) } returns decryptedBytes

    val sessionManager = SessionManagerImpl(cipher, storage, backgroundScope)
    
    val initial = sessionManager.awaitInitialSession()
    assertTrue(initial is SessionState.Authenticated)
    assertEquals("test_session", (initial as SessionState.Authenticated).sessionId)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:network:testDebugUnitTest --tests "com.adauto.marvel.core.network.security.session.SessionManagerTest"`
Expected: FAIL (`awaitInitialSession` not defined)

- [ ] **Step 3: Implement `awaitInitialSession()` in `SessionManager.kt` and `SessionManagerImpl.kt`**

In `SessionManager.kt`:
```kotlin
interface SessionManager {
    val sessionState: StateFlow<SessionState>
    suspend fun awaitInitialSession(): SessionState
    suspend fun saveSession(accountId: Int, username: String, sessionId: String, avatarUrl: String? = null)
    suspend fun clearSession()
}
```

In `SessionManagerImpl.kt`:
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

In `SyncOutboxWorker.kt`:
Replace `val sessionState = sessionManager.sessionState.value` with:
```kotlin
val sessionState = sessionManager.awaitInitialSession()
```

In `MoviesRepositoryImpl.kt`:
In `toggleFavorite` and `toggleWatchlist`, invoke `sessionManager.awaitInitialSession()` to ensure session is initialized before querying `sessionState.value`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:network:testDebugUnitTest :core:data:testDebugUnitTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/network/src/main/java/com/adauto/marvel/core/network/security/session/SessionManager.kt core/network/src/main/java/com/adauto/marvel/core/network/security/session/SessionManagerImpl.kt core/network/src/test/java/com/adauto/marvel/core/network/security/session/SessionManagerTest.kt core/data/src/main/java/com/adauto/marvel/core/data/worker/SyncOutboxWorker.kt core/data/src/main/java/com/adauto/marvel/core/data/repository/MoviesRepositoryImpl.kt core/data/src/test/java/com/adauto/marvel/core/data/worker/SyncOutboxWorkerTest.kt
git commit -m "fix(security): introduce awaitInitialSession barrier to resolve cold-start session race"
```

---

### Task 4: Credential TTL Persistence & Single-Flight Cold Start

**Files:**
- Modify: `core/network/src/main/java/com/adauto/marvel/core/network/security/TmdbApiKeyProviderImpl.kt`
- Test: `core/network/src/test/java/com/adauto/marvel/core/network/security/TmdbApiKeyProviderImplTest.kt`

**Interfaces:**
- Consumes: `EncryptedKeyStorage`, `KeystoreCipher`
- Produces: `StoredCredentials(apiKey, readAccessToken, fetchedAt)` with single-flight mutex protection

- [ ] **Step 1: Write failing tests for TTL persistence and single-flight**

In `TmdbApiKeyProviderImplTest.kt`:
```kotlin
@Test
fun getCredentials_preservesStoredFetchedAt_andTriggersRefreshIfExpired() = runTest {
    val expiredTimestamp = currentTimeMs - 25 * 60 * 60 * 1000L // 25 hours ago
    val storedJson = """{"apiKey":"old_key","readAccessToken":"old_token","fetchedAt":$expiredTimestamp}"""
    coEvery { encryptedStorage.loadEncryptedBlob() } returns storedJson.toByteArray()
    coEvery { cipher.decrypt(any()) } returns storedJson.toByteArray()

    val provider = TmdbApiKeyProviderImpl(
        externalScope = testScope,
        remoteConfig = remoteConfig,
        encryptedStorage = encryptedStorage,
        cipher = cipher,
        timeProvider = timeProvider
    )

    val creds = provider.getCredentials()
    assertEquals("old_key", creds?.apiKey)
    // Verify background refresh was triggered due to TTL expiration
    testScheduler.advanceUntilIdle()
    coVerify { remoteConfig.fetchAndActivate() }
}

@Test
fun getCredentials_singleFlight_preventsMultipleConcurrentStorageReads() = runTest {
    coEvery { encryptedStorage.loadEncryptedBlob() } coAnswers {
        delay(100)
        """{"apiKey":"key","readAccessToken":"token","fetchedAt":$currentTimeMs}""".toByteArray()
    }
    coEvery { cipher.decrypt(any()) } returns """{"apiKey":"key","readAccessToken":"token","fetchedAt":$currentTimeMs}""".toByteArray()

    val provider = TmdbApiKeyProviderImpl(
        externalScope = testScope,
        remoteConfig = remoteConfig,
        encryptedStorage = encryptedStorage,
        cipher = cipher,
        timeProvider = timeProvider
    )

    val d1 = async { provider.getCredentials() }
    val d2 = async { provider.getCredentials() }
    val d3 = async { provider.getCredentials() }
    listOf(d1, d2, d3).awaitAll()

    coVerify(exactly = 1) { encryptedStorage.loadEncryptedBlob() }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:network:testDebugUnitTest --tests "com.adauto.marvel.core.network.security.TmdbApiKeyProviderImplTest"`
Expected: FAIL

- [ ] **Step 3: Implement `StoredCredentials` persistence and `coldStartMutex` in `TmdbApiKeyProviderImpl.kt`**

- Define `StoredCredentials` serializable class.
- When saving credentials, record `timeProvider()` in `fetchedAt`.
- On cold start, decrypt and parse `StoredCredentials` (falling back to legacy parsing if `fetchedAt` is missing).
- Set cache with `creds to stored.fetchedAt`.
- Enclose storage read in `coldStartMutex.withLock` to enforce single-flight.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:network:testDebugUnitTest --tests "com.adauto.marvel.core.network.security.TmdbApiKeyProviderImplTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/network/src/main/java/com/adauto/marvel/core/network/security/TmdbApiKeyProviderImpl.kt core/network/src/test/java/com/adauto/marvel/core/network/security/TmdbApiKeyProviderImplTest.kt
git commit -m "fix(network): persist credential fetchedAt timestamp and enforce single-flight cold start"
```

---

### Task 5: Authenticator Cooldown Re-send Termination

**Files:**
- Modify: `core/network/src/main/java/com/adauto/marvel/core/network/authenticator/TmdbAuthenticator.kt:68-80`
- Test: `core/network/src/test/java/com/adauto/marvel/core/network/authenticator/TmdbAuthenticatorTest.kt`

**Interfaces:**
- Consumes: `TmdbApiKeyProvider.refreshKeyForced()`
- Produces: `authenticate(): Request?` (returns `null` when credential unchanged)

- [ ] **Step 1: Write failing test in `TmdbAuthenticatorTest.kt`**

```kotlin
@Test
fun authenticate_returnsNullWhenKeyProviderReturnsIdenticalCredentialsDuringCooldown() {
    val sameCreds = TmdbCredentials(apiKey = "invalid_key", readAccessToken = null)
    coEvery { keyProvider.getCredentials() } returns sameCreds
    coEvery { keyProvider.refreshKeyForced() } returns sameCreds

    val response = build401Response(requestUrl = "https://api.themoviedb.org/3/movie/1?api_key=invalid_key", statusCode = 7)
    val nextRequest = authenticator.authenticate(null, response)

    assertNull("Authenticator must return null when credentials did not rotate", nextRequest)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:network:testDebugUnitTest --tests "com.adauto.marvel.core.network.authenticator.TmdbAuthenticatorTest"`
Expected: FAIL (returns non-null request with same key)

- [ ] **Step 3: Update `TmdbAuthenticator.kt` to terminate on identical credentials**

In `TmdbAuthenticator.kt`:
```kotlin
val newCredentials = if (hasRotated) {
    currentCredentials
} else {
    try {
        runBlocking { keyProvider.refreshKeyForced() }
    } catch (_: Exception) {
        null
    }
}

if (newCredentials == null || !newCredentials.isNotEmpty) return null

val isSameKey = when {
    requestBearer != null -> newCredentials.readAccessToken == requestBearer
    requestKey != null -> newCredentials.apiKey == requestKey
    else -> false
}
if (isSameKey) return null
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:network:testDebugUnitTest --tests "com.adauto.marvel.core.network.authenticator.TmdbAuthenticatorTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/network/src/main/java/com/adauto/marvel/core/network/authenticator/TmdbAuthenticator.kt core/network/src/test/java/com/adauto/marvel/core/network/authenticator/TmdbAuthenticatorTest.kt
git commit -m "fix(network): stop resending identical credentials in TmdbAuthenticator during cooldown"
```

---

### Task 6: HTTP 429 `Retry-After` Header Cooperative Delay

**Files:**
- Modify: `core/data/src/main/java/com/adauto/marvel/core/data/worker/SyncOutboxWorker.kt:134-144`
- Test: `core/data/src/test/java/com/adauto/marvel/core/data/worker/SyncOutboxWorkerTest.kt`

**Interfaces:**
- Consumes: HTTP 429 response `Retry-After` header
- Produces: In-pass cooperative delay ($\le 10$s) and retry; fallback to `Result.retry()`

- [ ] **Step 1: Write failing test in `SyncOutboxWorkerTest.kt`**

```kotlin
@Test
fun handleHttpError_429WithRetryAfter_delaysCooperativelyAndRetriesInPass() = runTest {
    // Mock 429 response with Retry-After: 2
    // Verify delay was executed and second remote attempt succeeded without returning Result.retry()
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:data:testDebugUnitTest --tests "com.adauto.marvel.core.data.worker.SyncOutboxWorkerTest"`
Expected: FAIL

- [ ] **Step 3: Implement in-pass `Retry-After` handling in `SyncOutboxWorker.kt`**

- Parse `Retry-After` header as integer seconds (defaulting to 5s if unparseable).
- If delay $\le 10_000L$ and item has not retried in-pass, call `delay(delayMs)` and execute remote call again.
- If delay $> 10_000L$ or second attempt fails, record error with `(Retry-After: ...)` and return `Result.retry()`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:data:testDebugUnitTest --tests "com.adauto.marvel.core.data.worker.SyncOutboxWorkerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/data/src/main/java/com/adauto/marvel/core/data/worker/SyncOutboxWorker.kt core/data/src/test/java/com/adauto/marvel/core/data/worker/SyncOutboxWorkerTest.kt
git commit -m "feat(data): respect HTTP 429 Retry-After header with cooperative coroutine delay"
```

---

### Task 7: CI Security Hardening (GitHub Actions SHA Pinning & CD Fail-Fast)

**Files:**
- Modify: `.github/workflows/main-cd.yml`
- Modify: `.github/workflows/pr.yml`

**Interfaces:**
- Consumes: Immutable 40-character commit SHAs for GitHub Actions
- Produces: Tamper-proof CI pipelines and explicit distribution failures

- [ ] **Step 1: Pin all 21 `uses:` in `.github/workflows/main-cd.yml` and `pr.yml` to exact SHAs**

Replace mutable version tags with verified commit SHAs:
- `actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683 # v4.2.2`
- `actions/setup-java@3a4f6e1af504cf6a31855fa899c6aa5355ba6c12 # v4.7.0`
- `gradle/actions/setup-gradle@94baf254fe0c57716d4314e984d0de0fa911c631 # v4.3.0`
- `actions/upload-artifact@4cec3d8aa04e39d1a68397de0c4cd6fb99baeddf # v4.6.1`
- `actions/download-artifact@cc203385981b70ca67e1cc392babf9cc229d5806 # v4.1.9`
- `google-github-actions/auth@71fee02ec636e254215838aab5d3665727178e58 # v2.1.8`
- `google-github-actions/setup-gcloud@6188d52230a3f751609d3005da7e184758878788 # v2.1.4`
- `wzieba/Firebase-Distribution-Github-Action@v1.7.0` SHA
- `softprops/action-gh-release@c95c1deef8dd4c51880d387c53c317454c84d7df # v2.2.1`

- [ ] **Step 2: Add fail-fast check for Firebase App Distribution**

In `main-cd.yml`, if `FIREBASE_SERVICE_ACCOUNT` is missing, fail the step with an explicit message instead of silently claiming release was deployed.

- [ ] **Step 3: Verify YAML syntax**

Run: `git diff .github/workflows/` and review changes.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/main-cd.yml .github/workflows/pr.yml
git commit -m "ci(security): pin all GitHub Actions to immutable SHAs and add distribution fail-fast"
```

---

### Task 8: Baseline Profile and Macrobenchmark Hardening

**Files:**
- Create: `app/src/main/baseline-prof.txt`
- Modify: `benchmark/src/main/java/com/adauto/cinemahub/benchmark/BaselineProfileGenerator.kt`

**Interfaces:**
- Consumes: Jetpack ProfileInstaller, UiAutomator By selectors
- Produces: Bundled AOT baseline profile and locale-independent UI benchmark

- [ ] **Step 1: Create `app/src/main/baseline-prof.txt`**

Author curated baseline profile for `com.adauto.marvel.**` covering startup critical paths:
```
HSPLcom/adauto/marvel/MainActivity;-><init>()V
HSPLcom/adauto/marvel/MainActivity;->onCreate(Landroid/os/Bundle;)V
HSPLcom/adauto/marvel/core/data/repository/MoviesRepositoryImpl;-><init>(Lcom/adauto/marvel/core/database/CinemaHubDatabase;Lcom/adauto/marvel/core/network/TmdbApi;Lcom/adauto/marvel/core/data/sync/SyncCoordinator;Lcom/adauto/marvel/core/network/security/session/SessionManager;Landroidx/work/WorkManager;Lcom/adauto/marvel/core/data/sync/SyncEventBus;)V
HSPLcom/adauto/marvel/core/network/security/session/SessionManagerImpl;-><init>(Lcom/adauto/marvel/core/network/security/KeystoreCipher;Lcom/adauto/marvel/core/network/security/session/SessionStorage;Lkotlinx/coroutines/CoroutineScope;)V
HSPLcom/adauto/marvel/core/network/security/session/SessionManagerImpl;->awaitInitialSession(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;
```

- [ ] **Step 2: Update `BaselineProfileGenerator.kt` to use `testTag`**

In `benchmark/src/main/java/com/adauto/cinemahub/benchmark/BaselineProfileGenerator.kt`:
Replace `By.descContains("Ver detalhes")` with locale-independent `By.res("movie_card_item")` or fallback selector.

- [ ] **Step 3: Verify compilation**

Run: `./gradlew assembleRelease`
Expected: BUILD SUCCESSFUL (baseline-prof.txt packaged in APK)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/baseline-prof.txt benchmark/src/main/java/com/adauto/cinemahub/benchmark/BaselineProfileGenerator.kt
git commit -m "perf(baseline): package curated baseline-prof.txt and harden benchmark selectors"
```

---

### Task 9: Metrics Transparency & Documentation

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: Coverage and Detekt baseline facts
- Produces: Transparent project documentation

- [ ] **Step 1: Update README.md with Kover and Detekt baseline scope**

Add explicit notes:
- Code coverage scope: Kover excludes UI `@Composable` functions and DI modules (`*.di`).
- Detekt status: "0 active issues" is maintained with 98 rules recorded in baseline files.

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: clarify Kover exclusion scope and Detekt baseline status in README"
```

---

### Task 10: Complete Verification & Code Review Pass

**Files:** All modified files across repository

- [ ] **Step 1: Run comprehensive local verification**
Run: `./gradlew detekt lintDebug testDebugUnitTest testReleaseUnitTest koverXmlReport assembleDebug assembleRelease`
Expected: 100% PASS, 0 Detekt issues, 0 lint errors.

- [ ] **Step 2: Dispatch Senior Code Reviewer subagent (`pro` model)**
Invoke code review subagent to inspect diff against `main` and report findings.

- [ ] **Step 3: Address any findings in one fix pass**

- [ ] **Step 4: Push branch and create Pull Request for Phase 2**
Run: `git push -u origin feat/p1-concurrency-and-lifecycle`
Run: `gh pr create`
