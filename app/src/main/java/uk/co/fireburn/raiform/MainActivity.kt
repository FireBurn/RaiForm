package uk.co.fireburn.raiform

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import uk.co.fireburn.raiform.ui.navigation.ActiveSession
import uk.co.fireburn.raiform.ui.navigation.AppNavigation
import uk.co.fireburn.raiform.ui.navigation.Scheduler
import uk.co.fireburn.raiform.ui.theme.RaiFormTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    private var latestIntent by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fix for modern Android edge-to-edge requirement
        enableEdgeToEdge()

        createNotificationChannel()
        checkPermissions()

        latestIntent = intent

        addOnNewIntentListener(Consumer { newIntent ->
            intent = newIntent
            latestIntent = newIntent
        })

        setContent {
            RaiFormTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    LaunchedEffect(latestIntent) {
                        handleIntent(latestIntent, navController)
                    }

                    AppNavigation(navController = navController)
                }
            }
        }
    }

    private fun handleIntent(intent: Intent?, navController: NavHostController) {
        if (intent == null) return

        val target = intent.getStringExtra("navigation_target")
        val clientId = intent.getStringExtra("client_id")
        val sessionId = intent.getStringExtra("session_id")

        when (target) {
            "scheduler" -> {
                navController.navigate(Scheduler)
            }

            "session" -> {
                if (clientId != null && sessionId != null) {
                    navController.navigate(ActiveSession(clientId, sessionId))
                }
            }
        }

        intent.removeExtra("navigation_target")
    }

    private fun createNotificationChannel() {
        val name = "Scheduling Reminders"
        val descriptionText = "Notifications to remind you to plan the week"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel("scheduling_channel", name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
