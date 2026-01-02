package uk.co.fireburn.raiform.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Define the mapping of your colors to Material Design slots
private val DarkColorScheme = darkColorScheme(
    primary = ElectricYellow,
    onPrimary = SlateBlack, // High contrast text on yellow buttons
    primaryContainer = ElectricYellowDark,

    secondary = LightningBlue,
    onSecondary = SlateBlack,
    secondaryContainer = PlasmaCyan,

    background = SlateBlack,
    onBackground = WhiteMist,

    surface = CarbonGrey,
    onSurface = WhiteMist,
    surfaceVariant = DeepSlate, // Slightly lighter for Card backgrounds
    onSurfaceVariant = AshGrey, // For metadata text (e.g., date)

    error = DangerRed
)

@Composable
fun RaiFormTheme(
    // We default to TRUE to enforce the brand identity regardless of system settings
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Set the status bar to match the app background
            window.statusBarColor = colorScheme.background.toArgb()

            // False = White icons on the status bar (because the background is dark)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
