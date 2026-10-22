package com.adauto.marvel

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adauto.marvel.navigation.ComicDetailRoute
import com.adauto.marvel.navigation.ComicListRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun verifyRoutesInstantiation() {
        val listRoute = ComicListRoute
        assertNotNull(listRoute)

        val detailRoute = ComicDetailRoute(comicId = 101)
        assertEquals(101, detailRoute.comicId)
    }
}
