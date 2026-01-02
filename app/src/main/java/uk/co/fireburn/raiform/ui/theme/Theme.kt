package uk.co.fireburn.raiform.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = ElectricYellow,
    onPrimary = SlateBlack,
    primaryContainer = ElectricYellowDark,

    secondary = LightningBlue,
    onSecondary = SlateBlack,
    secondaryContainer = PlasmaCyan,

    background = SlateBlack,
    onBackground = WhiteMist,

    surface = CarbonGrey,
    onSurface = WhiteMist,
    surfaceVariant = DeepSlate,
    onSurfaceVariant = AshGrey,

    error = DangerRed
)

@Composable
fun RaiFormTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Removed deprecated window.statusBarColor assignment.
            // Edge-to-edge logic in MainActivity handles transparency.

            // We still control the icon color (False = White icons for dark theme)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
