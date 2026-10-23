package com.adauto.marvel

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.adauto.marvel.core.data.repository.AuthRepository
import com.adauto.marvel.core.designsystem.theme.MarvelTheme
import com.adauto.marvel.navigation.DeepLinkHandler
import com.adauto.marvel.navigation.MarvelNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleDeepLink(intent)
        enableEdgeToEdge()
        setContent {
            MarvelTheme {
                val navController = rememberNavController()
                MarvelNavHost(navController = navController)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val requestToken = DeepLinkHandler.extractApprovedRequestToken(intent)
        if (requestToken != null) {
            lifecycleScope.launch {
                authRepository.completeAuthentication(requestToken)
            }
        }
    }
}
