# Wave 3: Cinema Hub Rebranding, Compose Optimizations & i18n Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebrand application ID to `com.adauto.cinemahub` and deep link scheme to `cinemahub://tmdb-callback`, eliminate object allocation churn in Compose (`MovieCard` gradients/colors and `LazyVerticalGrid` `contentType`), integrate active network connectivity monitoring (`NetworkMonitor`) to drive `isOffline` and `OfflineBanner`, decouple `TmdbAccountDto` into domain `AccountProfile`, and extract hardcoded strings to `strings.xml`.

**Architecture:**
1. In `:app` and `:feature:movies`, update `applicationId` to `com.adauto.cinemahub` and deep link scheme to `cinemahub://tmdb-callback`. Update `DeepLinkHandler`, `AndroidManifest.xml`, `MovieListViewModel`, and unit tests to ensure that TMDB OAuth redirects are handled via the Cinema Hub URI scheme.
2. In `:core:ui`, extract static gradients (`Brush.verticalGradient`) and color objects in `MovieCard.kt` to top-level constants to eliminate allocation churn on every card scroll and recomposition. In `MovieListScreen.kt`, specify `contentType = movies.itemContentType { "MovieCard" }` for optimal Compose item recycling.
3. In `:core:data`, create `NetworkMonitor` interface and `ConnectivityManagerNetworkMonitor` using Android `ConnectivityManager` and `callbackFlow`. Inject `NetworkMonitor` into `MovieListViewModel` to dynamically observe connectivity and update `uiState.isOffline` so `OfflineBanner` renders when the device is disconnected.
4. In `:core:model`, define domain model `AccountProfile` to decouple `AuthRepository` from network `TmdbAccountDto`.
5. In `:core:ui` and `:app`, introduce `strings.xml` and wire `@StringRes val titleRes: Int` to `MovieCategory` and string resources across Compose UI components.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose Material 3, AndroidX Core, Android ConnectivityManager, Dagger Hilt, JUnit 4, Google Truth, Robolectric.

