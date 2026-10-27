package com.adauto.cinemahub

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
import com.adauto.cinemahub.core.data.repository.AuthRepository
import com.adauto.cinemahub.core.designsystem.theme.CinemaHubTheme
import com.adauto.cinemahub.core.model.Outcome
import com.adauto.cinemahub.navigation.CinemaHubNavHost
import com.adauto.cinemahub.navigation.DeepLinkHandler
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
        handleDeepLink(intent)
        enableEdgeToEdge()
        setContent {
            CinemaHubTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { testTagsAsResourceId = true }
                ) {
                    val navController = rememberNavController()
                    CinemaHubNavHost(navController = navController)
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
