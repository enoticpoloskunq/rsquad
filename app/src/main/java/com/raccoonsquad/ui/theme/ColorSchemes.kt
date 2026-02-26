package com.raccoonsquad.ui.theme

import androidx.compose.ui.graphics.Color

// Color scheme options
enum class AppTheme(val displayName: String) {
    PURPLE("Фиолетовый"),
    BLUE("Синий"),
    GREEN("Зелёный"),
    ORANGE("Оранжевый"),
    RED("Красный"),
    DARK("Тёмный"),
    AMOLED("AMOLED Чёрный")
}

// Purple (default)
val PurplePrimary = Color(0xFF7C4DFF)
val PurpleSecondary = Color(0xFFB388FF)
val PurpleBackground = Color(0xFF121212)
val PurpleSurface = Color(0xFF1E1E1E)

// Blue
val BluePrimary = Color(0xFF2196F3)
val BlueSecondary = Color(0xFF64B5F6)
val BlueBackground = Color(0xFF0D1B2A)
val BlueSurface = Color(0xFF1B263B)

// Green
val GreenPrimary = Color(0xFF00E676)
val GreenSecondary = Color(0xFF69F0AE)
val GreenBackground = Color(0xFF0D1F12)
val GreenSurface = Color(0xFF1A2F1D)

// Orange
val OrangePrimary = Color(0xFFFF9800)
val OrangeSecondary = Color(0xFFFFB74D)
val OrangeBackground = Color(0xFF1A150D)
val OrangeSurface = Color(0xFF2A2318)

// Red
val RedPrimary = Color(0xFFF44336)
val RedSecondary = Color(0xFFEF9A9A)
val RedBackground = Color(0xFF1A0D0D)
val RedSurface = Color(0xFF2A1818)

// Dark (gray)
val DarkPrimary = Color(0xFF607D8B)
val DarkSecondary = Color(0xFF90A4AE)
val DarkBackground = Color(0xFF101010)
val DarkSurface = Color(0xFF1A1A1A)

// AMOLED Black
val AmoledPrimary = Color(0xFF9E9E9E)
val AmoledSecondary = Color(0xFF616161)
val AmoledBackground = Color(0xFF000000)
val AmoledSurface = Color(0xFF0A0A0A)
