# TMDB Authentication, Partial Custom Tab & Bidirectional Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement TMDB user authentication using a styled Android Partial Custom Tab BottomSheet modal, secure Keystore session storage, seamless Guest Mode, and bidirectional cloud synchronization for "Favoritos" (❤️) and "Quero Assistir" (🔖 / Watchlist).

**Architecture:** Offline-first architecture where Room 2.6 is the Single Source of Truth (SSOT). Guest Mode is the zero-barrier default. Authenticated users enjoy 0ms optimistic UI updates with background TMDB REST sync. On first login, an additive union ($Local \cup Remote$) synchronizes guest items to cloud and imports cloud items to device. On logout, local tables are purged to guarantee privacy across multiple accounts.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose, Room 2.6, AndroidX Browser (Custom Tabs), Retrofit + Sandwich, Kotlinx Serialization, Android Keystore AES-GCM, Hilt, Turbine, MockK.

**Spec:** `docs/superpowers/specs/2026-10-22-tmdb-auth-and-sync-design.md`

## Global Constraints

- Never collect, store, or transmit user plaintext passwords directly (ROPC forbidden).
- The authentication Custom Tab must launch as a Partial Custom Tab (BottomSheet height ~65%) with app toolbar colors (`DarkBackground #0F0F14`) and hidden browser menus.
- "Já assisti" is renamed to "Quero Assistir" (Watchlist 🔖) to semantically align 100% with TMDB's official `/account/{account_id}/watchlist` endpoint.
- Logging out must clear `SessionStorage` and purge local `favorite_movies` and `watched_movies` Room tables to prevent cross-account contamination.
- `@ImageOkHttpClient` must remain strictly isolated from TMDB authentication credentials.
- Zero Detekt code smells and zero Android Lint errors.

## Review Focus

1. **Custom Tab Redirect Interception:** If the user dismisses the Custom Tab without approving on TMDB, the app must remain in Guest Mode without crashing or hanging in a loading state.
2. **Network Failure on Cloud Sync:** When authenticated, if a user toggles favorite/watchlist while offline, Room must update immediately (0ms UI latency) and sync should gracefully retry on subsequent app resume/pull-to-refresh without rollbacks.
3. **Cross-Account Data Isolation:** When User A logs out and User B logs in on the same physical device, User B must never inherit User A's favorites or watchlist.
4. **Token Expiration / Invalid Session:** If a saved `session_id` is invalidated or revoked on TMDB, API 401 responses must trigger an automatic transition back to Guest Mode.
5. **Compose Preview Rendering:** All new UI elements (TopBar User Pill, Guest Banner, Quero Assistir Action Pill) must render flawlessly in Compose Previews under `LocalInspectionMode.current`.

---

### Task 1: Model & Network Evolution (`:core:model` & `:core:network`)

**Files:**
- Modify: `core/model/src/main/java/com/adauto/marvel/core/model/MovieCategory.kt`
- Create: `core/network/src/main/java/com/adauto/marvel/core/network/model/TmdbAuthDto.kt`
- Modify: `core/network/src/main/java/com/adauto/marvel/core/network/api/TmdbApi.kt`
- Test: `core/network/src/test/java/com/adauto/marvel/core/network/TmdbAuthApiTest.kt`

**Interfaces:**
- Consumes: `ApiResponse<T>` from Sandwich.
- Produces:
  - `MovieCategory.WATCHLIST` (replacing `WATCHED`).
  - `TmdbRequestTokenDto(success: Boolean, request_token: String, expires_at: String)`
  - `TmdbSessionResponseDto(success: Boolean, session_id: String)`
  - `TmdbAccountDto(id: Int, username: String, name: String? = null, avatar: TmdbAvatarDto? = null)`
  - `TmdbActionResponseDto(status_code: Int, status_message: String, success: Boolean)`
  - `TmdbApi.createRequestToken(): ApiResponse<TmdbRequestTokenDto>`
  - `TmdbApi.createSession(body: TmdbCreateSessionRequestDto): ApiResponse<TmdbSessionResponseDto>`
  - `TmdbApi.getAccountDetails(sessionId: String): ApiResponse<TmdbAccountDto>`
  - `TmdbApi.markAsFavorite(accountId: Int, sessionId: String, body: TmdbFavoriteRequestDto): ApiResponse<TmdbActionResponseDto>`
  - `TmdbApi.addToWatchlist(accountId: Int, sessionId: String, body: TmdbWatchlistRequestDto): ApiResponse<TmdbActionResponseDto>`
  - `TmdbApi.getFavoriteMovies(accountId: Int, sessionId: String, page: Int): ApiResponse<TmdbPageResponseDto<TmdbMovieDto>>`
  - `TmdbApi.getWatchlistMovies(accountId: Int, sessionId: String, page: Int): ApiResponse<TmdbPageResponseDto<TmdbMovieDto>>`

