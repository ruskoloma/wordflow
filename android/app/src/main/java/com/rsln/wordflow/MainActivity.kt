package com.rsln.wordflow

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.rsln.wordflow.ui.navigation.WordFlowNavHost
import com.rsln.wordflow.ui.screens.login.LoginScreen
import com.rsln.wordflow.ui.theme.Background
import com.rsln.wordflow.ui.theme.WordFlowTheme

/**
 * Hosts the Compose content + wires the auth gate. If the user
 * isn't signed in we show the LoginScreen. The StateFlow
 * recomposes automatically when sign-in succeeds, at which point
 * we swap to the main nav host.
 *
 * We also kick off a background sync whenever the user becomes
 * signed in (fresh sign-in OR app restart while already signed
 * in). The Sync button is gone from Settings, so this is the
 * primary sync trigger — the other one being the progress-flush
 * inside SyncService.refresh() which piggybacks on it.
 */
class MainActivity : ComponentActivity() {
    private var requestedRoute by mutableStateOf<String?>(null)
    private var routeRequestToken by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as WordFlowApp
        applyRouteRequest(intent)

        setContent {
            WordFlowTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Background
                ) {
                    val userId by app.container.authManager.currentUserId.collectAsState()

                    // Auto-sync: fires on first composition if already
                    // signed in, and re-fires any time the user id
                    // transitions (sign-in → non-null, sign-out → null).
                    // Errors are swallowed into syncMessage (consumed
                    // as a snackbar when you visit Settings).
                    LaunchedEffect(userId) {
                        if (userId != null) {
                            app.container.syncService.refresh()
                        }
                    }

                    if (userId == null) {
                        LoginScreen(app = app)
                    } else {
                        WordFlowNavHost(
                            app = app,
                            startRoute = requestedRoute,
                            routeRequestToken = routeRequestToken
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyRouteRequest(intent)
    }

    private fun applyRouteRequest(intent: Intent?) {
        requestedRoute = intent?.getStringExtra("navigate_to")
        routeRequestToken += 1
    }
}
