# Design: TMDB Authentication & Bidirectional Sync with Partial Custom Tab & Guest Mode

## 1. Intent & Context

The application was recently migrated to The Movie Database (TMDB) API to support general movies, ratings, and categories ("Populares", "Mais Votados", "Favoritos", "Assistidos").
Currently, "Favoritar" (❤️) and "Já assisti" (👁️) are persisted offline in a local Room 2.6 database.

This design introduces **TMDB User Authentication** and **Bidirectional Cloud Sync** while strictly honoring modern security practices and preserving a first-class **Guest Mode (Modo Convidado)**.

---

## 2. Goals, Non-Goals & Anti-Goals

### Goals
1. **First-Class Guest Mode (Modo Convidado):**
   * Default state on fresh install. Zero login barriers, splash walls, or blocking dialogs.
   * Guests can browse, search, and toggle "Favorito" and "Assistido" locally via Room 2.6 with instantaneous (0ms) reactive UI updates.
2. **Seamless Partial Custom Tab Authentication:**
   * Uses Android `androidx.browser:browser` Custom Tabs configured as a **Partial Custom Tab (BottomSheet)** covering ~65% of the screen.
   * Theme-styled toolbar (`DarkBackground #0F0F14`), hidden browser navigation menus, and automatic closure upon redirect.
   * Safe OAuth handshake with zero exposure of user passwords to the app code.
3. **Deep Link Callback Handling:**
   * Intercepts `marvelapp://tmdb-callback?request_token={token}&approved=true`.
   * Automatically closes the Custom Tab, exchanges the request token for a `session_id`, fetches account info (`account_id`, username), and persists credentials encrypted in Keystore.
4. **Bidirectional Sync Engine:**
   * When logged in:
     * Toggling favorite/watched executes an optimistic update in Room and asynchronously pushes to `POST /3/account/{account_id}/favorite` or `/watchlist`.
     * On initial login, merges guest's existing Room items into TMDB cloud and pulls the user's remote favorites/watchlist into Room.
5. **Session Management & Logout:**
   * User can log out at any time from the UI, seamlessly returning to Guest Mode without losing local data.

### Non-Goals
* Supporting third-party federated social logins directly (TMDB web authentication already handles user credentials).
* Replacing Room: Room remains the Single Source of Truth (SSOT) to guarantee zero latency and complete offline resilience.

### Anti-Goals
* **No Resource Owner Password Credentials (ROPC):** The app must NEVER collect, inspect, or transmit user plaintext passwords directly.
* **No Jarring Full-Screen Browser Redirects:** The browser must NOT feel like an external app; it must render as an integrated bottom-sheet modal.
* **No Blocking Gates:** A user who never logs in must have an uncompromised experience.

---

## 3. Approaches Considered

| Approach | Description | Pros | Cons / Verdict |
| :--- | :--- | :--- | :--- |
| **A. Partial Custom Tab (BottomSheet) with Deep Link** | Opens TMDB web auth in a styled Android Custom Tab bottom-sheet modal (~65% height) with redirect to `marvelapp://tmdb-callback`. | **Selected.** Complies with OAuth 2.0 / OWASP; uses system-managed autofill/biometrics; styled to match app; auto-closes seamlessly. | Requires Custom Tab-compatible browser (standard on >99.9% Android devices). |
| **B. Native Compose WebView Modal** | Embeds a WebView inside a Compose `ModalBottomSheet` and intercepts redirects via `WebViewClient`. | 100% inside Compose frame. | Rejected. Does not share system browser cookies/passwords; security posture is weaker than Custom Tabs; can trigger anti-phishing blocks. |
| **C. Direct REST Login (`validate_with_login`)** | Native Compose text fields for username/password sent directly to TMDB API. | 100% native UI with no browser involvement. | Rejected by user for security reasons (violates OWASP mobile security guidelines by handling plaintext passwords). |

---

## 4. Architectural Design

```
+--------------------------------------------------------------------------+
|                                :feature:comics & :feature:detail        |
|  [Guest / User Pill in TopBar]    [Favorite Button]    [Watched Button]  |
+-------------------------------------+------------------------------------+
                                      |
                                      v
+--------------------------------------------------------------------------+
|                                  :core:data                              |
|                       MoviesRepository & AuthRepository                  |
|                                                                          |
|   - observeSessionState(): Flow<SessionState> (Guest vs Authenticated)   |
|   - toggleFavorite(movie) -> Room (optimistic) -> Sync Engine            |
|   - toggleWatched(movie)  -> Room (optimistic) -> Sync Engine            |
|   - syncWithRemote(sessionId, accountId)                                 |
+-------------------+----------------------------------+-------------------+
                    |                                  |
                    v                                  v
+---------------------------------------+  +-------------------------------+
|             :core:database            |  |         :core:network         |
|  - MovieDao, FavoriteDao, WatchedDao  |  |  - TmdbApi (Auth & Account)   |
|  - SQLite (Single Source of Truth)    |  |  - TmdbAuthInterceptor       |
+---------------------------------------+  +---------------+---------------+
                                                           |
                                                           v
                                            +------------------------------+
                                            |   TMDB API (/3/account/...)  |
                                            +------------------------------+
```

