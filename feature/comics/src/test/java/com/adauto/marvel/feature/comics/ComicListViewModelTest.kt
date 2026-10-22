package com.adauto.marvel.feature.comics

import app.cash.turbine.test
import com.adauto.marvel.core.data.repository.ComicsRepository
import com.adauto.marvel.core.model.ComicSummary
import com.adauto.marvel.core.model.UiText
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ComicListViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val repository: ComicsRepository = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { repository.comics() } returns emptyFlow()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial uiState has expected defaults`() = runTest {
        val viewModel = ComicListViewModel(repository)
        val state = viewModel.uiState.value
        assertThat(state.isOffline).isFalse()
        assertThat(state.isRefreshing).isFalse()
        assertThat(state.attribution).isEqualTo("Data provided by Marvel. © 2026 MARVEL")
    }

    @Test
    fun `ComicClicked action emits OpenDetail effect via buffered channel`() = runTest {
        val viewModel = ComicListViewModel(repository)
        viewModel.effects.test {
            viewModel.onAction(ComicListAction.ComicClicked(101))
            val effect = awaitItem()
            assertEquals(ComicListEffect.OpenDetail(101), effect)
        }
    }

    @Test
    fun `multiple effects are buffered and received in order without dropping`() = runTest {
        val viewModel = ComicListViewModel(repository)
        viewModel.onAction(ComicListAction.ComicClicked(1))
        viewModel.onAction(ComicListAction.ComicClicked(2))
        viewModel.onAction(ComicListAction.ComicClicked(3))

        viewModel.effects.test {
            assertEquals(ComicListEffect.OpenDetail(1), awaitItem())
            assertEquals(ComicListEffect.OpenDetail(2), awaitItem())
            assertEquals(ComicListEffect.OpenDetail(3), awaitItem())
        }
    }

    @Test
    fun `RefreshRequested action keeps uiState consistent`() = runTest {
        val viewModel = ComicListViewModel(repository)
        viewModel.onAction(ComicListAction.RefreshRequested)
        assertThat(viewModel.uiState.value.isOffline).isFalse()
    }

    @Test
    fun `comics flow calls repository comics`() = runTest {
        val viewModel = ComicListViewModel(repository)
        assertThat(viewModel.comics).isNotNull()
        verify(atLeast = 1) { repository.comics() }
    }
}
