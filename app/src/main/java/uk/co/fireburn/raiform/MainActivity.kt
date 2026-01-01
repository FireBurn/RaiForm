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
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = "dashboard"
                    ) {
                        composable("dashboard") {
                            DashboardScreen(navController = navController)
                        }
                        composable("import") {
                            ImportScreen(navController = navController)
                        }
                        composable("client_details/{clientId}") {
                            uk.co.fireburn.raiform.presentation.client_details.ClientDetailsScreen(
                                navController = navController
                            )
                        }
                        composable("active_session/{clientId}/{sessionId}") {
                            uk.co.fireburn.raiform.presentation.active_session.ActiveSessionScreen(
                                navController = navController
                            )
                        }
                        composable("archived_clients") {
                            uk.co.fireburn.raiform.presentation.archived.ArchivedClientsScreen(
                                navController
                            )
                        }
                        composable("main_scheduler") {
                            uk.co.fireburn.raiform.presentation.scheduler.MainSchedulerScreen(
                                navController
                            )
                        }
                        // NEW ROUTE
                        composable("settings") {
                            uk.co.fireburn.raiform.presentation.settings.SettingsScreen(
                                navController
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
