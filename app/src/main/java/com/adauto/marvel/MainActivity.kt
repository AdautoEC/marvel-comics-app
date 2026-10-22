package com.adauto.marvel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.adauto.marvel.core.designsystem.theme.MarvelTheme
import com.adauto.marvel.navigation.MarvelNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MarvelTheme {
                val navController = rememberNavController()
                MarvelNavHost(navController = navController)
            }
        }
    }
}
