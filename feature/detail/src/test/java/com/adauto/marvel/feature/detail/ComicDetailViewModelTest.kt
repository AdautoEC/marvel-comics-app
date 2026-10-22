package com.adauto.marvel.feature.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.adauto.marvel.core.data.repository.ComicsRepository
import com.adauto.marvel.core.model.ComicDetail
import com.adauto.marvel.core.model.DomainError
import com.adauto.marvel.core.model.Outcome
import com.adauto.marvel.core.model.UiText
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ComicDetailViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val repository: ComicsRepository = mockk()

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `init loads comic detail from repository into uiState`() = runTest {
        val detail = ComicDetail(id = 101, title = "Iron Man #1", description = "Hero origin")
        coEvery { repository.getComicDetail(101) } returns Outcome.Success(detail)

        val handle = SavedStateHandle(mapOf("comicId" to 101))
        val viewModel = ComicDetailViewModel(repository, handle)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(detail, viewModel.uiState.value.comic)
        assertThat(viewModel.uiState.value.isLoading).isFalse()
        assertThat(viewModel.uiState.value.errorMessage).isNull()
    }

    @Test
    fun `init sets errorMessage and isLoading false on repository error`() = runTest {
        coEvery { repository.getComicDetail(101) } returns Outcome.Failure(DomainError.NotFound)

        val handle = SavedStateHandle(mapOf("comicId" to 101))
        val viewModel = ComicDetailViewModel(repository, handle)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.comic).isNull()
        assertThat(viewModel.uiState.value.isLoading).isFalse()
        assertThat(viewModel.uiState.value.errorMessage).isNotNull()
    }

    @Test
    fun `Retry action reloads comic detail from repository`() = runTest {
        val detail = ComicDetail(id = 101, title = "Iron Man #1")
        coEvery { repository.getComicDetail(101) } returnsMany listOf(
            Outcome.Failure(DomainError.NoConnection),
            Outcome.Success(detail)
        )

        val handle = SavedStateHandle(mapOf("comicId" to 101))
        val viewModel = ComicDetailViewModel(repository, handle)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.errorMessage).isNotNull()

        viewModel.onAction(ComicDetailAction.Retry)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { repository.getComicDetail(101) }
        assertEquals(detail, viewModel.uiState.value.comic)
        assertThat(viewModel.uiState.value.isLoading).isFalse()
        assertThat(viewModel.uiState.value.errorMessage).isNull()
    }

    @Test
    fun `BackClicked action emits NavigateBack effect via buffered channel`() = runTest {
        val detail = ComicDetail(id = 101, title = "Iron Man #1")
        coEvery { repository.getComicDetail(101) } returns Outcome.Success(detail)

        val handle = SavedStateHandle(mapOf("comicId" to 101))
        val viewModel = ComicDetailViewModel(repository, handle)

        viewModel.effects.test {
            viewModel.onAction(ComicDetailAction.BackClicked)
            val effect = awaitItem()
            assertEquals(ComicDetailEffect.NavigateBack, effect)
        }
    }
}