**Spec:** [`docs/superpowers/specs/2026-10-23-cinema-hub-hardening-design.md`](file:///Users/adautosouza/repos/estudos/docs/superpowers/specs/2026-10-23-cinema-hub-hardening-design.md)

## Global Constraints

- **Branding Consistency:** All user-facing identifiers and deep links must reference `Cinema Hub` and `cinemahub://tmdb-callback`. Legacy `marvelapp://` schemes must be rejected.
- **Compose Allocation Hygiene:** Frequent recomposition paths (e.g., `MovieCard` in `LazyVerticalGrid`) must not allocate new `Brush`, `List`, or `Color` objects inside composable functions.
- **Dynamic Connectivity Tracking:** `uiState.isOffline` must dynamically reflect system connectivity changes via `NetworkMonitor` rather than remaining statically false.
- **Domain Decoupling:** Domain and data repository interfaces must not leak network DTOs (`TmdbAccountDto`).
- **Detekt & Lint Cleanliness:** Zero new Detekt code smells, zero lint errors, 100% test pass rate across all modules.
- **Documentation Preservation:** All plans and temporary superpower documents remain local in `docs/` and `.superpowers/` (both in `.gitignore`).

## Review Focus

1. **Deep Link Scheme Rejection:** Passing a legacy `marvelapp://tmdb-callback` or non-whitelisted scheme to `DeepLinkHandler` returns `null` and does not initiate authentication. Passing `cinemahub://tmdb-callback?request_token=...&approved=true` returns the token.
2. **Recomposition Memory Stability:** Recomposing `MovieCard` does not allocate new `Brush` instances per frame.
3. **Reactive Offline State Transition:** Disconnecting and reconnecting network emits boolean changes on `NetworkMonitor.isOnline`, promptly updating `MovieListUiState.isOffline` and showing/hiding `OfflineBanner`.
4. **Clean Domain Authentication:** `AuthRepository.completeAuthentication` returns `Outcome<AccountProfile>` containing the user's TMDB account profile without leaking Retrofit/DTO details.
5. **Localization Compatibility:** `MovieCategory.titleRes` resolves valid string resources in Portuguese (pt-BR / default) without crashes or missing resource exceptions.

---

### Task 1: Cinema Hub Rebranding & Deep Link Migration (:app & :feature:movies)

**Files:**
- Modify: `app/build.gradle.kts:26`
- Modify: `app/src/main/AndroidManifest.xml:30`
- Modify: `app/src/main/java/com/adauto/marvel/navigation/DeepLinkHandler.kt:8`
- Modify: `feature/movies/src/main/java/com/adauto/marvel/feature/movies/MovieListViewModel.kt:32`
- Test: `app/src/test/java/com/adauto/marvel/navigation/DeepLinkIntentTest.kt`
- Test: `feature/movies/src/test/java/com/adauto/marvel/feature/movies/MovieListViewModelAuthTest.kt`

**Interfaces:**
- Consumes: `DeepLinkHandler.extractApprovedRequestToken(intent)`
- Produces: `applicationId = "com.adauto.cinemahub"`, `DeepLinkHandler.SCHEME = "cinemahub"`, `TMDB_REDIRECT_URI = "cinemahub://tmdb-callback"`

- [ ] **Step 1: Write failing tests for Cinema Hub scheme in `DeepLinkIntentTest` and `MovieListViewModelAuthTest`**

In `app/src/test/java/com/adauto/marvel/navigation/DeepLinkIntentTest.kt`:
Update tests to assert that `cinemahub://tmdb-callback?request_token=...&approved=true` returns the token, while legacy `marvelapp://` returns `null`:
```kotlin
@Test
fun extractApprovedRequestToken_withValidCinemaHubUri_returnsToken() {
    val uri = Uri.parse("cinemahub://tmdb-callback?request_token=abc_123&approved=true")
    val token = DeepLinkHandler.extractApprovedRequestToken(uri)
    assertThat(token).isEqualTo("abc_123")
}

@Test
fun extractApprovedRequestToken_withLegacyMarvelAppUri_returnsNull() {
    val uri = Uri.parse("marvelapp://tmdb-callback?request_token=abc_123&approved=true")
    val token = DeepLinkHandler.extractApprovedRequestToken(uri)
    assertThat(token).isNull()
}
```

In `feature/movies/src/test/java/com/adauto/marvel/feature/movies/MovieListViewModelAuthTest.kt`:
Update expected redirect URI:
```kotlin
assertThat(customTabLauncher.lastLaunchedUri)
    .contains("redirect_to=cinemahub://tmdb-callback")
```

Verify tests fail (RED).

- [ ] **Step 2: Update `applicationId` and manifest deep link scheme**

In `app/build.gradle.kts`:
```kotlin
defaultConfig {
    applicationId = "com.adauto.cinemahub"
    ...
}
```

In `app/src/main/AndroidManifest.xml`:
```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="cinemahub" android:host="tmdb-callback" />
</intent-filter>
```

- [ ] **Step 3: Update `DeepLinkHandler.SCHEME` and `MovieListViewModel.TMDB_REDIRECT_URI`**

In `app/src/main/java/com/adauto/marvel/navigation/DeepLinkHandler.kt`:
```kotlin
object DeepLinkHandler {
    const val SCHEME = "cinemahub"
    const val HOST_TMDB_CALLBACK = "tmdb-callback"
    ...
}
```

In `feature/movies/src/main/java/com/adauto/marvel/feature/movies/MovieListViewModel.kt`:
```kotlin
private const val TMDB_REDIRECT_URI = "cinemahub://tmdb-callback"
```

- [ ] **Step 4: Verify tests and Detekt pass**

Run:
```bash
./gradlew :app:testDebugUnitTest :feature:movies:testDebugUnitTest :app:detekt :feature:movies:detekt
```

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/java/com/adauto/marvel/navigation/DeepLinkHandler.kt feature/movies/src/main/java/com/adauto/marvel/feature/movies/MovieListViewModel.kt app/src/test/java/com/adauto/marvel/navigation/DeepLinkIntentTest.kt feature/movies/src/test/java/com/adauto/marvel/feature/movies/MovieListViewModelAuthTest.kt
git commit -m "refactor(branding): update applicationId to com.adauto.cinemahub and migrate deep links to cinemahub scheme"
```

---

### Task 2: Compose Allocation & Recomposition Optimization (:core:ui & :feature:movies)

**Files:**
- Modify: `core/ui/src/main/java/com/adauto/marvel/core/ui/component/MovieCard.kt`
- Modify: `feature/movies/src/main/java/com/adauto/marvel/feature/movies/MovieListScreen.kt`
- Test: `core/ui/src/test/java/com/adauto/marvel/core/ui/MovieCardModelTest.kt`

**Interfaces:**
- Consumes: `MovieCard`, `LazyVerticalGrid`
- Produces: Top-level static `MovieCardBottomGradient`, static colors `StarYellow`, `WatchedGreen`, `RatingBackground`, `FavoriteButtonBackground`, `LazyVerticalGrid` with `contentType = movies.itemContentType { "MovieCard" }`

- [ ] **Step 1: Extract static `Brush` gradient and color constants in `MovieCard.kt`**

In `core/ui/src/main/java/com/adauto/marvel/core/ui/component/MovieCard.kt`:
Extract top-level constants outside the `@Composable`:
```kotlin
private val MovieCardGradientColors = listOf(
    Color.Transparent,
    MarvelColors.DarkBackground.copy(alpha = 0.75f),
    MarvelColors.DarkBackground.copy(alpha = 0.98f)
)

private val MovieCardBottomGradient = Brush.verticalGradient(
    colors = MovieCardGradientColors
)

private val StarYellowColor = Color(0xFFFFD700)
private val WatchedGreenColor = Color(0xFF2E7D32)
private val RatingPillBackground = Color.Black.copy(alpha = 0.7f)
private val FavoritePillBackground = Color.Black.copy(alpha = 0.5f)
```
In `MovieCard`:
Replace inline instantiations:
- `Brush.verticalGradient(...)` -> `MovieCardBottomGradient`
- `Color(0xFFFFD700)` -> `StarYellowColor`
- `Color(0xFF2E7D32)` -> `WatchedGreenColor`
- `Color.Black.copy(alpha = 0.7f)` -> `RatingPillBackground`
- `Color.Black.copy(alpha = 0.5f)` -> `FavoritePillBackground`

- [ ] **Step 2: Add `contentType` in `LazyVerticalGrid` items**

In `feature/movies/src/main/java/com/adauto/marvel/feature/movies/MovieListScreen.kt`:
```kotlin
items(
    count = movies.itemCount,
    key = movies.itemKey { it.id },
    contentType = movies.itemContentType { "MovieCard" }
) { index ->
    val movie = movies[index]
    if (movie != null) {
        MovieCard(
            movie = movie,
            onClick = { onAction(MovieListAction.MovieClicked(movie.id)) },
            onFavoriteToggle = { onAction(MovieListAction.FavoriteToggled(movie)) },
            modifier = Modifier.testTag("MovieCard_${movie.id}")
        )
    }
}
```

- [ ] **Step 3: Verify tests and Detekt pass**

Run:
```bash
./gradlew :core:ui:testDebugUnitTest :feature:movies:testDebugUnitTest :core:ui:detekt :feature:movies:detekt
```

- [ ] **Step 4: Commit**

```bash
git add core/ui/src/main/java/com/adauto/marvel/core/ui/component/MovieCard.kt feature/movies/src/main/java/com/adauto/marvel/feature/movies/MovieListScreen.kt
git commit -m "perf(compose): eliminate object allocations in MovieCard and supply contentType to LazyVerticalGrid"
```

---

### Task 3: Active Connectivity Monitoring & Domain DTO Decoupling (:core:model, :core:data, :feature:movies)

**Files:**
- Create: `core/model/src/main/java/com/adauto/marvel/core/model/AccountProfile.kt`
- Create: `core/data/src/main/java/com/adauto/marvel/core/data/util/NetworkMonitor.kt`
- Create: `core/data/src/main/java/com/adauto/marvel/core/data/util/ConnectivityManagerNetworkMonitor.kt`
- Modify: `core/data/src/main/java/com/adauto/marvel/core/data/repository/AuthRepository.kt`
- Modify: `core/data/src/main/java/com/adauto/marvel/core/data/repository/AuthRepositoryImpl.kt`
- Modify: `core/data/src/main/java/com/adauto/marvel/core/data/di/DataModule.kt`
- Modify: `feature/movies/src/main/java/com/adauto/marvel/feature/movies/MovieListViewModel.kt`
- Test: `core/data/src/test/java/com/adauto/marvel/core/data/repository/AuthRepositoryTest.kt`
- Test: `core/data/src/test/java/com/adauto/marvel/core/data/util/ConnectivityManagerNetworkMonitorTest.kt`
- Test: `feature/movies/src/test/java/com/adauto/marvel/feature/movies/MovieListViewModelTest.kt`

**Interfaces:**
- Consumes: `ConnectivityManager`, `TmdbAccountDto`
- Produces: `NetworkMonitor { val isOnline: Flow<Boolean> }`, `AccountProfile`, `AuthRepository.completeAuthentication(requestToken: String): Outcome<AccountProfile>`

- [ ] **Step 1: Create `AccountProfile` domain model in `:core:model`**

In `core/model/src/main/java/com/adauto/marvel/core/model/AccountProfile.kt`:
```kotlin
package com.adauto.marvel.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class AccountProfile(
    val id: Int,
    val username: String,
    val name: String? = null,
    val includeAdult: Boolean = false
)
```

- [ ] **Step 2: Update `AuthRepository` and `AuthRepositoryImpl` to return `Outcome<AccountProfile>`**

In `core/data/src/main/java/com/adauto/marvel/core/data/repository/AuthRepository.kt`:
```kotlin
suspend fun completeAuthentication(requestToken: String): Outcome<AccountProfile>
```

In `core/data/src/main/java/com/adauto/marvel/core/data/repository/AuthRepositoryImpl.kt`:
Map `TmdbAccountDto` to `AccountProfile`:
```kotlin
val profile = AccountProfile(
    id = accountDto.id,
    username = accountDto.username,
    name = accountDto.name,
    includeAdult = accountDto.includeAdult
)
return Outcome.Success(profile)
```

Update `AuthRepositoryTest.kt` to assert on `AccountProfile` and verify tests pass.

- [ ] **Step 3: Create `NetworkMonitor` interface and `ConnectivityManagerNetworkMonitor` in `:core:data`**

In `core/data/src/main/java/com/adauto/marvel/core/data/util/NetworkMonitor.kt`:
```kotlin
package com.adauto.marvel.core.data.util

import kotlinx.coroutines.flow.Flow

interface NetworkMonitor {
    val isOnline: Flow<Boolean>
}
```

In `core/data/src/main/java/com/adauto/marvel/core/data/util/ConnectivityManagerNetworkMonitor.kt`:
```kotlin
package com.adauto.marvel.core.data.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

class ConnectivityManagerNetworkMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context
) : NetworkMonitor {

    override val isOnline: Flow<Boolean> = callbackFlow {
        val connectivityManager = context.getSystemService<ConnectivityManager>()
        if (connectivityManager == null) {
            channel.trySend(false)
            channel.close()
            return@callbackFlow
        }

        fun currentOnline(): Boolean {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

        channel.trySend(currentOnline())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                channel.trySend(currentOnline())
            }

            override fun onLost(network: Network) {
                channel.trySend(currentOnline())
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                channel.trySend(currentOnline())
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.conflate()
}
```

Bind `NetworkMonitor` in `DataModule.kt`:
```kotlin
@Binds
fun bindNetworkMonitor(impl: ConnectivityManagerNetworkMonitor): NetworkMonitor
```

Write Robolectric test `ConnectivityManagerNetworkMonitorTest.kt` verifying callback behavior.

- [ ] **Step 4: Inject `NetworkMonitor` into `MovieListViewModel` and observe `isOnline`**

In `feature/movies/src/main/java/com/adauto/marvel/feature/movies/MovieListViewModel.kt`:
Inject `networkMonitor: NetworkMonitor` and collect in `init`:
```kotlin
init {
    viewModelScope.launch {
        networkMonitor.isOnline.collect { isOnline ->
            _uiState.update { it.copy(isOffline = !isOnline) }
        }
    }
}
```

Update `MovieListViewModelTest.kt` with a fake/mock `NetworkMonitor` and verify that `isOnline = false` updates `uiState.isOffline` to `true`.

- [ ] **Step 5: Verify tests and Detekt pass**

Run:
```bash
./gradlew :core:model:testDebugUnitTest :core:data:testDebugUnitTest :feature:movies:testDebugUnitTest :core:data:detekt :feature:movies:detekt
```

- [ ] **Step 6: Commit**

```bash
git add core/model/src/main/java/com/adauto/marvel/core/model/AccountProfile.kt core/data/src/main/java/com/adauto/marvel/core/data/util/NetworkMonitor.kt core/data/src/main/java/com/adauto/marvel/core/data/util/ConnectivityManagerNetworkMonitor.kt core/data/src/main/java/com/adauto/marvel/core/data/repository/AuthRepository.kt core/data/src/main/java/com/adauto/marvel/core/data/repository/AuthRepositoryImpl.kt core/data/src/main/java/com/adauto/marvel/core/data/di/DataModule.kt feature/movies/src/main/java/com/adauto/marvel/feature/movies/MovieListViewModel.kt core/data/src/test/java/com/adauto/marvel/core/data/repository/AuthRepositoryTest.kt core/data/src/test/java/com/adauto/marvel/core/data/util/ConnectivityManagerNetworkMonitorTest.kt feature/movies/src/test/java/com/adauto/marvel/feature/movies/MovieListViewModelTest.kt
git commit -m "feat(network,auth): add active NetworkMonitor to drive isOffline and decouple AuthRepository with AccountProfile"
```

---

### Task 4: Strings Localization & Resources Extraction (:core:ui, :core:model, :app, :feature:movies)

**Files:**
- Create: `core/ui/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/AndroidManifest.xml` (`android:label="@string/app_name"`)
- Modify: `core/model/src/main/java/com/adauto/marvel/core/model/MovieCategory.kt`
- Modify: `core/ui/src/main/java/com/adauto/marvel/core/ui/component/OfflineBanner.kt`
- Modify: `core/ui/src/main/java/com/adauto/marvel/core/ui/component/MovieCard.kt`
- Modify: `feature/movies/src/main/java/com/adauto/marvel/feature/movies/MovieListScreen.kt`
- Test: `core/model/src/test/java/com/adauto/marvel/core/model/MovieModelTest.kt`

**Interfaces:**
- Consumes: Android string resources (`@string/...`)
- Produces: `MovieCategory.titleRes`, extracted UI strings in `OfflineBanner`, `MovieCard`, `MovieListScreen`, and `app_name`

- [ ] **Step 1: Create `app/src/main/res/values/strings.xml` and `core/ui/src/main/res/values/strings.xml`**

In `app/src/main/res/values/strings.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Cinema Hub</string>
</resources>
```
In `app/src/main/AndroidManifest.xml`:
Update `android:label="@string/app_name"`.

In `core/ui/src/main/res/values/strings.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="category_popular">Populares</string>
    <string name="category_top_rated">Mais Votados</string>
    <string name="category_favorites">Favoritos</string>
    <string name="category_watchlist">Quero Assistir</string>
    <string name="offline_banner_message">Você está offline. Exibindo dados em cache.</string>
    <string name="movie_rating_desc">Nota</string>
    <string name="movie_favorite_add">Favoritar</string>
    <string name="movie_favorite_remove">Remover dos favoritos</string>
    <string name="movie_watched_badge">Visto</string>
    <string name="movie_watched_desc">Assistido</string>
    <string name="search_placeholder">Buscar em %1$s…</string>
    <string name="search_empty_message">Nenhum filme encontrado para \"%1$s\" em %2$s.</string>
</resources>
```

- [ ] **Step 2: Add `@StringRes val titleRes: Int` to `MovieCategory`**

In `core/model/src/main/java/com/adauto/marvel/core/model/MovieCategory.kt`:
```kotlin
package com.adauto.marvel.core.model

import androidx.annotation.StringRes

enum class MovieCategory(
    val displayName: String,
    @param:StringRes val titleRes: Int
) {
    POPULAR("Populares", com.adauto.marvel.core.ui.R.string.category_popular),
    TOP_RATED("Mais Votados", com.adauto.marvel.core.ui.R.string.category_top_rated),
    FAVORITES("Favoritos", com.adauto.marvel.core.ui.R.string.category_favorites),
    WATCHLIST("Quero Assistir", com.adauto.marvel.core.ui.R.string.category_watchlist);

    companion object {
        @Deprecated("Renamed to WATCHLIST", ReplaceWith("WATCHLIST"))
        val WATCHED: MovieCategory = WATCHLIST
    }
}
```
*(Note: If `:core:model` cannot depend directly on `:core:ui:R`, declare a clean UI extension function `val MovieCategory.titleRes: Int` in `:core:ui/src/main/java/com/adauto/marvel/core/ui/util/MovieCategoryExtensions.kt` to preserve strict layer separation).*

- [ ] **Step 3: Update `OfflineBanner`, `MovieCard`, and `MovieListScreen` to use string resources**

In `OfflineBanner.kt`:
Use `stringResource(R.string.offline_banner_message)` as default parameter.

In `MovieCard.kt`:
Use `stringResource(R.string.movie_rating_desc)`, `stringResource(R.string.movie_favorite_add)`, `stringResource(R.string.movie_watched_badge)`.

In `MovieListScreen.kt`:
Use `stringResource(category.titleRes)` for category tabs, `stringResource(R.string.search_placeholder, stringResource(uiState.selectedCategory.titleRes))`, etc.

- [ ] **Step 4: Verify tests and Detekt pass**

Run:
```bash
./gradlew :core:ui:testDebugUnitTest :feature:movies:testDebugUnitTest :core:ui:detekt :feature:movies:detekt :app:assembleDebug
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values/strings.xml core/ui/src/main/res/values/strings.xml app/src/main/AndroidManifest.xml core/ui/src/main/java/com/adauto/marvel/core/ui/ core/model/src/main/java/com/adauto/marvel/core/model/ feature/movies/src/main/java/com/adauto/marvel/feature/movies/
git commit -m "refactor(i18n): extract UI strings and category labels to string resources"
```
