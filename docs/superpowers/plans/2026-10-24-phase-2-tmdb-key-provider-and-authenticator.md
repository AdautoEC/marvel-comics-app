# Phase 2: TMDB Key Provider, OkHttp Authenticator & Prewarm Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provide resilient TMDB credential distribution with in-memory caching, 24h stale-while-revalidate TTL, 5s timeout, single-flight OkHttp Authenticator for TMDB status_code 7 (invalid API key), prewarming at startup, and fail-fast `KeysUnavailableException` without plaintext release fallbacks.

**Architecture:** `TmdbApiKeyProviderImpl` maintains an in-memory cache with timestamp. On cache expiration, it returns the stale key while launching an unblocking background refresh. An OkHttp `TmdbAuthenticator` handles 401 responses strictly when TMDB body indicates `status_code: 7`, deduplicating concurrent 401s. `TmdbAuthInterceptor` attaches Bearer token header or replaces `api_key` query param, throwing `KeysUnavailableException` if keys are missing (mapped to `DomainError.KeysUnavailable` in `ErrorMapper`).

**Tech Stack:** Kotlin Coroutines (Timeout, Mutex, StateFlow), OkHttp 4 (`Authenticator`, `Interceptor`, `peekBody`), Kotlinx Serialization, Firebase Remote Config, Dagger Hilt.

**Spec:** `docs/superpowers/specs/2026-10-24-cinema-hub-production-resilience-design.md`

## Global Constraints
- Zero plaintext fallback in release builds: `buildConfigField` for local fallback enabled strictly on `buildTypes.debug`.
- TMDB 401 Differentiation: Authenticator acts strictly on TMDB `status_code: 7` (Invalid API key) parsed via `kotlinx.serialization`. Session 401s pass through to repository.
- Single-Flight Concurrency: Multiple concurrent 401s must not trigger multiple Remote Config fetches.
- Non-blocking Startup: Key prewarm runs asynchronously in `MarvelApp.onCreate` via `@ApplicationScope`.
- Immediate Rejection: Missing credentials throw `KeysUnavailableException : IOException` rather than sending requests without keys.

## Review Focus
- Slow / hanging Remote Config: Must time out at 5 seconds and not block request execution indefinitely.
- Rapid concurrent 401s: N parallel requests failing with status 7 must trigger exactly 1 Remote Config fetch.
- TMDB Account 401 (session denied vs invalid key): Non-status-7 responses must return null from Authenticator.
- Expired cache offline: Must return stale key and not fail if background refresh cannot reach network.
- Release build: Must not contain raw `TMDB_API_KEY` string in `BuildConfig`.

---

### Task 1: Credentials Model, Encrypted Storage & Fallback Configuration (:core:network)

**Files:**
- Create: `core/network/src/main/java/com/adauto/marvel/core/network/security/TmdbCredentials.kt`
- Create: `core/network/src/main/java/com/adauto/marvel/core/network/security/KeysUnavailableException.kt`
- Create: `core/network/src/main/java/com/adauto/marvel/core/network/di/CoroutinesModule.kt`
- Modify: `core/network/build.gradle.kts:13-35`
- Modify: `core/network/src/main/java/com/adauto/marvel/core/network/security/TmdbApiKeyProvider.kt:1-10`
- Test: `core/network/src/test/java/com/adauto/marvel/core/network/security/TmdbCredentialsTest.kt`

**Interfaces:**
- Consumes: `local.properties` in debug builds.
- Produces:
  ```kotlin
  @Serializable
  data class TmdbCredentials(
      val apiKey: String? = null,
      val readAccessToken: String? = null
  )

  class KeysUnavailableException(message: String = "TMDB API credentials unavailable") : IOException(message)

  @Qualifier
  @Retention(AnnotationRetention.RUNTIME)
  annotation class ApplicationScope

  interface TmdbApiKeyProvider {
      suspend fun getApiKey(): String?
      suspend fun getReadAccessToken(): String?
      suspend fun getCredentials(): TmdbCredentials?
      suspend fun refreshKeyForced(): TmdbCredentials?
  }
  ```

