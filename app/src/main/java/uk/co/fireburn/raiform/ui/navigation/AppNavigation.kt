package uk.co.fireburn.raiform.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import uk.co.fireburn.raiform.ui.screens.active_session.ActiveSessionScreen
import uk.co.fireburn.raiform.ui.screens.archived.ArchivedClientsScreen
import uk.co.fireburn.raiform.ui.screens.client_details.ClientDetailsScreen
import uk.co.fireburn.raiform.ui.screens.dashboard.DashboardScreen
import uk.co.fireburn.raiform.ui.screens.import_flow.ImportScreen
import uk.co.fireburn.raiform.ui.screens.scheduler.MainSchedulerScreen
import uk.co.fireburn.raiform.ui.screens.settings.SettingsScreen
import uk.co.fireburn.raiform.ui.screens.stats.ClientStatsScreen

// --- Route Definitions (Type-Safe) ---

@Serializable
object Dashboard

@Serializable
data class ClientDetails(val clientId: String)

@Serializable
data class ActiveSession(val clientId: String, val sessionId: String)

@Serializable
object Scheduler

@Serializable
data class Stats(val clientId: String)

@Serializable
object Import

@Serializable
object Settings

@Serializable
object ArchivedClients


// --- Navigation Host ---

@Composable
fun AppNavigation(
    navController: NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = Dashboard
    ) {
        composable<Dashboard> {
            DashboardScreen(
                onNavigateToClientDetails = { clientId ->
                    navController.navigate(ClientDetails(clientId))
                },
                onNavigateToScheduler = {
                    navController.navigate(Scheduler)
                },
                onNavigateToImport = {
                    navController.navigate(Import)
                },
                onNavigateToSettings = {
                    navController.navigate(Settings)
                },
                onNavigateToArchived = {
                    navController.navigate(ArchivedClients)
                }
            )
        }

        composable<ClientDetails> { backStackEntry ->
            // Type-safe arguments are automatically handled by Hilt via SavedStateHandle
            // inside the ViewModel, but can also be accessed here if needed:
            // val route: ClientDetails = backStackEntry.toRoute()
            ClientDetailsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSession = { clientId, sessionId ->
                    navController.navigate(ActiveSession(clientId, sessionId))
                },
                onNavigateToStats = { clientId ->
                    navController.navigate(Stats(clientId))
                }
            )
        }

        composable<ActiveSession> {
            ActiveSessionScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<Scheduler> {
            MainSchedulerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<Stats> {
            ClientStatsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<Import> {
            ImportScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<Settings> {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<ArchivedClients> {
            ArchivedClientsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
