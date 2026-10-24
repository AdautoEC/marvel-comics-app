# In-Tab Movie Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement an in-tab movie title search in Cinema Hub, allowing instant, offline-first filtering of movies within the currently selected category.

**Architecture:** UDF with `StateFlow` and `PagingData.filter`. The `MovieListViewModel` exposes `searchQuery` and combines it with category feeds to filter items locally in memory with 0ms latency. The UI renders an expandable search bar in the TopAppBar with clear button and empty search feedback.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose Material 3, AndroidX Paging 3, StateFlow, Turbine, Detekt.

**Spec:** `docs/superpowers/specs/2026-10-23-in-tab-movie-search-design.md`

## Global Constraints
- Target package: `com.adauto.marvel.feature.movies`
- 100% offline-first; no redundant network calls for in-tab filtering
- Zero code smells in Detekt, 0 compilation warnings
- Maintain Unidirectional Data Flow (UDF) conventions and Compose state immutability

## Review Focus
1. Search query trimming and case-insensitivity: "clube" must match "Clube da Luta".
2. Empty search query behavior: clearing the search query immediately restores all items in the current category.
3. Tab switching resets search: selecting a new category tab resets `searchQuery` to empty and hides search if active.
4. Empty search results UI: displays "Nenhum filme encontrado para '<query>' nesta aba" when no items match.
5. Search bar accessibility: clear button and close button have clear content descriptions.

---

### Task 1: Contract & ViewModel In-Tab Filtering

**Files:**
- Modify: `feature/movies/src/main/java/com/adauto/marvel/feature/movies/MovieListContract.kt`
- Modify: `feature/movies/src/main/java/com/adauto/marvel/feature/movies/MovieListViewModel.kt`
- Test: `feature/movies/src/test/java/com/adauto/marvel/feature/movies/MovieListViewModelTest.kt`

- [x] **Step 1.1**: Add failing unit tests in `MovieListViewModelTest.kt` for `SearchQueryChanged` and `ToggleSearchClicked`:
  - `searchQueryChanged_updatesUiStateSearchQuery`
  - `searchQuery_filtersMoviesStreamByTitleCaseInsensitive`
  - `switchingCategory_resetsSearchQueryAndActiveState`
- [x] **Step 1.2**: Run `./gradlew :feature:movies:testDebugUnitTest` and verify tests fail (RED).
- [x] **Step 1.3**: Update `MovieListContract.kt`:
  - In `MovieListUiState`: add `val searchQuery: String = ""` and `val isSearchActive: Boolean = false`.
  - In `MovieListAction`: add `data class SearchQueryChanged(val query: String) : MovieListAction` and `data object ToggleSearchClicked : MovieListAction`.
- [x] **Step 1.4**: Update `MovieListViewModel.kt`:
  - Add `private val _searchQuery = MutableStateFlow("")` and `private val _isSearchActive = MutableStateFlow(false)`.
  - Combine category selection with `_searchQuery` using `pagingData.filter { it.title.contains(query.trim(), ignoreCase = true) }`.
  - In `onAction`:
    - `SearchQueryChanged`: updates `_searchQuery.value = action.query` and `_uiState.update { it.copy(searchQuery = action.query) }`.
    - `ToggleSearchClicked`: toggles `_isSearchActive`, and if deactivated, clears `_searchQuery`.
    - `CategorySelected`: resets `_searchQuery.value = ""` and `_isSearchActive.value = false`.
- [x] **Step 1.5**: Run `./gradlew :feature:movies:testDebugUnitTest :feature:movies:detekt` and verify GREEN (0 errors, 0 code smells).
- [x] **Step 1.6**: Commit changes: `git commit -am "feat(movies): add search query contract and in-tab filtering in MovieListViewModel"`.

---

### Task 2: UI SearchBar Component & MovieListScreen Integration

**Files:**
- Modify: `feature/movies/src/main/java/com/adauto/marvel/feature/movies/MovieListScreen.kt`
- Modify: `feature/movies/src/main/java/com/adauto/marvel/feature/movies/preview/MovieListUiStatePreviewParameterProvider.kt`
- Test: `feature/movies/src/test/java/com/adauto/marvel/feature/movies/preview/MovieListPreviewProviderTest.kt`
- Test: `app/src/androidTest/java/com/adauto/marvel/MovieListUiTest.kt`

- [x] **Step 2.1**: Update `MovieListScreen.kt` TopAppBar to support search mode:
  - When `uiState.isSearchActive` is false: renders standard TopBar with title "CINEMA HUB", search icon button (`Icons.Default.Search`, contentDescription = "Pesquisar"), and `UserSessionPill`.
  - When `uiState.isSearchActive` is true: renders an animated search input field with back/close arrow, clear text icon ('X' button when text is non-empty), and auto-focus.
- [x] **Step 2.2**: Update empty state text in `MovieListScreen.kt`:
  - When `uiState.searchQuery.isNotBlank() && movies.itemCount == 0`, show `"Nenhum filme encontrado para \"${uiState.searchQuery}\" em ${uiState.selectedCategory.displayName}."`.
- [x] **Step 2.3**: Update previews in `MovieListUiStatePreviewParameterProvider.kt` to include search active state.
- [x] **Step 2.4**: Add/update instrumented test in `app/src/androidTest/java/com/adauto/marvel/MovieListUiTest.kt`:
  - `searchIcon_clicking_togglesSearchActive`
  - `typingInSearch_updatesQueryAction`
- [x] **Step 2.5**: Run verification `./gradlew :feature:movies:testDebugUnitTest :feature:movies:detekt :app:testDebugUnitTest :app:detekt`.
- [x] **Step 2.6**: Commit changes: `git commit -am "feat(movies): integrate in-tab search bar and empty search state in MovieListScreen"`.

---

### Task 3: Full Suite Verification & Push to Origin

**Files:**
- Working tree check across all modules

- [ ] **Step 3.1**: Run full local verification:
  `./gradlew detekt lintDebug testDebugUnitTest assembleDebug assembleRelease :app:assembleDebugAndroidTest`
- [ ] **Step 3.2**: Push clean commits to `origin main`:
  `git push origin main`
- [ ] **Step 3.3**: Monitor GitHub Actions CD workflow run using `gh run list --limit 1` and verify all checks pass.
