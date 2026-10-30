package com.adauto.cinemahub.feature.movies

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.adauto.cinemahub.core.data.sync.SyncEventBus
import com.adauto.cinemahub.core.data.sync.SyncFailureEvent
import com.adauto.cinemahub.core.data.usecase.GetMoviesUseCase
import com.adauto.cinemahub.core.data.usecase.ToggleFavoriteUseCase
import com.adauto.cinemahub.core.data.usecase.ToggleWatchlistUseCase
import com.adauto.cinemahub.core.model.MovieCategory
import com.adauto.cinemahub.core.model.MovieSummary
import com.adauto.cinemahub.core.testing.data.MovieTestData
import com.adauto.cinemahub.core.testing.network.FakeNetworkMonitor
import com.adauto.cinemahub.core.testing.repository.FakeAuthRepository
import com.adauto.cinemahub.core.testing.repository.FakeMoviesRepository
import com.adauto.cinemahub.core.testing.storage.FakeSyncFailureStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
class MovieListScreenRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleMovies = listOf(
        MovieTestData.createMovieSummary(id = 101, title = "Clube da Luta"),
        MovieTestData.createMovieSummary(id = 102, title = "Inception")
    )

    @Test
    fun movieListScreen_displaysMovieCards_andHandlesClicks() {
        var clickedId: Int? = null

        composeTestRule.setContent {
            val movies = flowOf(PagingData.from(sampleMovies)).collectAsLazyPagingItems()
            MovieListScreen(
                uiState = MovieListUiState(),
                movies = movies,
                onAction = { action ->
                    if (action is MovieListAction.MovieClicked) {
                        clickedId = action.id
                    }
                }
            )
        }

        composeTestRule.onNodeWithText("Clube da Luta").assertIsDisplayed()
        composeTestRule.onNodeWithText("Inception").assertIsDisplayed()

        composeTestRule.onNodeWithText("Clube da Luta").performClick()
        assertEquals(101, clickedId)
    }

    @Test
    fun movieCard_favoriteButtonClick_dispatchesFavoriteToggledAction() {
        var toggledMovie: MovieSummary? = null

        composeTestRule.setContent {
            val movies = flowOf(PagingData.from(sampleMovies)).collectAsLazyPagingItems()
            MovieListScreen(
                uiState = MovieListUiState(),
                movies = movies,
                onAction = { action ->
                    if (action is MovieListAction.FavoriteToggled) {
                        toggledMovie = action.movie
                    }
                }
            )
        }

        composeTestRule.onAllNodesWithContentDescription("Favoritar")[0].performClick()
        assertEquals(101, toggledMovie?.id)
    }

    @Test
    fun movieListScreen_displaysOfflineBannerWhenOffline() {
        composeTestRule.setContent {
            val movies = flowOf(PagingData.empty<MovieSummary>()).collectAsLazyPagingItems()
            MovieListScreen(
                uiState = MovieListUiState(isOffline = true),
                movies = movies,
                onAction = {}
            )
        }

        composeTestRule.onNodeWithTag("OfflineBanner").assertIsDisplayed()
    }

    @Test
    fun movieListScreen_displaysAttributionFooter() {
        composeTestRule.setContent {
            val movies = flowOf(PagingData.from(sampleMovies)).collectAsLazyPagingItems()
            MovieListScreen(
                uiState = MovieListUiState(),
                movies = movies,
                onAction = {}
            )
        }

        composeTestRule.onNodeWithText(
            "Este produto usa a API TMDB, mas não é endossado ou certificado pelo TMDB."
        ).assertIsDisplayed()
    }

    @Test
    fun categoryTabs_clickingCategory_dispatchesCategorySelectedAction() {
        var selectedCategory: MovieCategory? = null

        composeTestRule.setContent {
            val movies = flowOf(PagingData.from(sampleMovies)).collectAsLazyPagingItems()
            MovieListScreen(
                uiState = MovieListUiState(selectedCategory = MovieCategory.POPULAR),
                movies = movies,
                onAction = { action ->
                    if (action is MovieListAction.CategorySelected) {
                        selectedCategory = action.category
                    }
                }
            )
        }

        composeTestRule.onNodeWithText("Mais Votados").performClick()
        assertEquals(MovieCategory.TOP_RATED, selectedCategory)
    }

    @Test
    fun movieListScreen_whenFavoritesEmpty_displaysEmptyFavoritesMessage() {
        val notLoadingStates = LoadStates(
            refresh = LoadState.NotLoading(endOfPaginationReached = true),
            prepend = LoadState.NotLoading(endOfPaginationReached = true),
            append = LoadState.NotLoading(endOfPaginationReached = true)
        )
        composeTestRule.setContent {
            val movies = flowOf(
                PagingData.from(emptyList<MovieSummary>(), sourceLoadStates = notLoadingStates)
            ).collectAsLazyPagingItems()
            MovieListScreen(
                uiState = MovieListUiState(selectedCategory = MovieCategory.FAVORITES),
                movies = movies,
                onAction = {}
            )
        }

        composeTestRule.onNodeWithTag("EmptyStateText").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Nenhum filme favoritado ainda.\nToque no coração para salvar!")
            .assertIsDisplayed()
    }

    @Test
    fun movieListScreen_whenWatchlistEmpty_displaysEmptyWatchlistMessage() {
        val notLoadingStates = LoadStates(
            refresh = LoadState.NotLoading(endOfPaginationReached = true),
            prepend = LoadState.NotLoading(endOfPaginationReached = true),
            append = LoadState.NotLoading(endOfPaginationReached = true)
        )
        composeTestRule.setContent {
            val movies = flowOf(
                PagingData.from(emptyList<MovieSummary>(), sourceLoadStates = notLoadingStates)
            ).collectAsLazyPagingItems()
            MovieListScreen(
                uiState = MovieListUiState(selectedCategory = MovieCategory.WATCHLIST),
                movies = movies,
                onAction = {}
            )
        }

        composeTestRule.onNodeWithTag("EmptyStateText").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Nenhum filme na lista de Quero Assistir.")
            .assertIsDisplayed()
    }

    @Test
    fun movieListScreen_whenSearchEmpty_displaysEmptySearchMessage() {
        val notLoadingStates = LoadStates(
            refresh = LoadState.NotLoading(endOfPaginationReached = true),
            prepend = LoadState.NotLoading(endOfPaginationReached = true),
            append = LoadState.NotLoading(endOfPaginationReached = true)
        )
        composeTestRule.setContent {
            val movies = flowOf(
                PagingData.from(emptyList<MovieSummary>(), sourceLoadStates = notLoadingStates)
            ).collectAsLazyPagingItems()
            MovieListScreen(
                uiState = MovieListUiState(searchQuery = "Matrix"),
                movies = movies,
                onAction = {}
            )
        }

        composeTestRule.onNodeWithTag("EmptyStateText").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Nenhum filme encontrado para \"Matrix\" em Populares.")
            .assertIsDisplayed()
    }

    @Test
    fun movieListScreen_searchInput_dispatchesSearchQueryChanged_andClearsQuery() {
        val dispatchedActions = mutableListOf<MovieListAction>()

        composeTestRule.setContent {
            var uiState by remember { mutableStateOf(MovieListUiState(isSearchActive = false)) }
            val movies = flowOf(PagingData.from(sampleMovies)).collectAsLazyPagingItems()
            MovieListScreen(
                uiState = uiState,
                movies = movies,
                onAction = { action ->
                    dispatchedActions.add(action)
                    when (action) {
                        is MovieListAction.ToggleSearchClicked -> {
                            uiState = uiState.copy(isSearchActive = !uiState.isSearchActive, searchQuery = "")
                        }
                        is MovieListAction.SearchQueryChanged -> {
                            uiState = uiState.copy(searchQuery = action.query)
                        }
                        else -> Unit
                    }
                }
            )
        }

        composeTestRule.onNodeWithTag("SearchIconButton").performClick()
        assertTrue(dispatchedActions.contains(MovieListAction.ToggleSearchClicked))

        composeTestRule.onNodeWithTag("SearchTextField").performTextInput("Matrix")
        assertTrue(dispatchedActions.contains(MovieListAction.SearchQueryChanged("Matrix")))

        composeTestRule.onNodeWithTag("ClearSearchButton").performClick()
        assertEquals(MovieListAction.SearchQueryChanged(""), dispatchedActions.last())
    }

    @Test
    fun movieListScreen_displaysRefreshErrorState_andTriggersRefreshRequestedOnRetry() {
        var refreshRequested = false

        composeTestRule.setContent {
            val loadStates = LoadStates(
                refresh = LoadState.Error(IOException("Network error")),
                prepend = LoadState.NotLoading(endOfPaginationReached = true),
                append = LoadState.NotLoading(endOfPaginationReached = true)
            )
            val movies = flowOf(
                PagingData.empty<MovieSummary>(sourceLoadStates = loadStates)
            ).collectAsLazyPagingItems()
            MovieListScreen(
                uiState = MovieListUiState(),
                movies = movies,
                onAction = { action ->
                    if (action is MovieListAction.RefreshRequested) {
                        refreshRequested = true
                    }
                }
            )
        }

        composeTestRule.onNodeWithTag("RefreshErrorState").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tentar novamente").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tentar novamente").performClick()
        assertTrue(refreshRequested)
    }

    @Test
    fun movieListScreen_displaysAppendErrorState_withRetryButton() {
        val loadStates = LoadStates(
            refresh = LoadState.NotLoading(endOfPaginationReached = false),
            prepend = LoadState.NotLoading(endOfPaginationReached = true),
            append = LoadState.Error(IOException("Falha ao carregar próxima página"))
        )

        composeTestRule.setContent {
            val movies = flowOf(
                PagingData.from(sampleMovies, sourceLoadStates = loadStates)
            ).collectAsLazyPagingItems()
            MovieListScreen(
                uiState = MovieListUiState(),
                movies = movies,
                onAction = {}
            )
        }

        composeTestRule.onNodeWithTag("AppendErrorState").assertIsDisplayed()
        composeTestRule.onNodeWithText("Falha ao carregar próxima página").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tentar novamente").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tentar novamente").performClick()
    }

    @Test
    fun movieListScreen_pullToRefresh_dispatchesRefreshRequestedAction() {
        var refreshRequested = false

        composeTestRule.setContent {
            val movies = flowOf(PagingData.from(sampleMovies)).collectAsLazyPagingItems()
            MovieListScreen(
                uiState = MovieListUiState(),
                movies = movies,
                onAction = { action ->
                    if (action is MovieListAction.RefreshRequested) {
                        refreshRequested = true
                    }
                }
            )
        }

        composeTestRule.onNodeWithTag("MovieListGrid").performTouchInput {
            swipeDown(startY = 100f, endY = 800f, durationMillis = 200)
        }
        composeTestRule.waitForIdle()
        assertTrue(refreshRequested)
    }

    @Test
    fun movieListRoute_whenSyncFailureEmitted_displaysSnackbarMessage() {
        val fakeMoviesRepo = FakeMoviesRepository().apply { moviesList = sampleMovies }
        val fakeAuthRepo = FakeAuthRepository()
        val fakeNetworkMonitor = FakeNetworkMonitor()
        val fakeSyncStorage = FakeSyncFailureStorage()
        val syncEventBus = SyncEventBus(fakeSyncStorage)

        val viewModel = MovieListViewModel(
            moviesRepository = fakeMoviesRepo,
            authRepository = fakeAuthRepo,
            networkMonitor = fakeNetworkMonitor,
            toggleFavoriteUseCase = ToggleFavoriteUseCase(fakeMoviesRepo),
            toggleWatchlistUseCase = ToggleWatchlistUseCase(fakeMoviesRepo),
            getMoviesUseCase = GetMoviesUseCase(fakeMoviesRepo),
            syncEventBus = syncEventBus
        )

        composeTestRule.setContent {
            MovieListRoute(
                onOpenDetail = {},
                viewModel = viewModel
            )
        }

        runBlocking {
            syncEventBus.emitFailure(SyncFailureEvent(movieId = 101, target = "FAVORITE", error = "500"))
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText("Algumas alterações não puderam ser sincronizadas com o TMDB.")
            .assertIsDisplayed()
    }

    @Test
    fun movieListGrid_preservesItemNodeIdentityAcrossPrependWhenKeyProvided() {
        val pagingFlow = MutableStateFlow(PagingData.from(sampleMovies))

        composeTestRule.setContent {
            val movies = pagingFlow.collectAsLazyPagingItems()
            MovieListScreen(
                uiState = MovieListUiState(),
                movies = movies,
                onAction = {}
            )
        }

        val initialSemanticsId = composeTestRule
            .onNodeWithTag("MovieCard_101")
            .fetchSemanticsNode()
            .id

        // Prepend a new movie at index 0; Movie 101 moves from index 0 to index 1
        pagingFlow.value = PagingData.from(
            listOf(MovieTestData.createMovieSummary(id = 999, title = "Matrix")) + sampleMovies
        )
        composeTestRule.waitForIdle()

        val afterPrependSemanticsId = composeTestRule
            .onNodeWithTag("MovieCard_101")
            .fetchSemanticsNode()
            .id

        // With `key = movies.itemKey { it.id }`, Compose reuses the exact same semantics node for Movie 101
        assertEquals(initialSemanticsId, afterPrependSemanticsId)
    }

    @Test
    fun movieListScreen_accessibilityChecks_rolesLabelsAndTouchTargets() {
        composeTestRule.setContent {
            val movies = flowOf(PagingData.from(sampleMovies)).collectAsLazyPagingItems()
            MovieListScreen(
                uiState = MovieListUiState(),
                movies = movies,
                onAction = {}
            )
        }

        // Verify semantic roles and click labels
        composeTestRule.onNodeWithTag("MovieCard_101").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        )
        composeTestRule.onNodeWithTag("UserSessionPill_ConnectBtn").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        )

        // Verify search icon button has contentDescription and minimum 48.dp touch target
        composeTestRule.onNodeWithContentDescription("Pesquisar filmes")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)

        // Verify favorite icon button on movie card has contentDescription and minimum 48.dp touch target
        composeTestRule.onAllNodesWithContentDescription("Favoritar")[0]
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)

        // Verify every clickable node in the hierarchy has a non-empty text, contentDescription, or onClickLabel
        val clickableNodes = composeTestRule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue(clickableNodes.isNotEmpty())
        clickableNodes.forEach { node ->
            val hasText = node.config
                .getOrElse(SemanticsProperties.Text) { emptyList() }
                .isNotEmpty()
            val hasContentDesc = node.config
                .getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
                .isNotEmpty()
            val hasClickLabel = !node.config
                .getOrNull(SemanticsActions.OnClick)
                ?.label
                .isNullOrBlank()
            assertTrue(
                "Clickable node ${node.id} lacks accessible label (text, contentDescription, or onClickLabel)",
                hasText || hasContentDesc || hasClickLabel
            )
        }
    }
}