### 4.1. Data & DTO Contracts (`:core:network`)

* `POST /3/authentication/token/new` -> `TmdbRequestTokenDto(success, request_token, expires_at)`
* `POST /3/authentication/session/new` with body `{ "request_token": "..." }` -> `TmdbSessionResponseDto(success, session_id)`
* `GET /3/account?session_id=...` -> `TmdbAccountDto(id, username, name, avatar)`
* `POST /3/account/{account_id}/favorite?session_id=...` with `{ "media_type": "movie", "media_id": Int, "favorite": Boolean }`
* `POST /3/account/{account_id}/watchlist?session_id=...` with `{ "media_type": "movie", "media_id": Int, "watchlist": Boolean }`
* `GET /3/account/{account_id}/favorite/movies?session_id=...&page=...`
* `GET /3/account/{account_id}/watchlist/movies?session_id=...&page=...`

### 4.2. Security & Session Storage (`:core:security`)

* `SessionStorage`:
  * Uses Android Keystore AES-GCM (via existing `CryptoManager`) or `EncryptedSharedPreferences`.
  * Persists: `sessionId`, `accountId`, `username`, `avatarUrl`.
  * State model:
    ```kotlin
    sealed interface SessionState {
        data object Guest : SessionState
        data class Authenticated(
            val accountId: Int,
            val username: String,
            val sessionId: String,
            val avatarPath: String? = null
        ) : SessionState
    }
    ```

### 4.3. Partial Custom Tab Launcher (`:core:ui` / `:app`)

* Configured with `CustomTabsIntent.Builder()`:
  * `setInitialActivityHeightPx((displayMetrics.heightPixels * 0.65).toInt())`
  * `setToolbarColor(MarvelColors.DarkBackground.toArgb())`
  * `setUrlBarHidingEnabled(true)`
  * `setShowTitle(true)`
  * `setStartAnimations(...)` / `setExitAnimations(...)`
* Target URL:
  `https://www.themoviedb.org/authenticate/{request_token}?redirect_to=marvelapp://tmdb-callback`

### 4.4. Deep Link & Intent Callback (`:app`)

* `AndroidManifest.xml` intent-filter on `MainActivity`:
  ```xml
  <intent-filter>
      <action android:name="android.intent.action.VIEW" />
      <category android:name="android.intent.category.DEFAULT" />
      <category android:name="android.intent.category.BROWSABLE" />
      <data android:scheme="marvelapp" android:host="tmdb-callback" />
  </intent-filter>
  ```
* In `MainActivity.onNewIntent(intent)`:
  * If URI matches `marvelapp://tmdb-callback`:
    * Extracts `request_token` and `approved=true`.
    * Dispatches event to `AuthViewModel` / `AuthRepository` to complete the session exchange.

### 4.5. Bidirectional Sync Strategy

1. **Initial Sync Policy (Additive Union):**
   * On first login, the sync engine executes an **Additive Union** ($Local \cup Remote$):
     * Any movie favorited or watched locally while in Guest Mode is pushed to TMDB via `markAsFavorite(id, true)` / `addToWatchlist(id, true)`.
     * All movies favorited or on watchlist in the user's TMDB cloud account are downloaded and upserted into local Room entities.
     * Neither side deletes items from the other on first login; no data is lost.
2. **Account Switching & Data Isolation:**
   * `SessionStorage` tracks `lastActiveAccountId`.
   * When logging in:
     * If logging into the *same* account (or first login from guest): Local guest items are merged into this account.
     * If logging into a *different* account (`newAccountId != lastActiveAccountId`): The local favorites/watched tables are cleared prior to pulling the new account's cloud data, preventing cross-account data pollution.
3. **Authenticated Operations:**
   * Clicking Favorite / Watched:
     * **Step 1:** Room update (immediate optimistic UI reaction).
     * **Step 2:** Async API call `api.markAsFavorite(accountId, sessionId, movie.id, isFavorite)`.
     * If the API call fails (network offline), Room remains the offline source of truth, and synchronization retries on next app launch/refresh.
4. **Logout:**
   * Clears `sessionId`, `username`, and `avatarPath` from `SessionStorage`.
   * Sets state to `SessionState.Guest`.
   * Retains the current local Room database for the guest, but records the previous account ID so that a future login by a *different* user does not contaminate the new user's TMDB account.

---

## 5. UI Integration

* **Top Bar Account Pill:**
  * In `ComicListScreen` top app bar:
    * **Guest State:** Displays a sleek pill: `[ 👤 Convidado | Entrar ]`. Clicking opens the Partial Custom Tab.
    * **Authenticated State:** Displays `[ 👤 @username ]`. Clicking shows a small menu with option `[ Sair (Logout) ]`.
* **Category Tabs ("Favoritos" & "Assistidos"):**
  * When in Guest state: shows banner above list:
    * *"Seus favoritos estão salvos neste dispositivo. [Conectar TMDB] para sincronizar com sua conta."*
  * When Authenticated: shows sync indicator with last synced time.

---

## 6. What Is Left to the Builder

* Exact pixel dimensions and layout padding for the Top Bar User Pill.
* Retry policy for failed background sync requests.
* Unit test mocks for TMDB authentication endpoints in MockWebServer.
