package uk.co.fireburn.raiform.ui.theme

import androidx.compose.ui.graphics.Color

// --- Zeraora Inspired Palette ---

// Backgrounds
val SlateBlack = Color(0xFF121212)    // Main Background (OLED friendly)
val CarbonGrey = Color(0xFF1E1E1E)    // Card Surfaces / Lists
val DeepSlate = Color(0xFF232526)     // App Bars / Bottom Navigation

// Primary Accents (Electric / Fighting)
val ElectricYellow = Color(0xFFF7D02C) // Primary Action (Zeraora Fur)
val ElectricYellowDark = Color(0xFFC4A218) // Button Pressed State

// Secondary Accents (Plasma / Graphs)
val LightningBlue = Color(0xFF29B6F6) // Secondary / Graph Lines
val PlasmaCyan = Color(0xFF00E5FF)    // Highlights / Glow effects

// Typography & Functional
val WhiteMist = Color(0xFFF5F5F5)     // Primary Text
val AshGrey = Color(0xFFAAAAAA)       // Secondary Text (Metadata)
val DangerRed = Color(0xFFFF5252)     // Delete / Error
val SuccessGreen = Color(0xFF69F0AE)  // Completed Sets

// Gradients (Optional usage for graph fills or headers)
val ZeraoraGradient = listOf(ElectricYellow, LightningBlue)
