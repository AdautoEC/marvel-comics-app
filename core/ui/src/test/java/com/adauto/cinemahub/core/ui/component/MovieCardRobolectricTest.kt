package com.adauto.cinemahub.core.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.adauto.cinemahub.core.testing.data.MovieTestData
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MovieCardRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleMovie = MovieTestData.createMovieSummary(
        id = 101,
        title = "Inception",
        voteAverage = 8.8,
        releaseDate = "2010-07-16",
        isFavorite = false,
        isInWatchlist = false
    )

    @Test
    fun movieCard_displaysTitleRatingAndYear() {
        composeTestRule.setContent {
            MovieCard(
                movie = sampleMovie,
                onClick = {}
            )
        }

        composeTestRule.onNodeWithText("Inception").assertIsDisplayed()
        composeTestRule.onNodeWithText("8.8").assertIsDisplayed()
        composeTestRule.onNodeWithText("2010").assertIsDisplayed()
    }

    @Test
    fun movieCard_clicking_triggersOnClick() {
        var clicked = false

        composeTestRule.setContent {
            MovieCard(
                movie = sampleMovie,
                onClick = { clicked = true }
            )
        }

        composeTestRule.onNodeWithText("Inception").performClick()
        assertTrue(clicked)
    }

    @Test
    fun movieCard_clickingFavorite_triggersOnFavoriteToggle() {
        var favToggled = false

        composeTestRule.setContent {
            MovieCard(
                movie = sampleMovie,
                onClick = {},
                onFavoriteToggle = { favToggled = true }
            )
        }

        composeTestRule.onNodeWithContentDescription("Favoritar").performClick()
        assertTrue(favToggled)
    }
}
