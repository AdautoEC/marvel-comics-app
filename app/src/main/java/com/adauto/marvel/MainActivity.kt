package com.adauto.marvel

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.adauto.marvel.core.data.repository.AuthRepository
import com.adauto.marvel.core.designsystem.theme.MarvelTheme
import com.adauto.marvel.core.model.Outcome
import com.adauto.marvel.navigation.DeepLinkHandler
import com.adauto.marvel.navigation.MarvelNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            handleDeepLink(intent)
        }
        enableEdgeToEdge()
        setContent {
            MarvelTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { testTagsAsResourceId = true }
                ) {
                    val navController = rememberNavController()
                    MarvelNavHost(navController = navController)
                }
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
            intent?.data = null
            lifecycleScope.launch {
                val outcome = authRepository.completeAuthentication(requestToken)
                if (outcome is Outcome.Failure) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.auth_error_message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
