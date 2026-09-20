package com.atop.taskmanager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * UI VRSTVA — téma.
 * Tmavé schéma je výchozí: task manager se čte jako terminál.
 */

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4CC2FF),
    onPrimary = Color(0xFF00344A),
    background = Color(0xFF0B1021),
    onBackground = Color(0xFFE3E6F0),
    surface = Color(0xFF141A2E),
    onSurface = Color(0xFFE3E6F0),
    surfaceVariant = Color(0xFF1E2740),
    onSurfaceVariant = Color(0xFFB8C0D9),
    error = Color(0xFFFF6B6B)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00629E),
    background = Color(0xFFF6F7FB),
    surface = Color(0xFFFFFFFF)
)

@Composable
fun AtopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}