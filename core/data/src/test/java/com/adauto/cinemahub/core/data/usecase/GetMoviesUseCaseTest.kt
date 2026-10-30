package com.adauto.cinemahub.core.data.usecase

import androidx.paging.testing.asSnapshot
import com.adauto.cinemahub.core.model.MovieCategory
import com.adauto.cinemahub.core.model.MovieSummary
import com.adauto.cinemahub.core.testing.repository.FakeMoviesRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GetMoviesUseCaseTest {

    private val repository = FakeMoviesRepository()
    private val useCase = GetMoviesUseCase(repository)

    private fun movie(id: Int, title: String) = MovieSummary(
        id = id,
        title = title,
        posterUrl = null,
        voteAverage = 7.0,
        releaseDate = "2020-01-01"
    )

    @Test
    fun invoke_forRemoteCategory_delegatesToRepositoryWithTrimmedQuery() = runTest {
        repository.moviesList = listOf(movie(1, "Inception"), movie(2, "Interstellar"))

        val result = useCase(MovieCategory.POPULAR, "  Inc ").take(1).asSnapshot()

        assertThat(repository.moviesQueries).containsExactly(MovieCategory.POPULAR to "Inc")
        assertThat(result.map { it.id }).containsExactly(1)
    }

    @Test
    fun invoke_forFavorites_filtersByTitleIgnoringCase() = runTest {
        repository.favorites.value = listOf(
            movie(1, "Inception").copy(isFavorite = true),
            movie(2, "Interstellar").copy(isFavorite = true)
        )

        val result = useCase(MovieCategory.FAVORITES, "inter").take(1).asSnapshot()

        assertThat(result.map { it.id }).containsExactly(2)
        assertThat(repository.moviesQueries).isEmpty()
    }

    @Test
    fun invoke_forWatchlistWithBlankQuery_returnsAllWatchlistItems() = runTest {
        repository.watchlist.value = listOf(
            movie(1, "Inception").copy(isInWatchlist = true),
            movie(2, "Interstellar").copy(isInWatchlist = true)
        )

        val result = useCase(MovieCategory.WATCHLIST, "").take(1).asSnapshot()

        assertThat(result.map { it.id }).containsExactly(1, 2).inOrder()
    }

    @Test
    fun invoke_appliesFavoriteAndWatchlistFlagsToItems() = runTest {
        repository.moviesList = listOf(movie(1, "Inception"), movie(2, "Interstellar"))
        repository.favorites.value = listOf(movie(1, "Inception").copy(isFavorite = true))
        repository.watchlist.value = listOf(movie(2, "Interstellar").copy(isInWatchlist = true))

        val result = useCase(MovieCategory.POPULAR, "").take(1).asSnapshot()

        val byId = result.associateBy { it.id }
        assertThat(byId.getValue(1).isFavorite).isTrue()
        assertThat(byId.getValue(1).isInWatchlist).isFalse()
        assertThat(byId.getValue(2).isFavorite).isFalse()
        assertThat(byId.getValue(2).isInWatchlist).isTrue()
    }

    @Test
    fun invoke_reflectsFavoriteChangesReactively() = runTest {
        repository.moviesList = listOf(movie(1, "Inception"))
        assertThat(useCase(MovieCategory.POPULAR, "").take(1).asSnapshot().single().isFavorite).isFalse()

        repository.favorites.value = listOf(movie(1, "Inception").copy(isFavorite = true))

        assertThat(useCase(MovieCategory.POPULAR, "").take(1).asSnapshot().single().isFavorite).isTrue()
    }
}
