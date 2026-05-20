package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = MinimalPrimaryDark,
    onPrimary = MinimalBgDark,
    primaryContainer = MinimalInputBgDark,
    onPrimaryContainer = MinimalPrimaryDark,
    secondary = MinimalSecondaryDark,
    onSecondary = MinimalBgDark,
    secondaryContainer = MinimalInputBgDark,
    onSecondaryContainer = MinimalPrimaryDark,
    tertiary = MinimalTertiaryDark,
    onTertiary = MinimalPrimaryDark,
    background = MinimalBgDark,
    onBackground = MinimalPrimaryDark,
    surface = MinimalSurfaceDark,
    onSurface = MinimalPrimaryDark,
    surfaceVariant = MinimalInputBgDark,
    onSurfaceVariant = MinimalSecondaryDark,
    outline = MinimalSecondaryDark.copy(alpha = 0.4f),
    outlineVariant = MinimalSecondaryDark.copy(alpha = 0.15f),
    error = Color(0xFFF87171) // Pastel minimal pink-red
)

private val LightColorScheme = lightColorScheme(
    primary = MinimalPrimaryLight,
    onPrimary = MinimalBgLight,
    primaryContainer = MinimalInputBgLight,
    onPrimaryContainer = MinimalPrimaryLight,
    secondary = MinimalSecondaryLight,
    onSecondary = MinimalBgLight,
    secondaryContainer = MinimalInputBgLight,
    onSecondaryContainer = MinimalPrimaryLight,
    tertiary = MinimalTertiaryLight,
    onTertiary = MinimalPrimaryLight,
    background = MinimalBgLight,
    onBackground = MinimalPrimaryLight,
    surface = MinimalSurfaceLight,
    onSurface = MinimalPrimaryLight,
    surfaceVariant = MinimalInputBgLight,
    onSurfaceVariant = MinimalSecondaryLight,
    outline = MinimalSecondaryLight.copy(alpha = 0.4f),
    outlineVariant = MinimalSecondaryLight.copy(alpha = 0.15f),
    error = Color(0xFFEF4444) // Clean clean red
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
