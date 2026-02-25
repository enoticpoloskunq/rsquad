package com.raccoonsquad.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Color schemes for each theme
private fun getColorScheme(theme: AppTheme) = darkColorScheme(
    primary = when (theme) {
        AppTheme.PURPLE -> PurplePrimary
        AppTheme.BLUE -> BluePrimary
        AppTheme.GREEN -> GreenPrimary
        AppTheme.ORANGE -> OrangePrimary
        AppTheme.RED -> RedPrimary
        AppTheme.DARK -> DarkPrimary
        AppTheme.AMOLED -> AmoledPrimary
    },
    secondary = when (theme) {
        AppTheme.PURPLE -> PurpleSecondary
        AppTheme.BLUE -> BlueSecondary
        AppTheme.GREEN -> GreenSecondary
        AppTheme.ORANGE -> OrangeSecondary
        AppTheme.RED -> RedSecondary
        AppTheme.DARK -> DarkSecondary
        AppTheme.AMOLED -> AmoledSecondary
    },
    tertiary = Color(0xFFB388FF),
    background = when (theme) {
        AppTheme.PURPLE -> PurpleBackground
        AppTheme.BLUE -> BlueBackground
        AppTheme.GREEN -> GreenBackground
        AppTheme.ORANGE -> OrangeBackground
        AppTheme.RED -> RedBackground
        AppTheme.DARK -> DarkBackground
        AppTheme.AMOLED -> AmoledBackground
    },
    surface = when (theme) {
        AppTheme.PURPLE -> PurpleSurface
        AppTheme.BLUE -> BlueSurface
        AppTheme.GREEN -> GreenSurface
        AppTheme.ORANGE -> OrangeSurface
        AppTheme.RED -> RedSurface
        AppTheme.DARK -> DarkSurface
        AppTheme.AMOLED -> AmoledSurface
    },
    surfaceVariant = when (theme) {
        AppTheme.PURPLE -> PurpleSurface
        AppTheme.BLUE -> BlueSurface
        AppTheme.GREEN -> GreenSurface
        AppTheme.ORANGE -> OrangeSurface
        AppTheme.RED -> RedSurface
        AppTheme.DARK -> DarkSurface
        AppTheme.AMOLED -> AmoledSurface
    },
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    primaryContainer = when (theme) {
        AppTheme.PURPLE -> PurplePrimary.copy(alpha = 0.2f)
        AppTheme.BLUE -> BluePrimary.copy(alpha = 0.2f)
        AppTheme.GREEN -> GreenPrimary.copy(alpha = 0.2f)
        AppTheme.ORANGE -> OrangePrimary.copy(alpha = 0.2f)
        AppTheme.RED -> RedPrimary.copy(alpha = 0.2f)
        AppTheme.DARK -> DarkPrimary.copy(alpha = 0.2f)
        AppTheme.AMOLED -> AmoledPrimary.copy(alpha = 0.2f)
    },
    onPrimaryContainer = Color.White
)

@Composable
fun RaccoonSquadTheme(
    theme: AppTheme = AppTheme.PURPLE,
    content: @Composable () -> Unit
) {
    val colorScheme = getColorScheme(theme)
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