- [ ] **Step 1: Write failing unit test for `TmdbCredentials` serialization and parsing**
  In `core/network/src/test/java/com/adauto/marvel/core/network/security/TmdbCredentialsTest.kt`:
  - Test `serializeAndDeserialize_credentialsPreserved`
  - Test `parseLegacyString_fallbackToApiKey`

- [ ] **Step 2: Run test to verify it fails**
  Run: `./gradlew :core:network:testDebugUnitTest --tests com.adauto.marvel.core.network.security.TmdbCredentialsTest`
  Expected: FAIL (unresolved reference)

- [ ] **Step 3: Implement `TmdbCredentials.kt`, `KeysUnavailableException.kt`, and `ApplicationScope.kt`**
  - Create `core/network/src/main/java/com/adauto/marvel/core/network/security/TmdbCredentials.kt`
  - Create `core/network/src/main/java/com/adauto/marvel/core/network/security/KeysUnavailableException.kt`
  - Create `core/network/src/main/java/com/adauto/marvel/core/network/di/CoroutinesModule.kt` providing `@ApplicationScope CoroutineScope(SupervisorJob() + Dispatchers.Default)`
  - Update `TmdbApiKeyProvider.kt` interface with new signatures.

- [ ] **Step 4: Configure `buildConfigField` in `core/network/build.gradle.kts` strictly for debug**
  In `core/network/build.gradle.kts`:
  ```kotlin
  buildFeatures {
      buildConfig = true
  }
  buildTypes {
      debug {
          val localProps = java.util.Properties().apply {
              val file = rootProject.file("local.properties")
              if (file.exists()) load(file.inputStream())
          }
          val debugApiKey = localProps.getProperty("tmdb.api.key") ?: System.getenv("TMDB_API_KEY") ?: ""
          val debugAccessToken = localProps.getProperty("tmdb.read.access.token") ?: System.getenv("TMDB_READ_ACCESS_TOKEN") ?: ""
          buildConfigField("String", "TMDB_API_KEY", "\"$debugApiKey\"")
          buildConfigField("String", "TMDB_READ_ACCESS_TOKEN", "\"$debugAccessToken\"")
      }
      release {
          buildConfigField("String", "TMDB_API_KEY", "\"\"")
          buildConfigField("String", "TMDB_READ_ACCESS_TOKEN", "\"\"")
      }
  }
  ```

- [ ] **Step 5: Run tests and verify PASS**
  Run: `./gradlew :core:network:testDebugUnitTest --tests com.adauto.marvel.core.network.security.TmdbCredentialsTest :core:network:detekt`
  Expected: PASS

- [ ] **Step 6: Commit**
  ```bash
  git add core/network/
  git commit -m "feat(network): define TmdbCredentials, KeysUnavailableException, and debug-only BuildConfig fields"
  ```

---

### Task 2: Hardened `TmdbApiKeyProviderImpl` with TTL, Prewarm, Throttling & Stale-While-Revalidate (:core:network & :app)

**Files:**
- Modify: `core/network/src/main/java/com/adauto/marvel/core/network/security/TmdbApiKeyProviderImpl.kt:1-90`
- Modify: `core/network/src/main/java/com/adauto/marvel/core/network/di/SecurityModule.kt:40-60`
- Modify: `app/src/main/java/com/adauto/marvel/MarvelApp.kt:1-40`
- Test: `core/network/src/test/java/com/adauto/marvel/core/network/security/TmdbApiKeyProviderImplTest.kt`

**Interfaces:**
- Consumes: `TmdbCredentials`, `EncryptedKeyStorage`, `KeystoreCipher`, `FirebaseRemoteConfig`, `@ApplicationScope CoroutineScope`.
- Produces: Resilient `TmdbApiKeyProvider` implementation.

- [ ] **Step 1: Write failing unit tests for `TmdbApiKeyProviderImpl`**
  In `core/network/src/test/java/com/adauto/marvel/core/network/security/TmdbApiKeyProviderImplTest.kt`:
  - `getCredentials_whenCachedValid_returnsFromMemoryWithoutStorageOrNetwork`
  - `getCredentials_whenCacheExpired_returnsStaleAndLaunchesBackgroundRefresh`
  - `getCredentials_whenRemoteConfigTimesOut_fallsBackGracefully`
  - `refreshKeyForced_whenThrottled_returnsCurrentKeyWithoutCrashing`
  - `refreshKeyForced_respectsCooldown`

