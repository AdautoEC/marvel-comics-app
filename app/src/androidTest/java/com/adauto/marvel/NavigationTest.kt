package com.adauto.marvel

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adauto.marvel.navigation.MovieDetailRoute
import com.adauto.marvel.navigation.MovieListRoute
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
        val listRoute = MovieListRoute
        assertNotNull(listRoute)

        val detailRoute = MovieDetailRoute(movieId = 101)
        assertEquals(101, detailRoute.movieId)
    }
}