- [ ] **Step 1: Write the failing MockWebServer test in `TmdbAuthApiTest.kt`**

```kotlin
@Test
fun createRequestToken_parses_success_response() = runTest {
    mockWebServer.enqueue(
        MockResponse()
            .setResponseCode(200)
            .setBody("""{"success":true,"expires_at":"2026-10-23 00:00:00 UTC","request_token":"test_token_123"}""")
    )
    val response = api.createRequestToken()
    assertThat(response).isInstanceOf(ApiResponse.Success::class.java)
    val body = (response as ApiResponse.Success).data
    assertThat(body.request_token).isEqualTo("test_token_123")
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:network:testDebugUnitTest --tests com.adauto.marvel.core.network.TmdbAuthApiTest`
Expected: FAIL (unresolved reference: `createRequestToken`)

- [ ] **Step 3: Update `MovieCategory.kt`, create `TmdbAuthDto.kt` and add endpoints to `TmdbApi.kt`**

Implement DTOs with `@Serializable` and add Retrofit endpoints to `TmdbApi.kt`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:network:testDebugUnitTest --tests com.adauto.marvel.core.network.TmdbAuthApiTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/model core/network
git commit -m "feat(network): add TMDB auth, account and watchlist API endpoints and DTOs"
```

---

### Task 2: Session Management & Keystore Storage (`:core:security`)

**Files:**
- Create: `core/security/src/main/java/com/adauto/marvel/core/security/session/SessionState.kt`
- Create: `core/security/src/main/java/com/adauto/marvel/core/security/session/SessionManager.kt`
- Create: `core/security/src/main/java/com/adauto/marvel/core/security/session/SessionManagerImpl.kt`
- Modify: `core/security/src/main/java/com/adauto/marvel/core/security/di/SecurityModule.kt`
- Test: `core/security/src/test/java/com/adauto/marvel/core/security/SessionManagerTest.kt`

**Interfaces:**
- Consumes: `CryptoManager` from `:core:security`.
- Produces:
  - `SessionState`: `Guest` and `Authenticated(accountId: Int, username: String, sessionId: String, avatarUrl: String?)`.
  - `SessionManager`:
    - `val sessionState: StateFlow<SessionState>`
    - `suspend fun saveSession(accountId: Int, username: String, sessionId: String, avatarUrl: String? = null)`
    - `suspend fun clearSession()`

- [ ] **Step 1: Write the failing test in `SessionManagerTest.kt`**

```kotlin
@Test
fun initial_state_is_Guest_and_transitions_to_Authenticated_on_save() = runTest {
    val sessionManager = SessionManagerImpl(fakePreferences)
    assertThat(sessionManager.sessionState.value).isEqualTo(SessionState.Guest)
    sessionManager.saveSession(accountId = 42, username = "testuser", sessionId = "sess_abc", avatarUrl = null)
    assertThat(sessionManager.sessionState.value).isInstanceOf(SessionState.Authenticated::class.java)
    val auth = sessionManager.sessionState.value as SessionState.Authenticated
    assertThat(auth.username).isEqualTo("testuser")
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:security:testDebugUnitTest --tests com.adauto.marvel.core.security.SessionManagerTest`
Expected: FAIL

- [ ] **Step 3: Implement `SessionState`, `SessionManager`, and `SessionManagerImpl`**

Store encrypted session data in SharedPreferences using Keystore cipher. Expose `MutableStateFlow<SessionState>`. Bind in `SecurityModule`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:security:testDebugUnitTest --tests com.adauto.marvel.core.security.SessionManagerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/security
git commit -m "feat(security): implement SessionManager with Keystore encryption and SessionState"
```

---

### Task 3: Auth & Cloud Sync Repository (`:core:data`)

**Files:**
- Create: `core/data/src/main/java/com/adauto/marvel/core/data/repository/AuthRepository.kt`
- Create: `core/data/src/main/java/com/adauto/marvel/core/data/repository/AuthRepositoryImpl.kt`
- Modify: `core/data/src/main/java/com/adauto/marvel/core/data/repository/MoviesRepository.kt`
- Modify: `core/data/src/main/java/com/adauto/marvel/core/data/repository/MoviesRepositoryImpl.kt`
- Modify: `core/data/src/main/java/com/adauto/marvel/core/data/di/DataModule.kt`
- Test: `core/data/src/test/java/com/adauto/marvel/core/data/repository/AuthRepositoryTest.kt`
- Test: `core/data/src/test/java/com/adauto/marvel/core/data/repository/MoviesRepositorySyncTest.kt`

**Interfaces:**
- Consumes: `TmdbApi`, `SessionManager`, `MovieDao`, `FavoriteDao`, `WatchedDao`.
- Produces:
  - `AuthRepository`:
    - `suspend fun createRequestToken(): Outcome<String>`
    - `suspend fun completeAuthentication(requestToken: String): Outcome<TmdbAccountDto>`
    - `suspend fun logout()`
  - `MoviesRepository`:
    - `suspend fun toggleFavorite(movie: MovieSummary)` (optimistic Room + async TMDB if authenticated)
    - `suspend fun toggleWatchlist(movie: MovieSummary)` (optimistic Room + async TMDB if authenticated)
    - `suspend fun syncCloudWithLocal(): Outcome<Unit>` (additive union sync)

- [ ] **Step 1: Write failing tests in `AuthRepositoryTest.kt` and `MoviesRepositorySyncTest.kt`**

```kotlin
@Test
fun completeAuthentication_exchanges_token_fetches_account_and_saves_session() = runTest {
    coEvery { api.createSession(any()) } returns ApiResponse.Success(TmdbSessionResponseDto(true, "sess_123"))
    coEvery { api.getAccountDetails("sess_123") } returns ApiResponse.Success(TmdbAccountDto(42, "user_marvel"))
    val result = authRepository.completeAuthentication("req_tok")
    assertThat(result).isInstanceOf(Outcome.Success::class.java)
    coVerify { sessionManager.saveSession(42, "user_marvel", "sess_123", any()) }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:data:testDebugUnitTest --tests com.adauto.marvel.core.data.repository.AuthRepositoryTest`
Expected: FAIL

- [ ] **Step 3: Implement `AuthRepositoryImpl` and update `MoviesRepositoryImpl`**

Implement additive cloud sync and account purge on logout. Bind in `DataModule.kt`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:data:testDebugUnitTest --tests "com.adauto.marvel.core.data.repository.*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/data
git commit -m "feat(data): implement AuthRepository and bidirectional additive cloud sync in MoviesRepository"
```

---

### Task 4: Partial Custom Tab & Deep Link Handler (`:core:ui` & `:app`)

**Files:**
- Create: `core/ui/src/main/java/com/adauto/marvel/core/ui/util/CustomTabLauncher.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/adauto/marvel/MainActivity.kt`
- Modify: `app/build.gradle.kts` (add `androidx.browser:browser:1.8.0`)
- Test: `app/src/test/java/com/adauto/marvel/navigation/DeepLinkIntentTest.kt`

**Interfaces:**
- Consumes: AndroidX Browser `CustomTabsIntent`.
- Produces:
  - `CustomTabLauncher.launchPartialCustomTab(context: Context, url: Uri)`
  - Deep link receiver for `marvelapp://tmdb-callback?request_token=...&approved=true`

- [ ] **Step 1: Write failing test in `DeepLinkIntentTest.kt`**

```kotlin
@Test
fun intent_with_tmdb_callback_extracts_token_and_approved_flag() {
    val uri = Uri.parse("marvelapp://tmdb-callback?request_token=abc_123&approved=true")
    val intent = Intent(Intent.ACTION_VIEW, uri)
    assertThat(intent.data?.getQueryParameter("request_token")).isEqualTo("abc_123")
    assertThat(intent.data?.getQueryParameter("approved")).isEqualTo("true")
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.adauto.marvel.navigation.DeepLinkIntentTest`
Expected: FAIL

- [ ] **Step 3: Implement `CustomTabLauncher` and configure `MainActivity` & `AndroidManifest.xml`**

Add `androidx.browser:browser` dependency. Configure intent filter with `android:scheme="marvelapp"` and `android:host="tmdb-callback"`. Handle `onNewIntent` to broadcast token to active ViewModel.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.adauto.marvel.navigation.DeepLinkIntentTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/ui app
git commit -m "feat(ui,app): configure Partial Custom Tab BottomSheet and deep link callback handler"
```

---

### Task 5: UI Integration, TopBar User Pill & Compose Previews (`:feature:comics` & `:feature:detail`)

**Files:**
- Create: `feature/comics/src/main/java/com/adauto/marvel/feature/comics/component/UserSessionPill.kt`
- Create: `feature/comics/src/main/java/com/adauto/marvel/feature/comics/component/GuestSyncBanner.kt`
- Modify: `feature/comics/src/main/java/com/adauto/marvel/feature/comics/ComicListContract.kt`
- Modify: `feature/comics/src/main/java/com/adauto/marvel/feature/comics/ComicListViewModel.kt`
- Modify: `feature/comics/src/main/java/com/adauto/marvel/feature/comics/ComicListScreen.kt`
- Modify: `feature/detail/src/main/java/com/adauto/marvel/feature/detail/ComicDetailContract.kt`
- Modify: `feature/detail/src/main/java/com/adauto/marvel/feature/detail/ComicDetailScreen.kt`
- Test: `feature/comics/src/test/java/com/adauto/marvel/feature/comics/ComicListViewModelAuthTest.kt`

**Interfaces:**
- Consumes: `SessionManager`, `AuthRepository`, `MoviesRepository`.
- Produces:
  - TopBar User Pill with Guest (`[ 👤 Convidado | Conectar ]`) and Authenticated (`[ 👤 @username ]`) states.
  - Category tabs: "Populares", "Mais Votados", "Favoritos", "Quero Assistir".
  - Detail screen action pills: "Favorito" (❤️) and "Quero Assistir" (🔖).
  - Skydoves Compose Previews covering all states.

- [ ] **Step 1: Write failing test in `ComicListViewModelAuthTest.kt`**

```kotlin
@Test
fun clicking_connect_triggers_OpenAuthUrl_effect_with_request_token() = runTest {
    coEvery { authRepository.createRequestToken() } returns Outcome.Success("tok_456")
    viewModel.onAction(ComicListAction.ConnectTmdbClicked)
    viewModel.effects.test {
        val effect = awaitItem()
        assertThat(effect).isInstanceOf(ComicListEffect.LaunchTmdbAuth::class.java)
        assertThat((effect as ComicListEffect.LaunchTmdbAuth).authUrl)
            .contains("themoviedb.org/authenticate/tok_456")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :feature:comics:testDebugUnitTest --tests com.adauto.marvel.feature.comics.ComicListViewModelAuthTest`
Expected: FAIL

- [ ] **Step 3: Implement `UserSessionPill`, `GuestSyncBanner`, update contracts, ViewModels and Screens**

Implement all UI components with `@ThemePreviews` and `@DevicePreviews`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :feature:comics:testDebugUnitTest --tests com.adauto.marvel.feature.comics.ComicListViewModelAuthTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add feature/comics feature/detail
git commit -m "feat(ui): add UserSessionPill, GuestSyncBanner, Watchlist tabs and Compose previews"
```

---

### Task 6: End-to-End Quality Verification & PR Update

- [ ] **Step 1: Run full project verification**

Run: `./gradlew detekt lintDebug testDebugUnitTest assembleDebug assembleRelease`
Expected: `BUILD SUCCESSFUL` with 0 Detekt smells, 0 lint errors, 100% tests passing.

- [ ] **Step 2: Verify built APKs**

Run: `ls -lh app/build/outputs/apk/debug/app-debug.apk app/build/outputs/apk/release/app-release.apk`
Expected: Release APK exists and is minified via R8.

- [ ] **Step 3: Push to GitHub & update PR #2**

Run: `git push origin feat/compose-previews-and-firebase-config && gh pr edit 2`
Expected: Branch pushed, PR updated and GitHub Actions green.
