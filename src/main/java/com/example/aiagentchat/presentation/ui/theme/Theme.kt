package com.example.aiagentchat.presentation.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Cyber-minimalist palette
private val CyberPurple = Color(0xFF7C3AED)
private val CyberPurpleDark = Color(0xFF5B21B6)
private val CyberPurpleLight = Color(0xFFA78BFA)
private val CyberTeal = Color(0xFF14B8A6)
private val CyberTealDark = Color(0xFF0D9488)
private val NeonPink = Color(0xFFEC4899)
private val DarkSurface = Color(0xFF0F0F23)
private val DarkBackground = Color(0xFF1A1A2E)
private val DarkCard = Color(0xFF16213E)
private val LightSurface = Color(0xFFFAFAFC)
private val LightBackground = Color(0xFFF1F5F9)

private val DarkColorScheme = darkColorScheme(
    primary = CyberPurpleLight,
    onPrimary = Color.Black,
    primaryContainer = CyberPurpleDark,
    onPrimaryContainer = Color.White,
    secondary = CyberTeal,
    onSecondary = Color.Black,
    secondaryContainer = CyberTealDark,
    onSecondaryContainer = Color.White,
    tertiary = NeonPink,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkCard,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99)
)

private val LightColorScheme = lightColorScheme(
    primary = CyberPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = CyberPurpleDark,
    secondary = CyberTealDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF134E4A),
    tertiary = NeonPink,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = Color(0xFFE7E0EC),
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E)
)

@Composable
fun AiAgentChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}







