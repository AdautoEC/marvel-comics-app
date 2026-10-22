package com.adauto.marvel.core.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.adauto.marvel.core.model.ComicSummary
import com.adauto.marvel.core.ui.component.ComicCard
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ComicCardTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun comicCard_displaysTitleAndTriggersClick() {
        var clicked = false
        val comic = ComicSummary(
            id = 1,
            title = "Avengers Forever",
            issueNumber = "#1",
            coverUrl = null
        )

        composeTestRule.setContent {
            ComicCard(comic = comic, onClick = { clicked = true })
        }

        composeTestRule.onNodeWithText("Avengers Forever").assertIsDisplayed()
        composeTestRule.onNodeWithText("Avengers Forever").performClick()
        assertTrue(clicked)
    }
}