- [ ] **Step 2: Run tests to verify they fail**
  Run: `./gradlew :core:network:testDebugUnitTest --tests com.adauto.marvel.core.network.security.TmdbApiKeyProviderImplTest`
  Expected: FAIL

- [ ] **Step 3: Implement `TmdbApiKeyProviderImpl` with in-memory caching and stale-while-revalidate**
  In `TmdbApiKeyProviderImpl.kt`:
  - Process cache: `private val cachedCredentials = AtomicReference<Pair<TmdbCredentials, Long>?>()`
  - TTL: `24 * 60 * 60 * 1000L` (24 hours)
  - Cooldown: `15 * 60 * 1000L` (15 minutes between forced refetches)
  - Remote Config fetch with `withTimeout(5_000L)` and `fetch(0)` + `activate()` helper
  - Catch `FirebaseRemoteConfigFetchThrottledException`
  - If release build and no keys available, return null

- [ ] **Step 4: Update `SecurityModule.kt` and `MarvelApp.kt` for prewarm**
  In `SecurityModule.kt`:
  - Configure `FirebaseRemoteConfigSettings` with `setMinimumFetchIntervalInSeconds(3600 * 12)`
  In `MarvelApp.kt`:
  - Inject `TmdbApiKeyProvider` and `@ApplicationScope CoroutineScope`
  - In `onCreate()`: `applicationScope.launch { keyProvider.getCredentials() }`

- [ ] **Step 5: Run tests and verify PASS**
  Run: `./gradlew :core:network:testDebugUnitTest :app:testDebugUnitTest :core:network:detekt :app:detekt`
  Expected: PASS

- [ ] **Step 6: Commit**
  ```bash
  git add core/network/ app/
  git commit -m "feat(network,app): implement 24h stale-while-revalidate TTL, Remote Config timeout, and prewarm"
  ```

---

### Task 3: Single-Flight `TmdbAuthenticator` with JSON Status 7 Detection (:core:network)

**Files:**
- Create: `core/network/src/main/java/com/adauto/marvel/core/network/authenticator/TmdbAuthenticator.kt`
- Modify: `core/network/src/main/java/com/adauto/marvel/core/network/di/NetworkModule.kt:40-55`
- Test: `core/network/src/test/java/com/adauto/marvel/core/network/authenticator/TmdbAuthenticatorTest.kt`

**Interfaces:**
- Consumes: `TmdbApiKeyProvider`, `Json`, `okhttp3.Authenticator`.
- Produces: `TmdbAuthenticator` bound in `provideTmdbOkHttpClient`.

- [ ] **Step 1: Write failing unit tests for `TmdbAuthenticator`**
  In `core/network/src/test/java/com/adauto/marvel/core/network/authenticator/TmdbAuthenticatorTest.kt`:
  - `authenticate_whenStatusCodeIs7_refetchesAndRetriesWithNewKey`
  - `authenticate_whenStatusCodeIsNot7_returnsNull`
  - `authenticate_whenKeyAlreadyRotated_retriesImmediatelyWithoutFetch`
  - `authenticate_whenRetryLimitExceeded_returnsNull`

- [ ] **Step 2: Run tests to verify they fail**
  Run: `./gradlew :core:network:testDebugUnitTest --tests com.adauto.marvel.core.network.authenticator.TmdbAuthenticatorTest`
  Expected: FAIL

- [ ] **Step 3: Implement `TmdbAuthenticator`**
  In `TmdbAuthenticator.kt`:
  - Parse body with `kotlinx.serialization`: `@Serializable private data class TmdbStatusResponse(val status_code: Int? = null)`
  - Check `response.peekBody(1024L).string()`
  - Inspect `status_code == 7`
  - Check `responseCount(response) >= 2 -> return null`
  - Single-flight synchronization lock: if request credential != current provider credential, rebuild request with current credential; otherwise call `keyProvider.refreshKeyForced()` and rebuild request.

