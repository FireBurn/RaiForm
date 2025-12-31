package uk.co.fireburn.raiform

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import uk.co.fireburn.raiform.presentation.dashboard.DashboardScreen
import uk.co.fireburn.raiform.presentation.smart_import.ImportScreen
import uk.co.fireburn.raiform.ui.theme.RaiFormTheme

@AndroidEntryPoint // <--- CRITICAL: Enables Hilt injection for this Activity
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RaiFormTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

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

                        // 3. Client Details (NEW)
                        composable("client_details/{clientId}") {
                            // The ViewModel automatically grabs the {clientId} from here
                            uk.co.fireburn.raiform.presentation.client_details.ClientDetailsScreen(
                                navController = navController
                            )
                        }

                        // 4. Active Session Mode (NEW)
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

                        // Future routes (e.g., Active Session) will go here
                    }
                }
            }
        }
    }
}
