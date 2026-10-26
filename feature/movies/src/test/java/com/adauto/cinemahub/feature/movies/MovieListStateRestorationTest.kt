package com.adauto.cinemahub.feature.movies

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MovieListStateRestorationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun lazyGridState_preservesScrollPositionAcrossStateRestoration() {
        val restorationTester = StateRestorationTester(composeTestRule)
        lateinit var state: LazyGridState

        restorationTester.setContent {
            state = rememberLazyGridState()
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = state,
                modifier = Modifier.fillMaxSize()
            ) {
                items(100) { index ->
                    Text("Item $index", modifier = Modifier.height(100.dp))
                }
            }
        }

        composeTestRule.runOnIdle {
            runBlocking {
                state.scrollToItem(20)
            }
        }
        assertThat(state.firstVisibleItemIndex).isEqualTo(20)

        // Emulate saved instance state restoration
        restorationTester.emulateSavedInstanceStateRestore()

        // Assert scroll position was accurately preserved by rememberLazyGridState
        assertThat(state.firstVisibleItemIndex).isEqualTo(20)
    }
}
