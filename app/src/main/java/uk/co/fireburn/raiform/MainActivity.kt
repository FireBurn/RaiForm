package uk.co.fireburn.raiform

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import uk.co.fireburn.raiform.presentation.dashboard.DashboardScreen
import uk.co.fireburn.raiform.presentation.smart_import.ImportScreen
import uk.co.fireburn.raiform.ui.theme.RaiFormTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RaiFormTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    // Handle Widget Deep Linking
                    LaunchedEffect(Unit) {
                        val target = intent?.getStringExtra("navigation_target")
                        val clientId = intent?.getStringExtra("client_id")
                        val sessionId = intent?.getStringExtra("session_id")

                        when (target) {
                            "scheduler" -> navController.navigate("main_scheduler")
                            "session" -> {
                                if (clientId != null && sessionId != null) {
                                    navController.navigate("active_session/$clientId/$sessionId")
                                }
                            }
                            // "dashboard" is default, no action needed
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = "dashboard"
                    ) {
                        // 1. Dashboard
                        composable("dashboard") {
                            DashboardScreen(navController = navController)
                        }

                        // 2. Import
                        composable("import") {
                            ImportScreen(navController = navController)
                        }

                        // 3. Client Details
                        composable("client_details/{clientId}") {
                            uk.co.fireburn.raiform.presentation.client_details.ClientDetailsScreen(
                                navController = navController
                            )
                        }

                        // 4. Active Session Mode
                        composable("active_session/{clientId}/{sessionId}") {
                            uk.co.fireburn.raiform.presentation.active_session.ActiveSessionScreen(
                                navController = navController
                            )
                        }

                        // 5. Archived Clients
                        composable("archived_clients") {
                            uk.co.fireburn.raiform.presentation.archived.ArchivedClientsScreen(
                                navController
                            )
                        }

                        // 6. Main Scheduler
                        composable("main_scheduler") {
                            uk.co.fireburn.raiform.presentation.scheduler.MainSchedulerScreen(
                                navController
                            )
                        }
                    }
                }
            }
        }
    }

    // Ensure new intents (if app is already open) are handled
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent) // Update current intent so LaunchedEffect picks it up on recomposition if needed
        // Note: For complex navigation flows with singleInstance, simpler to let Activity recreate or use a Global Event Bus.
        // For this widget, standard launch is usually sufficient.
    }
}