- [ ] **Step 4: Bind `TmdbAuthenticator` in `NetworkModule.kt`**
  In `NetworkModule.kt`:
  - Update `provideTmdbOkHttpClient` to inject and configure `.authenticator(authenticator)`

- [ ] **Step 5: Run tests and verify PASS**
  Run: `./gradlew :core:network:testDebugUnitTest :core:network:detekt`
  Expected: PASS

- [ ] **Step 6: Commit**
  ```bash
  git add core/network/
  git commit -m "feat(network): add single-flight TmdbAuthenticator reacting strictly to TMDB status_code 7"
  ```

---

### Task 4: `TmdbAuthInterceptor` Header / Query Replacement & Error Mapping (:core:network & :core:data)

**Files:**
- Modify: `core/network/src/main/java/com/adauto/marvel/core/network/interceptor/TmdbAuthInterceptor.kt:1-35`
- Modify: `core/data/src/main/java/com/adauto/marvel/core/data/mapper/ErrorMapper.kt:1-30`
- Test: `core/network/src/test/java/com/adauto/marvel/core/network/interceptor/TmdbAuthInterceptorTest.kt`
- Test: `core/data/src/test/java/com/adauto/marvel/core/data/mapper/ErrorMapperTest.kt`

**Interfaces:**
- Consumes: `TmdbApiKeyProvider`, `KeysUnavailableException`.
- Produces: Header/query injection in `TmdbAuthInterceptor` and `DomainError.KeysUnavailable` in `ErrorMapper`.

- [ ] **Step 1: Write failing unit tests**
  - In `TmdbAuthInterceptorTest`:
    - `intercept_whenCredentialsMissing_throwsKeysUnavailableException`
    - `intercept_whenReadAccessTokenPresent_addsBearerAuthorizationHeader`
    - `intercept_whenApiKeyPresent_replacesQueryParameter`
  - In `ErrorMapperTest`:
    - `toDomainError_whenKeysUnavailableException_returnsKeysUnavailable`
    - `toDomainError_whenSocketTimeout_returnsTimeout`
    - `toDomainError_whenIOException_returnsNoConnection`

- [ ] **Step 2: Run tests to verify they fail**
  Run: `./gradlew :core:network:testDebugUnitTest :core:data:testDebugUnitTest`
  Expected: FAIL

- [ ] **Step 3: Implement `TmdbAuthInterceptor` updates**
  In `TmdbAuthInterceptor.kt`:
  ```kotlin
  val credentials = runBlocking { keyProvider.getCredentials() }
  if (credentials == null || (credentials.apiKey.isNullOrBlank() && credentials.readAccessToken.isNullOrBlank())) {
      throw KeysUnavailableException()
  }
  val requestBuilder = chain.request().newBuilder()
  if (!credentials.readAccessToken.isNullOrBlank()) {
      requestBuilder.header("Authorization", "Bearer ${credentials.readAccessToken}")
  } else if (!credentials.apiKey.isNullOrBlank()) {
      val newUrl = chain.request().url.newBuilder()
          .setQueryParameter("api_key", credentials.apiKey)
          .build()
      requestBuilder.url(newUrl)
  }
  return chain.proceed(requestBuilder.build())
  ```

- [ ] **Step 4: Update `ErrorMapper.kt` in `:core:data`**
  In `ErrorMapper.kt`:
  ```kotlin
  is ApiResponse.Failure.Exception -> when (throwable) {
      is KeysUnavailableException -> DomainError.KeysUnavailable
      is SocketTimeoutException -> DomainError.Timeout
      is IOException -> DomainError.NoConnection
      else -> DomainError.Unknown(throwable)
  }
  ```

- [ ] **Step 5: Run tests across all modules and verify PASS**
  Run: `./gradlew :core:network:testDebugUnitTest :core:data:testDebugUnitTest :core:network:detekt :core:data:detekt`
  Expected: PASS

- [ ] **Step 6: Commit**
  ```bash
  git add core/network/ core/data/
  git commit -m "feat(network,data): inject Bearer header or api_key param and map KeysUnavailableException"
  ```
